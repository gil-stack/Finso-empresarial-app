import Foundation
import Observation

/// Cómo se ve un destino en la lista «¿A quién le mandas?» (§3.4.1).
public enum EstadoDeDestino: Equatable, Sendable {
    case listo
    /// Nuevo: se activa en `segundos`.
    case enEspera(segundos: TimeInterval)
    /// Nuevo, sin fecha todavía.
    case activandose
    case soloNomina

    public var sePuedeTocar: Bool { self == .listo }

    /// «Proveedor · BANORTE · •••• 0921», «Nueva · se activa en 18 min», …
    public func texto(_ d: Destino) -> String {
        switch self {
        case .listo: return (["Proveedor", d.banco].compactMap { $0 } + ["•••• \(d.clabeFinal)"]).joined(separator: " · ")
        case .enEspera(let s): return "Nueva · se activa \(Fechas.faltan(s))"
        case .activandose: return "Nueva · se está activando"
        case .soloNomina: return "Colaborador · se paga desde Nómina en el portal"
        }
    }
}

/// Mandar un SPEI (§3.4): destino, importe y concepto, revisar, firmar con
/// Face ID y resultado. Sigue la regla de reintentos del contrato: una llave
/// de idempotencia y una firma por reto; si falla la red se repite igual, sin
/// volver a pedir Face ID; cualquier otro error gasta el reto.
@MainActor
@Observable
public final class EnvioSpei {
    public enum Paso: Equatable, Sendable {
        case destino, importe, revisar, resultado
    }

    public private(set) var paso: Paso = .destino
    public private(set) var destinos: [Destino] = []
    public private(set) var cargando = false
    public private(set) var ocupado = false
    public private(set) var error: String?
    public private(set) var destino: Destino?
    /// Lo que se ve en el campo, ya formateado (`18,450.00`).
    public private(set) var importeTecleado = ""
    /// Concepto, ya limpio (sin acentos ni ñ).
    public private(set) var concepto = ""
    public private(set) var preparacion: PreparacionSpei?
    public private(set) var orden: Orden?

    @ObservationIgnored let api: FinsoApi
    @ObservationIgnored let almacen: AlmacenDeSesion
    @ObservationIgnored let llave: LlaveDelDispositivo
    @ObservationIgnored let reloj: @Sendable () -> Date
    @ObservationIgnored let esperar: @Sendable (TimeInterval) async -> Void
    /// Disponible para comparar (nil si la persona no ve saldo).
    @ObservationIgnored public let disponible: Int?
    /// Lo que ya se firmó: si falla la red, el reintento manda exactamente
    /// esto (misma llave de idempotencia, reto y firma). Mientras exista no se
    /// arma otro pedido, para que no queden dos órdenes por liberar.
    private struct Intento: Equatable {
        let llave: String
        let firma: String
        let reto: String
        let solicitud: SolicitudSpei
    }
    private var intento: Intento?

    /// Hay un SPEI firmado cuya respuesta no llegó (se cayó la red).
    public var sinConfirmar: Bool { intento != nil }

    /// Con un SPEI sin confirmar, «Atrás» sale a Inicio en lugar de dejar
    /// cambiar importe, concepto o destino (§3.4, «SPEI sin confirmar»).
    public var regresarSale: Bool { paso == .revisar && sinConfirmar }

    public static let avisoDeReintento =
        "No supimos si llegó tu SPEI. Inténtalo otra vez: si ya había llegado, no se duplica."

    /// Al volver a «Mandar SPEI» con uno sin confirmar: directo a revisarlo.
    public func volverAlPendiente() {
        guard sinConfirmar else { return }
        paso = .revisar
        error = Self.avisoDeReintento
    }

    public init(
        api: FinsoApi, almacen: AlmacenDeSesion, llave: LlaveDelDispositivo, disponible: Int?,
        reloj: @escaping @Sendable () -> Date = { Date() },
        esperar: @escaping @Sendable (TimeInterval) async -> Void = { s in
            try? await Task.sleep(nanoseconds: UInt64(s * 1_000_000_000))
        }
    ) {
        self.api = api
        self.almacen = almacen
        self.llave = llave
        self.disponible = disponible
        self.reloj = reloj
        self.esperar = esperar
    }

    // MARK: 1. Destino

    public func estado(de d: Destino) -> EstadoDeDestino {
        if d.soloNomina { return .soloNomina }
        if d.listoEn(reloj()) { return .listo }
        guard let desde = d.activoDesde else { return .activandose }
        return .enEspera(segundos: desde.timeIntervalSince(reloj()))
    }

    /// Primero los que se pueden pagar, luego por nombre.
    public var destinosOrdenados: [Destino] {
        destinos.sorted {
            let a = estado(de: $0).sePuedeTocar, b = estado(de: $1).sePuedeTocar
            if a != b { return a }
            return Nombre.legible($0.titular) < Nombre.legible($1.titular)
        }
    }

    public func cargarDestinos() async {
        cargando = true
        defer { cargando = false }
        do {
            destinos = try await api.destinos()
            error = nil
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }

    /// Algún destino nuevo ya cumplió su espera: hay que volver a pedir la lista.
    public var hayQueRecargar: Bool {
        destinos.contains { !$0.activo && !$0.soloNomina && $0.listoEn(reloj()) }
    }

    public func elegir(_ d: Destino) {
        guard estado(de: d).sePuedeTocar else { return }
        destino = d
        error = nil
        paso = .importe
    }

    // MARK: 2. Importe y concepto

    public var importeCentavos: Int? { Dinero.centavos(de: importeTecleado) }

    /// Al teclear el importe: agrupa y no deja más de dos decimales.
    public func teclearImporte(_ nuevo: String) {
        importeTecleado = Dinero.alTeclear(anterior: importeTecleado, nuevo: nuevo)
    }

    /// Al teclear el concepto: quita acentos y ñ, y lo que SPEI no admite.
    public func teclearConcepto(_ nuevo: String) { concepto = Concepto.limpiar(nuevo) }

    public var pasaDelDisponible: Bool {
        guard let d = disponible, let i = importeCentavos else { return false }
        return i > d
    }

    /// El concepto como se manda (sin espacio final).
    public var conceptoFinal: String { concepto.trimmingCharacters(in: .whitespaces) }

    public var puedeRevisar: Bool {
        (importeCentavos ?? 0) > 0 && SolicitudSpei.conceptoValido(conceptoFinal) && !pasaDelDisponible
    }

    private var solicitud: SolicitudSpei? {
        guard let d = destino, let i = importeCentavos else { return nil }
        return SolicitudSpei(destino: d.id, importeCentavos: i, concepto: conceptoFinal)
    }

    private func trabajar(_ hacer: () async throws -> Void) async {
        guard !ocupado else { return }
        ocupado = true
        error = nil
        defer { ocupado = false }
        do {
            try await hacer()
        } catch let e as ErrorDeLlave {
            if e.fallo != .cancelada { error = e.paraPersona }
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }

    /// «Revisar»: `POST /spei/preparar`.
    public func revisar() async {
        guard puedeRevisar, let s = solicitud else { return }
        if let i = intento {
            // Ya hay uno firmado sin respuesta: se reintenta ese, no otro.
            if i.solicitud == s {
                error = nil
                paso = .revisar
            } else {
                error = Self.avisoSinConfirmar(i.solicitud)
            }
            return
        }
        await trabajar {
            preparacion = try await api.prepararSpei(s)
            paso = .revisar
        }
    }

    static func avisoSinConfirmar(_ s: SolicitudSpei) -> String {
        "Tu SPEI de \(Dinero.pesos(s.importeCentavos)) no se confirmó por la red. Reintenta mandarlo antes de pedir otro."
    }

    // MARK: 3. Revisar y firmar

    /// «Mandar $1,500.00 a Papelera del Centro SA de CV».
    public var motivoDeFirma: String {
        "Mandar \(Dinero.pesos(importeCentavos ?? 0)) a \(Nombre.legible(destino?.titular ?? ""))"
    }

    /// «Pedir con Face ID». Si el reto venció o se gastó, prepara otro.
    public func pedir() async {
        guard let s = intento?.solicitud ?? solicitud, let dispositivo = almacen.dispositivo() else { return }
        await trabajar {
            if intento == nil, preparacion == nil || preparacion!.expiraEn <= reloj() {
                preparacion = try await api.prepararSpei(s)
                intento = nil
            }
            guard let p = preparacion else { return }
            if intento == nil {
                let texto = TextoFirmado.spei(
                    dispositivo: dispositivo, reto: p.reto, destino: s.destino, importeCentavos: s.importeCentavos,
                    concepto: s.concepto)
                let firma = try await llave.firmar(texto, motivo: motivoDeFirma)
                intento = Intento(llave: nuevaLlaveDeIdempotencia(), firma: firma, reto: p.reto, solicitud: s)
            }
            guard let i = intento else { return }
            do {
                orden = try await api.mandarSpei(
                    i.solicitud, reto: i.reto, firma: i.firma, llaveDeIdempotencia: i.llave)
                intento = nil
                paso = .resultado
            } catch let e as ErrorFinso where !e.esSinRed {
                // Cualquier respuesta del servidor gasta el reto.
                intento = nil
                preparacion = nil
                throw e
            } catch let e as ErrorFinso where e.esSinRed {
                error = Self.avisoDeReintento
                return
            }
        }
    }

    public func regresar() {
        guard !regresarSale else { return }
        error = nil
        switch paso {
        case .importe: paso = .destino
        case .revisar: paso = .importe
        default: break
        }
    }

    // MARK: 4. Resultado

    /// Mientras está en proceso, consulta cada 3 s, hasta 20 veces.
    public func seguir() async {
        var veces = 0
        while let o = orden, !o.estado.esFinal, o.estado != .porLiberar, veces < 20 {
            await esperar(3)
            veces += 1
            if let nueva = try? await api.orden(o.id) { orden = nueva }
        }
    }
}

/// Título e ícono del resultado de una orden (§3.4.4).
public extension EstadoOrden {
    var tituloDeResultado: String {
        switch self {
        case .porLiberar: return "Listo, falta liberarlo"
        case .enProceso, .enviada: return "Enviando…"
        case .liquidada: return "SPEI enviado"
        case .rechazada: return "SPEI rechazado"
        case .devuelta, .cancelada: return "No se pudo mandar"
        }
    }
}
