import FinsoNucleo
import SwiftUI

/// Ingresos siguientes (§3.1.5): «Hola, Ana», Face ID solo al abrir la app y
/// «Entrar con otra cuenta».
struct Desbloqueo: View {
    let control: ControlDeSesion
    let nombre: String?
    let automatico: Bool

    @State private var ocupado = false
    @State private var error: String?
    @State private var confirmarOtraCuenta = false
    @State private var yaSeLanzo = false

    var body: some View {
        ZStack {
            FondoDeAcceso()
            VStack(alignment: .leading, spacing: 0) {
                MarcaFinso(alto: 30)
                    .foregroundStyle(Finso.sobreSaldo)
                    .padding(.top, 12)
                Spacer()
                Image(systemName: Biometria.simbolo)
                    .font(.system(size: 60, weight: .light))
                    .foregroundStyle(Finso.menta)
                    .symbolEffect(.pulse, options: .repeating, isActive: ocupado)
                    .padding(.bottom, 24)
                    .accessibilityHidden(true)
                Text(nombre.map { "Hola, \($0)" } ?? "Hola")
                    .font(Letra.gully(36, .bold, relativoA: .largeTitle))
                    .tracking(-0.8)
                    .foregroundStyle(Finso.sobreSaldo)
                    .accessibilityIdentifier("saludo")
                Text("Entra con \(Biometria.nombre) para ver tu cuenta.")
                    .font(Letra.cuerpoGrande)
                    .foregroundStyle(Finso.sobreSaldoSecundario)
                    .padding(.top, 8)
                if let error {
                    AvisoDeError(texto: error).padding(.top, 20)
                }
                Button("Entrar con \(Biometria.nombre)") { Task { await entrar() } }
                    .buttonStyle(.principal(cargando: ocupado))
                    .accessibilityIdentifier("entrarBiometria")
                    .padding(.top, 32)
                Button("Entrar con otra cuenta") { confirmarOtraCuenta = true }
                    .font(Letra.subtitulo)
                    .foregroundStyle(Finso.acento)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.top, 10)
                    .accessibilityIdentifier("otraCuenta")
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
            .frame(maxWidth: 520)
        }
        .environment(\.colorScheme, .dark)
        .animation(Movimiento.rapido, value: error)
        .confirmationDialog(
            "Para volver a entrar en este teléfono vas a necesitar tu correo, tu contraseña y los códigos.",
            isPresented: $confirmarOtraCuenta, titleVisibility: .visible
        ) {
            Button("Entrar con otra cuenta", role: .destructive) { Task { await control.olvidarTelefono() } }
            Button("Cancelar", role: .cancel) {}
        }
        .task {
            guard automatico, !yaSeLanzo else { return }
            yaSeLanzo = true
            await entrar()
        }
    }

    private func entrar() async {
        guard !ocupado else { return }
        ocupado = true
        error = nil
        defer { ocupado = false }
        do {
            try await control.desbloquear()
        } catch let e as ErrorDeLlave {
            if e.fallo != .cancelada { error = e.paraPersona }
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }
}
