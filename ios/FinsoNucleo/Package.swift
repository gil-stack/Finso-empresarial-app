// swift-tools-version:5.10
// Núcleo de Finso Empresarial: modelos, API (real y simulada), firmas, reglas
// y formatos. Solo usa Foundation, así que sus pruebas corren también en Linux
// (barato) y la app de iPhone lo usa tal cual.
import PackageDescription

let package = Package(
    name: "FinsoNucleo",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "FinsoNucleo", targets: ["FinsoNucleo"]),
    ],
    targets: [
        .target(name: "FinsoNucleo"),
        .testTarget(name: "FinsoNucleoTests", dependencies: ["FinsoNucleo"]),
    ]
)
