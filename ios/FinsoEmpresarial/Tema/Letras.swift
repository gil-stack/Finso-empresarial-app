import SwiftUI

// Tipografía de la especificación §4: Gully para títulos, botones e importes;
// Wanted Sans Std para el texto. Todas crecen con el tamaño de letra del
// sistema (Dynamic Type).

enum Letra {
    enum Peso: String {
        case medium = "Medium", semibold = "SemiBold", bold = "Bold", regular = "Regular"
    }

    static func gully(_ tamano: CGFloat, _ peso: Peso = .semibold, relativoA estilo: Font.TextStyle = .body) -> Font {
        .custom("Gully-\(peso == .regular ? "Medium" : peso.rawValue)", size: tamano, relativeTo: estilo)
    }

    static func wanted(_ tamano: CGFloat, _ peso: Peso = .regular, relativoA estilo: Font.TextStyle = .body) -> Font {
        .custom("WantedSansStd-\(peso.rawValue)", size: tamano, relativeTo: estilo)
    }

    static let tituloGrande = gully(30, .bold, relativoA: .largeTitle)
    static let tituloMedio = gully(25, .bold, relativoA: .title)
    static let titulo = gully(17, .semibold, relativoA: .headline)
    static let subtitulo = gully(15, .semibold, relativoA: .subheadline)
    static let boton = gully(16, .semibold, relativoA: .headline)
    static let cuerpoGrande = wanted(15, relativoA: .body)
    static let cuerpo = wanted(14, relativoA: .callout)
    static let chica = wanted(13, relativoA: .footnote)
    static let etiqueta = wanted(12, .semibold, relativoA: .caption)
}

extension View {
    /// Importes: Gully Bold con cifras tabulares, hasta 1.3× con letra grande.
    func importe(_ tamano: CGFloat = 40) -> some View {
        font(Letra.gully(tamano, .bold, relativoA: .largeTitle))
            .monospacedDigit()
            .tracking(-1.2)
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)
            .minimumScaleFactor(0.6)
            .lineLimit(1)
    }
}
