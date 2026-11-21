import SwiftUI

// Movimiento de la especificación §4: rápido 180 ms, normal 320 ms, lento
// 520 ms, con curva easeOutCubic. Con «reducir movimiento» solo hay fundidos.

enum Ritmo {
    static let curva = (x1: 0.33, y1: 1.0, x2: 0.68, y2: 1.0)

    static func cubica(_ segundos: Double) -> Animation {
        .timingCurve(curva.x1, curva.y1, curva.x2, curva.y2, duration: segundos)
    }

    static let rapido = cubica(0.18)
    static let normal = cubica(0.32)
    static let lento = cubica(0.52)
    static let resorte = Animation.spring(response: 0.28, dampingFraction: 0.62)

    /// Entre pasos del acceso: entra 6 % desde la derecha con fundido.
    static func paso(reducir: Bool) -> AnyTransition {
        reducir
            ? .opacity
            : .asymmetric(
                insertion: .modifier(
                    active: Desplazado(x: 0.06, opacidad: 0), identity: Desplazado(x: 0, opacidad: 1)),
                removal: .opacity)
    }
}

/// Desplaza una fracción del ancho y cambia la opacidad.
struct Desplazado: ViewModifier {
    let x: CGFloat
    let opacidad: Double

    func body(content: Content) -> some View {
        content
            .visualEffect { c, geo in c.offset(x: geo.size.width * x) }
            .opacity(opacidad)
    }
}

/// Aparece subiendo 8 pt con fundido, escalonado por renglón (30 ms).
struct Escalonado: ViewModifier {
    let indice: Int
    @State private var visible = false
    @Environment(\.accessibilityReduceMotion) private var reducir

    func body(content: Content) -> some View {
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible || reducir ? 0 : 8)
            .onAppear {
                withAnimation(Ritmo.normal.delay(Double(min(indice, 12)) * 0.03)) { visible = true }
            }
    }
}

extension View {
    func escalonado(_ indice: Int) -> some View { modifier(Escalonado(indice: indice)) }
}
