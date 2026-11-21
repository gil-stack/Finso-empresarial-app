import ActivityKit
import Foundation

/// Actividad en vivo de un SPEI (especificación §11, contrato §5.1). La
/// comparten la app y la extensión de la isla. El `ContentState` es
/// exactamente el del push del servidor: no cambiar sin cambiarlo allá.
struct ActividadSpei: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// `POR_LIBERAR`, `EN_PROCESO`, `LIQUIDADA`, …
        var estado: String
        /// Primer nombre de quien liberó.
        var liberadaPor: String?
        /// ISO sin milisegundos.
        var actualizadaEn: String
    }

    /// Id opaco de la orden (`spo_…`).
    var ref: String
    /// Importe ya formateado (`$18,450`).
    var importe: String
    /// Nombre legible del destino.
    var destino: String
}

extension ActividadSpei {
    /// Lo que abre la app al tocar la actividad.
    static func enlace(_ ref: String) -> URL {
        var c = URLComponents()
        c.scheme = "finso-empresarial"
        c.host = "orden"
        c.queryItems = [URLQueryItem(name: "ref", value: ref)]
        return c.url ?? URL(string: "finso-empresarial://orden")!
    }
}
