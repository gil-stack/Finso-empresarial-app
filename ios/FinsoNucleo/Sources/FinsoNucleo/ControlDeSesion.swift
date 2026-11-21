import Foundation
import Observation

/// En qué parte está la app (especificación §3).
public enum EstadoDeSesion: Equatable, Sendable {
    /// Leyendo el llavero.
    case arrancando
    /// Teléfono sin registrar: bienvenida. `aviso` se muestra una vez (p. ej.
    /// el teléfono se dio de baja).
    case fuera(aviso: String?)
    /// Registrado: pide Face ID. `nombre` para saludar («Hola, Ana»).
    case bloqueada(nombre: String?)
    /// Tiene varias empresas y no ha elegido (v1.1).
    case elegirEmpresa(Usuario)
    case dentro(Usuario)

    public var usuario: Usuario? {
        switch self {
        case .dentro(let u), .elegirEmpresa(let u): return u
        default: return nil
        }
    }
}

/// Lleva la sesión: entrar, desbloquear con Face ID, elegir empresa, salir,
/// olvidar el teléfono y el bloqueo a los 5 minutos en segundo plano. Toda la
/// lógica vive aquí (se prueba en Linux); las pantallas solo la muestran.
@MainActor
@Observable
public final class ControlDeSesion {
    public private(set) var estado: EstadoDeSesion = .arrancando
    /// Al abrir la app (o al bloquearse sola) Face ID se lanza solo; después
    /// de «Salir», no.
    public private(set) var faceIdAutomatico = false

    /// Tras 5 min en segundo plano se pide Face ID otra vez (§2).
    public static let bloqueoEnSegundoPlano: TimeInterval = 5 * 60
    /// La sesión dura 1 h desde el último Face ID (contrato §1.7).
    public static let duracionDeLaSesion: TimeInterval = 60 * 60

    @ObservationIgnored public let api: FinsoApi
    @ObservationIgnored public let almacen: AlmacenDeSesion
    @ObservationIgnored public let llave: LlaveDelDispositivo
    @ObservationIgnored let reloj: @Sendable () -> Date
    @ObservationIgnored private var ultimoFaceId: Date?
    @ObservationIgnored private var enSegundoPlanoDesde: Date?

    public init(
        api: FinsoApi, almacen: AlmacenDeSesion, llave: LlaveDelDispositivo,
        reloj: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.api = api
        self.almacen = almacen
        self.llave = llave
        self.reloj = reloj
    }

    /// Al abrir la app: registrado → pide Face ID; si no → bienvenida.
    public func arrancar() {
        if almacen.dispositivo() != nil && llave.existe() {
            faceIdAutomatico = true
            estado = .bloqueada(nombre: almacen.persona())
        } else {
            olvidarLocal()
            estado = .fuera(aviso: nil)
        }
    }

    /// Ya se vio el aviso de la bienvenida.
    public func avisoVisto() {
        if case .fuera(let aviso) = estado, aviso != nil { estado = .fuera(aviso: nil) }
    }

    /// Terminó el primer ingreso (teléfono registrado) o el desbloqueo.
    public func entro(_ ingreso: Ingreso) {
        if let d = ingreso.dispositivo { almacen.guardarDispositivo(d) }
        almacen.guardar(ingreso.sesion)
        almacen.guardarPersona(ingreso.usuario.nombreCorto)
        ultimoFaceId = reloj()
        mostrar(ingreso.usuario)
    }

    private func mostrar(_ u: Usuario) {
        if let e = u.empresa {
            almacen.guardarEmpresa(e.id)
            estado = .dentro(u)
        } else if u.debeElegirEmpresa {
            estado = .elegirEmpresa(u)
        } else {
            estado = .dentro(u)
        }
    }

    /// Ingresos siguientes: firma `entrar` con Face ID. Si la llave ya no
    /// sirve o el teléfono se dio de baja, olvida todo y vuelve a la
    /// bienvenida con el aviso. Otros errores se lanzan para mostrarlos.
    public func desbloquear() async throws {
        guard let dispositivo = almacen.dispositivo() else {
            olvidarLocal()
            estado = .fuera(aviso: nil)
            return
        }
        do {
            let reto = try await api.retoParaEntrar(dispositivo: dispositivo)
            let firma = try await llave.firmar(
                TextoFirmado.entrar(dispositivo: dispositivo, reto: reto.reto), motivo: "Entrar a Finso Empresarial")
            entro(try await api.entrarConBiometria(dispositivo: dispositivo, reto: reto.reto, firma: firma))
        } catch let e as ErrorDeLlave where e.fallo == .invalidada || e.fallo == .noExiste {
            olvidarLocal()
            estado = .fuera(aviso: e.paraPersona)
        } catch let e as ErrorFinso where e.cierraSesion {
            olvidarLocal()
            estado = .fuera(aviso: e.paraPersona)
        }
    }

    /// Elige la empresa (va en `X-Finso-Empresa` desde entonces).
    public func elegirEmpresa(_ id: String) async throws {
        almacen.guardarEmpresa(id)
        do {
            mostrar(try await api.elegirEmpresa(id))
        } catch {
            almacen.guardarEmpresa(nil)
            throw error
        }
    }

    /// `409 EMPRESA_REQUERIDA` en cualquier llamada: vuelve a elegir.
    public func pedirEmpresa() {
        almacen.guardarEmpresa(nil)
        if let u = estado.usuario { estado = .elegirEmpresa(u.sinEmpresa()) }
    }

    /// «Cambiar de empresa» desde Ajustes.
    public func cambiarDeEmpresa() { pedirEmpresa() }

    /// El servidor cerró la sesión (revocada, teléfono dado de baja) o se
    /// venció la hora.
    public func cerradaPorServidor(_ e: ErrorFinso) {
        if e.codigo == "DISPOSITIVO_REVOCADO" {
            olvidarLocal()
            estado = .fuera(aviso: e.paraPersona)
        } else if almacen.dispositivo() != nil {
            almacen.borrarSesion()
            estado = .bloqueada(nombre: almacen.persona())
        } else {
            estado = .fuera(aviso: nil)
        }
    }

    /// «Salir»: la próxima vez se entra con Face ID.
    public func salir() async {
        try? await api.salir()
        almacen.borrarSesion()
        faceIdAutomatico = false
        estado = .bloqueada(nombre: almacen.persona())
    }

    /// «Olvidar este teléfono» o «Entrar con otra cuenta»: para volver se
    /// necesitan correo, contraseña y códigos.
    public func olvidarTelefono() async {
        if almacen.sesion() != nil { try? await api.salir() }
        olvidarLocal()
        estado = .fuera(aviso: nil)
    }

    private func olvidarLocal() {
        almacen.olvidarTodo()
        llave.borrar()
        ultimoFaceId = nil
    }

    // MARK: Segundo plano

    public func fueASegundoPlano() {
        if enSegundoPlanoDesde == nil { enSegundoPlanoDesde = reloj() }
    }

    /// Al volver: tras 5 min fuera, o pasada la hora desde el Face ID, pide
    /// Face ID otra vez.
    public func volvio() {
        defer { enSegundoPlanoDesde = nil }
        guard estado.usuario != nil else { return }
        let ahora = reloj()
        let fuera = enSegundoPlanoDesde.map { ahora.timeIntervalSince($0) } ?? 0
        let vencida = ultimoFaceId.map { ahora.timeIntervalSince($0) >= Self.duracionDeLaSesion } ?? false
        if fuera >= Self.bloqueoEnSegundoPlano || vencida {
            almacen.borrarSesion()
            faceIdAutomatico = true
            estado = .bloqueada(nombre: almacen.persona())
        }
    }
}
