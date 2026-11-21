import FinsoNucleo
import Foundation
import Security

/// Sesión, teléfono, nombre corto y empresa en el llavero del iPhone
/// (especificación §2): solo este teléfono, sin respaldo en iCloud.
final class AlmacenLlavero: AlmacenDeSesion, @unchecked Sendable {
    private let servicio: String
    private let lock = NSLock()

    private enum Clave: String, CaseIterable {
        case sesion, dispositivo, persona, empresa
    }

    init(servicio: String = "mx.finsocial.empresarial") {
        self.servicio = servicio
        borrarSiEsInstalacionNueva()
    }

    /// El llavero sobrevive a desinstalar la app. En el primer arranque
    /// después de instalar se borra (la marca vive en UserDefaults, que sí se
    /// borra al desinstalar).
    private func borrarSiEsInstalacionNueva() {
        let marca = "finso.yaArranco"
        guard !UserDefaults.standard.bool(forKey: marca) else { return }
        olvidarTodo()
        LlaveSecureEnclave().borrar()
        UserDefaults.standard.set(true, forKey: marca)
    }

    private func base(_ clave: Clave) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: clave.rawValue,
        ]
    }

    private func leer(_ clave: Clave) -> Data? {
        var q = base(clave)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var r: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &r) == errSecSuccess else { return nil }
        return r as? Data
    }

    private func escribir(_ clave: Clave, _ datos: Data?) {
        lock.lock()
        defer { lock.unlock() }
        SecItemDelete(base(clave) as CFDictionary)
        guard let datos else { return }
        var q = base(clave)
        q[kSecValueData as String] = datos
        q[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }

    private func texto(_ clave: Clave) -> String? { leer(clave).flatMap { String(data: $0, encoding: .utf8) } }
    private func guardarTexto(_ clave: Clave, _ s: String?) { escribir(clave, s.map { Data($0.utf8) }) }

    func sesion() -> Sesion? {
        guard let d = leer(.sesion), let j = try? JSONSerialization.jsonObject(with: d) else { return nil }
        return try? Sesion(json: j)
    }

    func guardar(_ sesion: Sesion) {
        escribir(.sesion, try? JSONSerialization.data(withJSONObject: sesion.json))
    }

    func borrarSesion() { escribir(.sesion, nil) }
    func dispositivo() -> String? { texto(.dispositivo) }
    func guardarDispositivo(_ id: String) { guardarTexto(.dispositivo, id) }
    func persona() -> String? { texto(.persona) }
    func guardarPersona(_ nombre: String) { guardarTexto(.persona, nombre) }
    func empresa() -> String? { texto(.empresa) }
    func guardarEmpresa(_ id: String?) { guardarTexto(.empresa, id) }

    func olvidarTodo() {
        for c in Clave.allCases { escribir(c, nil) }
    }
}
