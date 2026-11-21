import SwiftUI

// Piezas compartidas. Siguen el lenguaje del sistema (§10): vidrio del
// sistema en iOS 26 (Liquid Glass) y materiales antes; nunca vidrio pintado.

// MARK: - Botón principal

/// Menta con letra tinta (regla de marca, también en oscuro). Ancho completo,
/// alto 56, radio 20; se encoge a 0.97 con un resorte al tocarlo.
struct BotonPrincipal: ButtonStyle {
    var cargando = false
    @Environment(\.isEnabled) private var habilitado

    func makeBody(configuration: Configuration) -> some View {
        ZStack {
            configuration.label
                .font(Letra.boton)
                .tracking(-0.2)
                .opacity(cargando ? 0 : 1)
            if cargando {
                ProgressView().tint(Finso.tinta)
            }
        }
        .foregroundStyle(Finso.sobreAccion.opacity(habilitado ? 1 : 0.5))
        .frame(maxWidth: .infinity, minHeight: 56)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Finso.accion.opacity(habilitado ? 1 : 0.2)))
        .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .scaleEffect(configuration.isPressed ? 0.97 : 1)
        .animation(Ritmo.resorte, value: configuration.isPressed)
        .animation(Ritmo.rapido, value: cargando)
        .accessibilityAddTraits(.isButton)
    }
}

extension ButtonStyle where Self == BotonPrincipal {
    static var principal: BotonPrincipal { BotonPrincipal() }
    static func principal(cargando: Bool) -> BotonPrincipal { BotonPrincipal(cargando: cargando) }
}

// MARK: - Vidrio

extension View {
    /// Vidrio del sistema: `.glassEffect()` en iOS 26 y material fino antes.
    @ViewBuilder
    func vidrio<S: Shape>(_ forma: S) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: forma)
        } else {
            background(.ultraThinMaterial, in: forma)
        }
        #else
        background(.ultraThinMaterial, in: forma)
        #endif
    }

    func vidrioCapsula() -> some View { vidrio(Capsule()) }
}

/// Botón redondo de vidrio (copiar CLABE, ojo, ajustes).
struct BotonRedondo: View {
    let simbolo: String
    let etiqueta: String
    let accion: () -> Void

    var body: some View {
        Button(action: accion) {
            Image(systemName: simbolo)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .vidrio(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(etiqueta)
    }
}

// MARK: - Fondo de acceso (§3.1)

/// Fondo de marca de las pantallas de acceso: degradado, halo menta arriba a
/// la izquierda, viñeta abajo y el patrón de Finso muy tenue.
struct FondoDeAcceso: View {
    var body: some View {
        ZStack {
            Finso.accesoBase
            LinearGradient(
                colors: [Finso.accesoArriba, Finso.accesoMedio, Finso.accesoBase],
                startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.55))
            RadialGradient(
                colors: [Finso.accesoHalo.opacity(0.42), Finso.accesoHalo.opacity(0)],
                center: UnitPoint(x: 0.1, y: 0.05), startRadius: 0, endRadius: 420)
            // En un overlay para que el patrón no agrande el fondo.
            Color.clear
                .overlay {
                    Image("PatronFinso")
                        .resizable()
                        .scaledToFill()
                        .opacity(0.05)
                        .blendMode(.screen)
                }
                .clipped()
                .accessibilityHidden(true)
            LinearGradient(
                colors: [Finso.accesoVineta.opacity(0), Finso.accesoVineta.opacity(0.94)],
                startPoint: UnitPoint(x: 0.5, y: 0.55), endPoint: .bottom)
        }
        .ignoresSafeArea()
    }
}

/// La marca de Finso (plantilla: toma el color del texto).
struct MarcaFinso: View {
    var alto: CGFloat = 34

    var body: some View {
        Image("FinsoMarca")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(height: alto)
            .accessibilityLabel("Finso")
    }
}
