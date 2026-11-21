import FinsoNucleo
import Foundation

/// Lo que se decide al compilar (especificación §1). `FINSO_API` y
/// `FINSO_API_URL` vienen de `ios/Config/*.xcconfig` vía Info.plist.
enum Configuracion {
    static var info: [String: Any] { Bundle.main.infoDictionary ?? [:] }

    /// `simulada` (datos de prueba) o `real` (Finso Empresas).
    static var simulada: Bool {
        if let forzada = ProcessInfo.processInfo.environment["FINSO_API"] { return forzada != "real" }
        return (info["FinsoApi"] as? String ?? "real") != "real"
    }

    static var urlDeLaApi: URL {
        URL(string: info["FinsoApiUrl"] as? String ?? "") ?? URL(string: "https://empresas.finsocial.app/movil/v1")!
    }

    static var version: String {
        let v = info["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let b = info["CFBundleVersion"] as? String ?? "1"
        return "\(v)+\(b)"
    }

    /// Para pruebas de pantalla y capturas para tiendas: se apaga el lienzo
    /// seguro (si no, las capturas salen negras). Solo en Debug.
    static var capturasPermitidas: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("-finsoCapturas")
        #else
        return false
        #endif
    }

    /// API, almacén y llave según el modo.
    static func piezas() -> (api: FinsoApi, almacen: AlmacenDeSesion, llave: LlaveDelDispositivo) {
        let llave: LlaveDelDispositivo
        #if targetEnvironment(simulator)
        // El simulador no tiene Secure Enclave.
        llave = LlaveSimulada()
        #else
        llave = LlaveSecureEnclave()
        #endif
        if simulada {
            let api: FinsoApi = ApiSimulada()
            let almacen: AlmacenDeSesion = AlmacenEnMemoria()
            return (api, almacen, llave)
        }
        let llavero = AlmacenLlavero()
        let api: FinsoApi = ApiHttp(almacen: llavero, base: urlDeLaApi, version: version)
        let almacen: AlmacenDeSesion = llavero
        return (api, almacen, llave)
    }
}
