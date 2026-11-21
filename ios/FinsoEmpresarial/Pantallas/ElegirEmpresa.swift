import FinsoNucleo
import SwiftUI

/// Varias empresas (§3.2): «Hola, Luis · Trabajas con varias empresas».
struct ElegirEmpresa: View {
    let control: ControlDeSesion
    let usuario: Usuario

    @State private var eligiendo: String?
    @State private var error: String?

    var body: some View {
        ZStack {
            Finso.fondo.ignoresSafeArea()
            ColumnaDeAcceso {
                EncabezadoDePaso(
                    paso: nil, titulo: "Hola, \(usuario.nombreCorto)",
                    explicacion: "Trabajas con varias empresas. ¿Con cuál quieres entrar?")
                    .padding(.top, 24)
                VStack(spacing: 12) {
                    ForEach(Array(usuario.empresas.enumerated()), id: \.element.id) { i, empresa in
                        Button {
                            Task { await elegir(empresa.id) }
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "building.2")
                                    .font(.system(size: 20, weight: .medium))
                                    .foregroundStyle(Finso.acento)
                                    .frame(width: 44, height: 44)
                                    .background(Finso.elevado, in: Circle())
                                Text(empresa.nombre)
                                    .font(Letra.titulo)
                                    .foregroundStyle(Finso.texto)
                                    .multilineTextAlignment(.leading)
                                Spacer()
                                if eligiendo == empresa.id { ProgressView().tint(Finso.acento) }
                            }
                            .padding(16)
                            .background(Finso.tarjeta, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .shadow(color: Finso.sombra, radius: 12, y: 4)
                        }
                        .buttonStyle(Presionable())
                        .disabled(eligiendo != nil)
                        .accessibilityIdentifier("empresa-\(empresa.id)")
                        .escalonado(i)
                    }
                }
                if let error { AvisoDeError(texto: error) }
            }
        }
        .sensoryFeedback(.selection, trigger: eligiendo)
        .animation(Movimiento.rapido, value: error)
    }

    private func elegir(_ id: String) async {
        eligiendo = id
        error = nil
        defer { eligiendo = nil }
        do {
            try await control.elegirEmpresa(id)
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }
}

/// Se encoge un poco al tocar, con resorte.
struct Presionable: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(Movimiento.resorte, value: configuration.isPressed)
    }
}
