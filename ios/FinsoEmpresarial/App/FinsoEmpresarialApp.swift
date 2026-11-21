import FinsoNucleo
import SwiftUI
import UIKit

@main
struct FinsoEmpresarialApp: App {
    @State private var app = EstadoDeLaApp()

    init() {
        // Selector segmentado: lo elegido en menta con letra tinta (§3.6).
        let s = UISegmentedControl.appearance()
        s.selectedSegmentTintColor = UIColor(hex: 0x1DBB9D)
        s.setTitleTextAttributes([.foregroundColor: UIColor(hex: 0x102D31)], for: .selected)
    }

    var body: some Scene {
        WindowGroup {
            LienzoSeguro(activo: !Configuracion.capturasPermitidas, contenido: Raiz(app: app))
                .ignoresSafeArea()
        }
    }
}

/// Lo que comparte toda la app: la sesión y el modo (simulado o real).
@MainActor
@Observable
final class EstadoDeLaApp {
    let control: ControlDeSesion
    let simulada = Configuracion.simulada
    let telefono: DatosDelTelefono

    init() {
        let p = Configuracion.piezas()
        control = ControlDeSesion(api: p.api, almacen: p.almacen, llave: p.llave)
        let d = UIDevice.current
        telefono = DatosDelTelefono(nombre: d.name, modelo: d.model, versionSistema: d.systemVersion)
        if let http = p.api as? ApiHttp {
            let control = self.control
            http.alCerrarSesion = { e in Task { @MainActor in control.cerradaPorServidor(e) } }
            http.alPedirEmpresa = { Task { @MainActor in control.pedirEmpresa() } }
        }
        control.arrancar()
    }

    func nuevoFlujo() -> FlujoDeEntrada { FlujoDeEntrada(control: control, telefono: telefono) }

    /// Un SPEI firmado cuya respuesta no llegó (§3.4, «SPEI sin confirmar»).
    /// Vive mientras la app siga abierta, también bloqueada; se olvida al
    /// salir, al entrar con otra cuenta o al cambiar de empresa.
    @ObservationIgnored private var speiPendiente: (dueno: String, envio: EnvioSpei)?

    private static func dueno(_ u: Usuario) -> String { u.id + "|" + (u.empresa?.id ?? "") }

    /// Lo que abre «Mandar SPEI»: el pendiente, si hay, o uno nuevo.
    func envioSpei(para usuario: Usuario, disponible: Int?) -> EnvioSpei {
        if let p = speiPendiente, p.dueno == Self.dueno(usuario), p.envio.sinConfirmar {
            p.envio.volverAlPendiente()
            return p.envio
        }
        speiPendiente = nil
        return EnvioSpei(api: control.api, almacen: control.almacen, llave: control.llave, disponible: disponible)
    }

    /// Al cerrar «Mandar SPEI»: se guarda solo si quedó sin confirmar.
    func cerroSpei(_ envio: EnvioSpei, usuario: Usuario) {
        speiPendiente = envio.sinConfirmar ? (Self.dueno(usuario), envio) : nil
    }

    func olvidarSpeiPendiente() { speiPendiente = nil }
}

/// Apariencia elegida en Ajustes (§3.6).
enum Apariencia: String, CaseIterable, Identifiable {
    case sistema, claro, oscuro
    var id: String { rawValue }
    var nombre: String {
        switch self {
        case .sistema: return "Sistema"
        case .claro: return "Claro"
        case .oscuro: return "Oscuro"
        }
    }
    var esquema: ColorScheme? {
        switch self {
        case .sistema: return nil
        case .claro: return .light
        case .oscuro: return .dark
        }
    }
}

/// Raíz de la app: pantalla según el estado de la sesión (con fundido),
/// bloqueo al volver de segundo plano, tapa de privacidad y aviso tras una
/// captura de pantalla.
struct Raiz: View {
    let app: EstadoDeLaApp
    @Environment(\.scenePhase) private var fase
    @AppStorage("apariencia") private var apariencia = Apariencia.sistema
    @State private var avisoDeCaptura = false

    private var control: ControlDeSesion { app.control }

    var body: some View {
        ZStack {
            pantalla
                .transition(.opacity)
            if fase != .active {
                TapaDePrivacidad().transition(.opacity)
            }
        }
        .animation(Ritmo.lento, value: control.estado)
        .animation(Ritmo.rapido, value: fase)
        .preferredColorScheme(apariencia.esquema)
        .overlay(alignment: .bottom) {
            if avisoDeCaptura {
                AvisoCapsula(simbolo: "eye.slash", texto: "Por seguridad, las capturas salen en negro.")
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onChange(of: control.estado) { _, nuevo in
            switch nuevo {
            case .fuera, .elegirEmpresa: app.olvidarSpeiPendiente()
            default: break
            }
        }
        .onChange(of: fase) { _, nueva in
            switch nueva {
            case .background: control.fueASegundoPlano()
            case .active: control.volvio()
            default: break
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.userDidTakeScreenshotNotification)) { _ in
            guard !Configuracion.capturasPermitidas else { return }
            withAnimation(Ritmo.normal) { avisoDeCaptura = true }
            Task {
                try? await Task.sleep(for: .seconds(3))
                withAnimation(Ritmo.normal) { avisoDeCaptura = false }
            }
        }
    }

    @ViewBuilder private var pantalla: some View {
        switch control.estado {
        case .arrancando:
            FondoDeAcceso()
        case .fuera(let aviso):
            Acceso(app: app, aviso: aviso)
        case .bloqueada(let nombre):
            Desbloqueo(control: control, nombre: nombre, automatico: control.faceIdAutomatico)
        case .elegirEmpresa(let usuario):
            ElegirEmpresa(control: control, usuario: usuario)
        case .dentro(let usuario):
            Inicio(app: app, usuario: usuario)
                .id(usuario.empresa?.id ?? usuario.id)
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
