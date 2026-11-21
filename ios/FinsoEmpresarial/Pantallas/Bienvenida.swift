import SwiftUI

/// Primera pantalla (§3.1.1): logo, «Tu empresa, en tu bolsillo» y el botón
/// menta «Entrar». Siempre en oscuro, sobre el fondo de marca.
struct Bienvenida: View {
    let simulada: Bool
    var aviso: String?
    let entrar: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reducir
    @State private var visible = false

    var body: some View {
        ZStack {
            FondoDeAcceso()
            VStack(alignment: .leading, spacing: 0) {
                MarcaFinso(alto: 34)
                    .foregroundStyle(Finso.sobreSaldo)
                    .padding(.top, 12)
                Spacer()
                VStack(alignment: .leading, spacing: 14) {
                    Text("Tu empresa,\nen tu bolsillo")
                        .font(Letra.gully(40, .bold, relativoA: .largeTitle))
                        .tracking(-1)
                        .foregroundStyle(Finso.sobreSaldo)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Revisa tu cuenta, manda y libera SPEI con Face ID.")
                        .font(Letra.cuerpoGrande)
                        .foregroundStyle(Finso.sobreSaldoSecundario)
                }
                .opacity(visible ? 1 : 0)
                .offset(y: visible || reducir ? 0 : 12)
                if let aviso {
                    Label(aviso, systemImage: "exclamationmark.circle")
                        .font(Letra.cuerpo)
                        .foregroundStyle(Finso.aviso)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Finso.avisoFondo, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .padding(.top, 20)
                }
                Button("Entrar", action: entrar)
                    .buttonStyle(.principal)
                    .padding(.top, 32)
                    .opacity(visible ? 1 : 0)
                if simulada {
                    Text("Modo de prueba: datos simulados")
                        .font(Letra.chica)
                        .foregroundStyle(Finso.sobreSaldoSecundario)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 14)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
            .frame(maxWidth: 520)
        }
        .environment(\.colorScheme, .dark)
        .onAppear { withAnimation(Movimiento.lento.delay(0.1)) { visible = true } }
    }
}

#Preview { Bienvenida(simulada: true) {} }
