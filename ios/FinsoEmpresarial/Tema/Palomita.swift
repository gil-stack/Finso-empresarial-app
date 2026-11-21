import SwiftUI

/// Palomita de éxito (§10): disco de vidrio con el trazo verde que se dibuja
/// y se va sola, sin texto. Se muestra con `.palomita(cuando:)`.
struct Palomita: View {
    @State private var dibujada = false

    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 34, weight: .bold))
            .foregroundStyle(Finso.palomita)
            .symbolEffect(.bounce, value: dibujada)
            .scaleEffect(dibujada ? 1 : 0.6)
            .opacity(dibujada ? 1 : 0)
            .frame(width: 92, height: 92)
            .vidrio(Circle())
            .onAppear { withAnimation(Ritmo.resorte) { dibujada = true } }
            .accessibilityLabel("Listo")
    }
}

private struct ConPalomita: ViewModifier {
    let disparador: Int
    @State private var visible = false

    func body(content: Content) -> some View {
        content
            .overlay {
                if visible {
                    Palomita()
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                        .allowsHitTesting(false)
                }
            }
            .sensoryFeedback(.success, trigger: disparador)
            .onChange(of: disparador) { _, _ in
                withAnimation(Ritmo.normal) { visible = true }
                Task {
                    try? await Task.sleep(for: .seconds(1.1))
                    withAnimation(Ritmo.normal) { visible = false }
                }
            }
    }
}

extension View {
    /// Muestra la palomita cada vez que cambia `disparador`.
    func palomita(cuando disparador: Int) -> some View { modifier(ConPalomita(disparador: disparador)) }
}

/// Insignia de estado de una orden.
struct InsigniaDeEstado: View {
    let texto: String
    let color: Color
    let fondo: Color

    var body: some View {
        Text(texto)
            .font(Letra.etiqueta)
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(fondo, in: Capsule())
    }
}

/// Renglón «Etiqueta ····· valor» de las tarjetas de detalle.
struct RenglonDeDetalle: View {
    let etiqueta: String
    let valor: String
    var copiable = false

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(etiqueta)
                .font(Letra.cuerpo)
                .foregroundStyle(Finso.secundario)
            Spacer(minLength: 16)
            Text(valor)
                .font(Letra.cuerpo.weight(.medium))
                .foregroundStyle(Finso.texto)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
        .padding(.vertical, 10)
        .accessibilityElement(children: .combine)
    }
}

/// Tarjeta blanca (o elevada en oscuro) para agrupar renglones.
struct Tarjeta<Contenido: View>: View {
    @ViewBuilder var contenido: Contenido

    var body: some View {
        VStack(spacing: 0) { contenido }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Finso.tarjeta, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Finso.sombra, radius: 14, y: 4)
    }
}
