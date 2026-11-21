import SwiftUI
import UIKit

// Tokens de color de la especificación §4. Cada uno tiene versión clara y
// oscura; las pantallas de acceso fuerzan el oscuro.

extension UIColor {
    convenience init(hex: UInt32, alfa: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: alfa)
    }
}

extension Color {
    init(hex: UInt32, alfa: Double = 1) { self.init(uiColor: UIColor(hex: hex, alfa: CGFloat(alfa))) }

    /// Color que cambia con el modo claro / oscuro del sistema.
    init(claro: UInt32, oscuro: UInt32, alfaClaro: CGFloat = 1, alfaOscuro: CGFloat = 1) {
        self.init(uiColor: UIColor { t in
            t.userInterfaceStyle == .dark ? UIColor(hex: oscuro, alfa: alfaOscuro) : UIColor(hex: claro, alfa: alfaClaro)
        })
    }
}

enum Finso {
    static let menta = Color(hex: 0x1DBB9D)
    static let tinta = Color(hex: 0x102D31)
    static let palomita = Color(hex: 0x49C095)

    static let fondo = Color(claro: 0xF2F7F2, oscuro: 0x04100F)
    static let tarjeta = Color(claro: 0xFFFFFF, oscuro: 0x0C1C1D)
    static let elevado = Color(claro: 0xDCF1E9, oscuro: 0x142628)
    static let texto = Color(claro: 0x102D31, oscuro: 0xF2F7F2)
    static let secundario = Color(claro: 0x4A6668, oscuro: 0xA9C4BF)
    static let acento = Color(claro: 0x0A6F5D, oscuro: 0x1DBB9D)
    static let borde = Color(claro: 0x63827D, oscuro: 0x5E8A86)
    static let sombra = Color(claro: 0x102D31, oscuro: 0x061417, alfaClaro: 0.08, alfaOscuro: 0.40)

    /// Botón principal: menta con letra tinta, en claro y en oscuro.
    static let accion = menta
    static let sobreAccion = tinta

    static let saldoArriba = Color(claro: 0x17494B, oscuro: 0x162B2D)
    static let saldoAbajo = Color(claro: 0x102D31, oscuro: 0x0A1517)
    static let sobreSaldo = Color(hex: 0xF2F7F2)
    static let sobreSaldoSecundario = Color(hex: 0xA9C4BF)

    static let positivo = Color(claro: 0x0A6F5D, oscuro: 0x1DBB9D)
    static let positivoFondo = Color(claro: 0xDCF1E9, oscuro: 0x143A36)
    static let aviso = Color(claro: 0x8A5A00, oscuro: 0xF5B544)
    static let avisoFondo = Color(claro: 0xFBF0D9, oscuro: 0x33291B)
    static let peligro = Color(claro: 0xB3261E, oscuro: 0xFFB4AB)
    static let peligroFondo = Color(claro: 0xFBE6E4, oscuro: 0x3A1A18)

    // Fondo de acceso (§3.1).
    static let accesoBase = Color(hex: 0x061518)
    static let accesoArriba = Color(hex: 0x123035)
    static let accesoMedio = Color(hex: 0x0C2327)
    static let accesoHalo = Color(hex: 0x2A6B73)
    static let accesoVineta = Color(hex: 0x03090A)
}
