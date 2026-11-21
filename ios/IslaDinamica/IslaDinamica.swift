import ActivityKit
import FinsoNucleo
import SwiftUI
import WidgetKit

@main
struct IslaDinamicaBundle: WidgetBundle {
    var body: some Widget { SpeiEnVivo() }
}

/// El SPEI en curso en la Dynamic Island y en la pantalla bloqueada (§11).
/// Nunca saldo ni CLABE. En la pantalla bloqueada solo va el estado: el
/// importe y el destino se ven en la isla, marcados como sensibles para que
/// el sistema los oculte si el teléfono está bloqueado.
struct SpeiEnVivo: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ActividadSpei.self) { context in
            PantallaBloqueada(context: context)
                .widgetURL(ActividadSpei.enlace(context.attributes.ref))
                .activityBackgroundTint(nil)
                .activitySystemActionForegroundColor(Isla.menta)
        } dynamicIsland: { context in
            let p = Isla.presentacion(context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Marca()
                        .foregroundStyle(.white.opacity(0.92))
                        .padding(.leading, 6)
                        .padding(.top, 6)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Simbolo(p: p, lado: 38).padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Detalle(context: context, p: p)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 6)
                        .padding(.bottom, 8)
                }
            } compactLeading: {
                Simbolo(p: p, lado: 22)
            } compactTrailing: {
                Text(context.attributes.importe)
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Isla.color(p.tono))
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                    .privacySensitive()
            } minimal: {
                Simbolo(p: p, lado: 22)
            }
            .widgetURL(ActividadSpei.enlace(context.attributes.ref))
            .keylineTint(Isla.menta)
        }
    }
}

enum Isla {
    static let menta = Color(red: 0x1D / 255, green: 0xBB / 255, blue: 0x9D / 255)
    static let verde = Color(red: 0x49 / 255, green: 0xC0 / 255, blue: 0x95 / 255)
    static let ambar = Color(red: 0xF5 / 255, green: 0xB5 / 255, blue: 0x44 / 255)

    static func presentacion(_ c: ActivityViewContext<ActividadSpei>) -> PresentacionDeActividad {
        let p = PresentacionDeActividad(estado: c.state.estado, liberadaPor: c.state.liberadaPor)
        return c.isStale && !p.final ? .vieja : p
    }

    static func color(_ t: PresentacionDeActividad.Tono) -> Color {
        switch t {
        case .aviso: return ambar
        case .menta: return menta
        case .exito: return verde
        case .secundario: return .secondary
        }
    }
}

/// La palabra «finso», sin contenedor.
struct Marca: View {
    var body: some View {
        Image("FinsoMarca")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(height: 13)
            .accessibilityLabel("Finso")
    }
}

/// El estado en un símbolo dentro de un disco; cambia con resorte.
struct Simbolo: View {
    let p: PresentacionDeActividad
    let lado: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Isla.color(p.tono).opacity(0.18))
            Image(systemName: p.simbolo)
                .font(.system(size: lado * 0.45, weight: .bold))
                .foregroundStyle(Isla.color(p.tono))
                .contentTransition(.symbolEffect(.replace))
        }
        .frame(width: lado, height: lado)
        .animation(.spring(response: 0.55, dampingFraction: 0.68), value: p)
        .accessibilityLabel(p.titulo)
    }
}

/// Importe grande, «a Fletes Rapidos del Norte» y el estado. Si no cabe, se
/// acorta el nombre, nunca el importe.
struct Detalle: View {
    let context: ActivityViewContext<ActividadSpei>
    let p: PresentacionDeActividad

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(context.attributes.importe)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .layoutPriority(1)
                    .fixedSize()
                Text("a \(context.attributes.destino)")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .privacySensitive()
            Text(p.titulo)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Isla.color(p.tono))
            if context.state.estado == "LIQUIDADA" {
                Link(destination: ActividadSpei.enlace(context.attributes.ref)) {
                    Text("Ver")
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Isla.menta.opacity(0.2), in: Capsule())
                }
                .padding(.top, 4)
            }
        }
    }
}

/// Pantalla bloqueada: sigue el modo del teléfono y solo dice el estado, sin
/// importe ni destino (la ve cualquiera sin desbloquear; igual que Android).
struct PantallaBloqueada: View {
    let context: ActivityViewContext<ActividadSpei>

    var body: some View {
        let p = Isla.presentacion(context)
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                Marca().foregroundStyle(.primary)
                Text("Tu SPEI")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)
                Text(p.titulo)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Isla.color(p.tono))
                Text("Abre la app para ver el detalle.")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Simbolo(p: p, lado: 44)
        }
        .padding(16)
    }
}
