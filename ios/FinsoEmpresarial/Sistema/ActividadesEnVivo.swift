import ActivityKit
import FinsoNucleo
import Foundation

/// Lleva la actividad en vivo de cada SPEI (§11): la inicia al pedirlo o al
/// liberarlo, la actualiza mientras la app consulta la orden y la termina al
/// llegar a un estado final. Con la app cerrada, la actualiza el servidor con
/// push de ActivityKit; para eso se le manda el token (contrato §5.1).
@MainActor
final class ActividadesEnVivo {
    static let compartidas = ActividadesEnVivo()

    /// Sin noticias en 30 min, la actividad dice «Sin confirmar, abre la app».
    static let vieneVieja: TimeInterval = 30 * 60
    /// Al terminar se queda 15 min en la pantalla bloqueada.
    static let seQuita: TimeInterval = 15 * 60

    var api: FinsoApi?
    private var tokens: [String: Task<Void, Never>] = [:]

    private init() {}

    private func contenido(_ o: Orden) -> ActivityContent<ActividadSpei.ContentState> {
        let estado = ActividadSpei.ContentState(
            estado: o.estado.rawValue, liberadaPor: PresentacionDeActividad.primerNombre(o.liberadaPor),
            actualizadaEn: PresentacionDeActividad.fecha(o.actualizadaEn))
        return ActivityContent(
            state: estado, staleDate: o.estado.esFinal ? nil : Date().addingTimeInterval(Self.vieneVieja),
            relevanceScore: o.estado.esFinal ? 0 : 50)
    }

    /// Inicia o actualiza la actividad de la orden.
    func seguir(_ o: Orden) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let c = contenido(o)
        if let a = Activity<ActividadSpei>.activities.first(where: { $0.attributes.ref == o.id }) {
            guard a.content.state != c.state else { return }
            Task {
                if o.estado.esFinal {
                    await a.end(c, dismissalPolicy: .after(Date().addingTimeInterval(Self.seQuita)))
                    tokens[o.id]?.cancel()
                    tokens[o.id] = nil
                } else {
                    await a.update(c)
                }
            }
            return
        }
        guard !o.estado.esFinal else { return }
        let atributos = ActividadSpei(
            ref: o.id, importe: PresentacionDeActividad.importe(o.importeCentavos),
            destino: Nombre.legible(o.titular))
        let actividad: Activity<ActividadSpei>
        do {
            actividad = try Activity.request(attributes: atributos, content: c, pushType: .token)
        } catch {
            // Sin permiso de push (p. ej. en el simulador): solo local.
            guard let a = try? Activity.request(attributes: atributos, content: c, pushType: nil) else { return }
            actividad = a
        }
        let id = o.id
        tokens[id] = Task { [weak self] in
            for await t in actividad.pushTokenUpdates {
                try? await self?.api?.avisarActividad(orden: id, token: PresentacionDeActividad.hex(t))
            }
        }
    }
}
