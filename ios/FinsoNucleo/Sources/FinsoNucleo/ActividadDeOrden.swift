import Foundation

/// Lo que muestra la actividad en vivo de una orden (Dynamic Island y
/// pantalla bloqueada, especificación §11). Nunca saldo ni CLABE completa:
/// se ve sin desbloquear.
public struct PresentacionDeActividad: Equatable, Sendable {
    public enum Tono: String, Sendable { case aviso, menta, exito, secundario }

    public var simbolo: String
    public var titulo: String
    public var tono: Tono
    public var final: Bool

    /// Sin noticias en 30 min (`staleDate`).
    public static let vieja = PresentacionDeActividad(
        simbolo: "clock.arrow.circlepath", titulo: "Sin confirmar, abre la app", tono: .secundario, final: false)

    /// `estado` es el texto del contrato (`POR_LIBERAR`, `LIQUIDADA`, …).
    public init(estado: String, liberadaPor: String?) {
        switch EstadoOrden(rawValue: estado) {
        case .porLiberar?:
            self.init(simbolo: "hourglass", titulo: "Falta que la liberen", tono: .aviso, final: false)
        case .enProceso?, .enviada?:
            self.init(
                simbolo: "arrow.up.right", titulo: liberadaPor.map { "Liberado por \($0)" } ?? "Enviando…",
                tono: .menta, final: false)
        case .liquidada?:
            self.init(simbolo: "checkmark", titulo: "Enviado", tono: .exito, final: true)
        case .rechazada?:
            self.init(simbolo: "xmark", titulo: "Rechazado", tono: .secundario, final: true)
        case .devuelta?:
            self.init(simbolo: "arrow.uturn.backward", titulo: "Devuelto", tono: .secundario, final: true)
        case .cancelada?:
            self.init(simbolo: "xmark", titulo: "Cancelado", tono: .secundario, final: true)
        case nil:
            self = .vieja
        }
    }

    init(simbolo: String, titulo: String, tono: Tono, final: Bool) {
        self.simbolo = simbolo
        self.titulo = titulo
        self.tono = tono
        self.final = final
    }

    /// Importe para la isla: sin «.00» si son cero (`$18,450`).
    public static func importe(_ centavos: Int) -> String {
        Dinero.pesos(centavos, sinCentavos: centavos % 100 == 0)
    }

    /// Fecha del contrato: ISO sin milisegundos.
    public static func fecha(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: d)
    }

    /// Token de push en hexadecimal, como lo pide el servidor.
    public static func hex(_ token: Data) -> String { token.map { String(format: "%02x", $0) }.joined() }

    /// Solo el primer nombre (`Ana López Garza` → `Ana`).
    public static func primerNombre(_ n: String?) -> String? {
        n.flatMap { $0.split(separator: " ").first.map(String.init) }
    }
}
