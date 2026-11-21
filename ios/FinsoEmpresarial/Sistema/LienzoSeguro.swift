import SwiftUI
import UIKit

/// Las capturas y grabaciones de pantalla salen en negro (Gil, 13/10/2026).
/// La app se dibuja dentro del lienzo de un campo de contraseña, que el
/// sistema deja fuera de capturas, como `ScreenShield` en finsocial-v2.
struct LienzoSeguro<Contenido: View>: UIViewControllerRepresentable {
    var activo: Bool
    var contenido: Contenido

    func makeUIViewController(context: Context) -> ControladorSeguro<Contenido> {
        ControladorSeguro(contenido: contenido, activo: activo)
    }

    func updateUIViewController(_ c: ControladorSeguro<Contenido>, context: Context) {
        c.anfitrion.rootView = contenido
        c.activo = activo
    }
}

final class ControladorSeguro<Contenido: View>: UIViewController {
    let anfitrion: UIHostingController<Contenido>
    private var lienzo: UIView?
    var activo: Bool {
        didSet { if oldValue != activo { colocar() } }
    }

    init(contenido: Contenido, activo: Bool) {
        anfitrion = UIHostingController(rootView: contenido)
        self.activo = activo
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        anfitrion.view.backgroundColor = .clear
        addChild(anfitrion)
        colocar()
        anfitrion.didMove(toParent: self)
    }

    private func llenar(_ v: UIView, en padre: UIView) {
        v.translatesAutoresizingMaskIntoConstraints = true
        v.frame = padre.bounds
        v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        padre.addSubview(v)
    }

    private func colocar() {
        guard isViewLoaded else { return }
        if activo, let l = lienzo ?? Self.hacerLienzo() {
            lienzo = l
            llenar(l, en: view)
            llenar(anfitrion.view, en: l)
        } else {
            llenar(anfitrion.view, en: view)
            lienzo?.removeFromSuperview()
        }
    }

    /// El lienzo seguro de un campo de contraseña, vacío y tocable.
    private static func hacerLienzo() -> UIView? {
        let campo = UITextField()
        campo.isSecureTextEntry = true
        guard let lienzo = campo.subviews.first else { return nil }
        lienzo.subviews.forEach { $0.removeFromSuperview() }
        lienzo.isUserInteractionEnabled = true
        lienzo.backgroundColor = .clear
        return lienzo
    }
}
