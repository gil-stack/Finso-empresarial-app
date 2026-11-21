import Foundation
import Observation

/// Primer ingreso (especificación §3.1): correo y contraseña, los pasos que
/// mande el servidor en su orden (código del correo, celular, código del SMS)
/// y activar Face ID. Al terminar le pasa el ingreso a `ControlDeSesion`.
@MainActor
@Observable
public final class FlujoDeEntrada {
    public enum Paso: Equatable, Sendable {
        case correo
        case codigo(PasoEntrada)
        case celular
        case biometria
    }

    public private(set) var paso: Paso = .correo
    public private(set) var reto: RetoDeEntrada?
    public private(set) var ocupado = false
    /// Error para mostrar bajo los campos.
    public private(set) var error: String?
    /// Sube con cada código equivocado (para sacudir las casillas).
    public private(set) var codigosEquivocados = 0
    /// Cuándo se puede pedir otro código.
    public private(set) var reenvioDisponibleEn: Date?

    /// Cuánto se muestra el aviso antes de volver al correo.
    public static let esperaAntesDeVolver: TimeInterval = 1.6
    static let codigosQueReinician: Set<String> = ["RETO_VENCIDO", "DEMASIADOS_INTENTOS", "PASO_FUERA_DE_ORDEN"]

    @ObservationIgnored let control: ControlDeSesion
    @ObservationIgnored let telefono: DatosDelTelefono
    @ObservationIgnored let esperar: @Sendable (TimeInterval) async -> Void

    public init(
        control: ControlDeSesion, telefono: DatosDelTelefono,
        esperar: @escaping @Sendable (TimeInterval) async -> Void = { s in
            try? await Task.sleep(nanoseconds: UInt64(s * 1_000_000_000))
        }
    ) {
        self.control = control
        self.telefono = telefono
        self.esperar = esperar
    }

    // MARK: «PASO k DE N»

    /// N = pasos que pidió el servidor al inicio + 2 (correo y biometría).
    /// Antes de saberlo, el caso común: 4.
    public var totalDePasos: Int { (reto?.totalDePasos ?? 2) + 2 }

    /// k = N − pasos del servidor pendientes.
    public var numeroDePaso: Int {
        switch paso {
        case .correo: return 1
        case .biometria: return totalDePasos
        default: return totalDePasos - (reto?.pasos.count ?? 0)
        }
    }

    public var textoDelPaso: String { "PASO \(numeroDePaso) DE \(totalDePasos)" }

    public var correoEnmascarado: String? { reto?.correoEnmascarado }
    public var celularEnmascarado: String? { reto?.celularEnmascarado }

    // MARK: Acciones

    public func limpiarError() { error = nil }

    private func trabajar(_ hacer: () async throws -> Void) async {
        guard !ocupado else { return }
        ocupado = true
        error = nil
        defer { ocupado = false }
        do {
            try await hacer()
        } catch let e as ErrorFinso {
            await fallo(e)
        } catch let e as ErrorDeLlave {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }

    private func fallo(_ e: ErrorFinso) async {
        error = e.paraPersona
        if e.codigo == "CODIGO_INCORRECTO" { codigosEquivocados += 1 }
        if Self.codigosQueReinician.contains(e.codigo), paso != .correo {
            await esperar(Self.esperaAntesDeVolver)
            volverAlCorreo(conservarError: true)
        }
    }

    /// Avanza al siguiente paso pendiente.
    private func avanzar(_ r: RetoDeEntrada) {
        reto = r
        reenvioDisponibleEn = r.reenvioDisponibleEn
        switch r.pasos.first {
        case .capturarCelular: paso = .celular
        case .some(let p): paso = .codigo(p)
        case .none: paso = .biometria
        }
    }

    public func continuar(correo: String, contrasena: String) async {
        let c = correo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !c.isEmpty, !contrasena.isEmpty else {
            error = "Escribe tu correo y tu contraseña."
            return
        }
        await trabajar {
            avanzar(try await control.api.iniciar(correo: c, contrasena: contrasena, telefono: telefono))
        }
    }

    public func confirmar(codigo: String) async {
        guard case .codigo(let p) = paso, let r = reto else { return }
        await trabajar { avanzar(try await control.api.confirmarCodigo(reto: r, paso: p, codigo: codigo)) }
    }

    /// 10 dígitos (se aceptan espacios); se manda `+52…`.
    public func capturar(celular: String) async {
        guard paso == .celular, let r = reto else { return }
        let digitos = celular.filter(\.isNumber)
        guard digitos.count == 10 else {
            error = ErrorFinso("CELULAR_INVALIDO").paraPersona
            return
        }
        await trabajar { avanzar(try await control.api.capturarCelular(reto: r, celular: "+52" + digitos)) }
    }

    public func reenviar() async {
        guard case .codigo(let p) = paso, let r = reto else { return }
        await trabajar { reenvioDisponibleEn = try await control.api.reenviarCodigo(reto: r.reto, paso: p) }
    }

    /// Crea la llave, firma el texto de registro con Face ID y registra el
    /// teléfono. Si algo falla, la llave se borra y no queda nada registrado.
    public func activarBiometria() async {
        guard paso == .biometria, let r = reto else { return }
        let llave = control.llave
        await trabajar {
            do {
                let publica = try await llave.crear()
                let texto = TextoFirmado.registrar(reto: r.reto, huellaDeLlave: huellaDeLlave(publica))
                let firma = try await llave.firmar(texto, motivo: "Activar Face ID o huella en Finso Empresarial")
                let ingreso = try await control.api.registrarDispositivo(
                    reto: r.reto, llavePublica: publica, firma: firma, telefono: telefono)
                control.entro(ingreso)
            } catch {
                llave.borrar()
                throw error
            }
        }
    }

    public func volverAlCorreo(conservarError: Bool = false) {
        reto = nil
        reenvioDisponibleEn = nil
        paso = .correo
        if !conservarError { error = nil }
    }

    /// «Pedir otro código en 0:59».
    public func textoDeReenvio(ahora: Date) -> String? {
        guard let en = reenvioDisponibleEn, en > ahora else { return nil }
        let s = Int(en.timeIntervalSince(ahora).rounded(.up))
        return String(format: "Pedir otro código en %d:%02d", s / 60, s % 60)
    }
}
