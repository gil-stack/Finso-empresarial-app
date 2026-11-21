import FinsoNucleo
import SwiftUI

/// Ajustes (§3.6), en hoja desde abajo.
struct Ajustes: View {
    let app: EstadoDeLaApp
    let usuario: Usuario
    @AppStorage("apariencia") private var apariencia = Apariencia.sistema
    @Environment(\.dismiss) private var cerrar
    @State private var confirmarOlvidar = false

    private var control: ControlDeSesion { app.control }

    var body: some View {
        NavigationStack {
            List {
                Section("Apariencia") {
                    Picker("Apariencia", selection: $apariencia) {
                        ForEach(Apariencia.allCases) { Text($0.nombre).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .accessibilityIdentifier("apariencia")
                }
                Section {
                    if usuario.empresas.count > 1 {
                        Button {
                            cerrar()
                            control.cambiarDeEmpresa()
                        } label: {
                            Label("Cambiar de empresa", systemImage: "building.2")
                        }
                        .accessibilityIdentifier("cambiarEmpresa")
                    }
                    Button {
                        cerrar()
                        Task { await control.salir() }
                    } label: {
                        Label("Salir", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                    .accessibilityIdentifier("salir")
                } footer: {
                    Text("La próxima vez entras con \(Biometria.nombre).")
                }
                Section {
                    Button(role: .destructive) {
                        confirmarOlvidar = true
                    } label: {
                        Label("Olvidar este teléfono", systemImage: "iphone.slash")
                    }
                    .accessibilityIdentifier("olvidar")
                } footer: {
                    Text("Finso Empresarial \(Configuracion.version)\(app.simulada ? " · datos simulados" : "")")
                        .frame(maxWidth: .infinity)
                        .padding(.top, 16)
                }
            }
            .font(Letra.cuerpoGrande)
            .tint(Finso.acento)
            .scrollContentBackground(.hidden)
            .background(Finso.fondo)
            .navigationTitle("Ajustes")
            .navigationBarTitleDisplayMode(.inline)
            .confirmationDialog(
                "Para volver a entrar vas a necesitar tu correo, tu contraseña y los códigos.",
                isPresented: $confirmarOlvidar, titleVisibility: .visible
            ) {
                Button("Olvidar este teléfono", role: .destructive) {
                    cerrar()
                    Task { await control.olvidarTelefono() }
                }
                Button("Cancelar", role: .cancel) {}
            }
        }
    }
}
