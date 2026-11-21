import FinsoNucleo
import SwiftUI
import UIKit

@main
struct FinsoEmpresarialApp: App {
    @State private var app = EstadoDeLaApp()

    var body: some Scene {
        WindowGroup {
            LienzoSeguro(activo: !Configuracion.capturasPermitidas, contenido: Raiz(app: app))
                .ignoresSafeArea()
        }
    }
}

/// Lo que comparte toda la app. En el PR de «entrar» se le agrega el control
/// de la sesión (fuera, bloqueada, dentro).
@Observable
final class EstadoDeLaApp {
    let api: FinsoApi
    let almacen: AlmacenDeSesion
    let llave: LlaveDelDispositivo
    let simulada = Configuracion.simulada

    init() {
        let p = Configuracion.piezas()
        api = p.api
        almacen = p.almacen
        llave = p.llave
    }
}

/// Raíz de la app: pantalla según el estado, tapa de privacidad al ir a
/// segundo plano y aviso tras una captura de pantalla.
struct Raiz: View {
    let app: EstadoDeLaApp
    @Environment(\.scenePhase) private var fase
    @State private var avisoDeCaptura = false

    var body: some View {
        ZStack {
            Bienvenida(simulada: app.simulada) {}
            if fase != .active {
                TapaDePrivacidad().transition(.opacity)
            }
        }
        .animation(Movimiento.rapido, value: fase)
        .overlay(alignment: .bottom) {
            if avisoDeCaptura {
                AvisoCapsula(simbolo: "eye.slash", texto: "Por seguridad, las capturas salen en negro.")
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.userDidTakeScreenshotNotification)) { _ in
            guard !Configuracion.capturasPermitidas else { return }
            withAnimation(Movimiento.normal) { avisoDeCaptura = true }
            Task {
                try? await Task.sleep(for: .seconds(3))
                withAnimation(Movimiento.normal) { avisoDeCaptura = false }
            }
        }
    }
}

/// Cubre la app en el selector de apps: no se ven saldos (§2).
struct TapaDePrivacidad: View {
    var body: some View {
        ZStack {
            FondoDeAcceso()
            MarcaFinso(alto: 40).foregroundStyle(Finso.sobreSaldo)
        }
        .accessibilityHidden(true)
    }
}

/// Aviso con texto: cápsula de vidrio con símbolo y una línea (§10). Deja
/// pasar los toques y VoiceOver lo lee.
struct AvisoCapsula: View {
    let simbolo: String
    let texto: String

    var body: some View {
        Label(texto, systemImage: simbolo)
            .font(Letra.cuerpo)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .vidrioCapsula()
            .allowsHitTesting(false)
            .accessibilityAddTraits(.isStaticText)
            .onAppear { UIAccessibility.post(notification: .announcement, argument: texto) }
    }
}
