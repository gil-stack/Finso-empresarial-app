import Foundation

/// Dónde se guardan la sesión y el teléfono. En el iPhone va en el llavero
/// (solo este teléfono, sin respaldo); en pruebas, en memoria.
public protocol AlmacenDeSesion: AnyObject, Sendable {
    func sesion() -> Sesion?
    func guardar(_ sesion: Sesion)
    /// Borra solo la sesión (el teléfono sigue registrado).
    func borrarSesion()
    func dispositivo() -> String?
    func guardarDispositivo(_ id: String)
    /// Nombre corto de quien entró, para saludar al desbloquear.
    func persona() -> String?
    func guardarPersona(_ nombre: String)
    /// Empresa elegida si la persona tiene varias (`X-Finso-Empresa`).
    func empresa() -> String?
    func guardarEmpresa(_ id: String?)
    /// Olvida todo: sesión, teléfono, persona y empresa.
    func olvidarTodo()
}

public final class AlmacenEnMemoria: AlmacenDeSesion, @unchecked Sendable {
    private let lock = NSLock()
    private var _sesion: Sesion?
    public var dispositivoGuardado: String?
    public var personaGuardada: String?
    public var empresaGuardada: String?

    public init() {}

    private func con<T>(_ f: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return f()
    }

    public func sesion() -> Sesion? { con { _sesion } }
    public func guardar(_ sesion: Sesion) { con { _sesion = sesion } }
    public func borrarSesion() { con { _sesion = nil } }
    public func dispositivo() -> String? { con { dispositivoGuardado } }
    public func guardarDispositivo(_ id: String) { con { dispositivoGuardado = id } }
    public func persona() -> String? { con { personaGuardada } }
    public func guardarPersona(_ nombre: String) { con { personaGuardada = nombre } }
    public func empresa() -> String? { con { empresaGuardada } }
    public func guardarEmpresa(_ id: String?) { con { empresaGuardada = id } }
    public func olvidarTodo() {
        con {
            _sesion = nil
            dispositivoGuardado = nil
            personaGuardada = nil
            empresaGuardada = nil
        }
    }
}
