# Finso-empresarial-app

App móvil **Finso Empresarial** para las empresas clientes de Finso: ver la cuenta y sus movimientos, pedir SPEI a destinos registrados y liberar los que pidió otra persona, todo firmado con Face ID o huella.

Son dos apps nativas en este repo, con el mismo diseño y las mismas reglas:

| | Carpeta | Lenguaje | Pruebas |
|---|---|---|---|
| iPhone | [`ios/`](ios/) | Swift + SwiftUI (iOS 17+, Liquid Glass en iOS 26) | `.github/workflows/ios-pruebas.yml` |
| Android | `android/` | Kotlin + Jetpack Compose | `.github/workflows/android-pruebas.yml` |

El servidor es Finso Empresas (`https://empresas.finsocial.app/movil/v1`). La especificación compartida y el contrato de la API viven en los archivos del proyecto (`app-empresarial/`).

## iPhone

```
ios/
  FinsoNucleo/          paquete Swift: modelos, API real y simulada, firmas, reglas y formatos
  FinsoEmpresarial/     la app (SwiftUI)
  FinsoEmpresarialTests/
  Config/               Identidad.xcconfig: identificador, nombre, versión y URL de la API
  project.yml           proyecto de Xcode (XcodeGen); el .xcodeproj no se guarda
```

- **Todo se arma en la nube.** No hace falta Xcode en ninguna computadora:
  1. el núcleo se prueba en Linux (`swift test`);
  2. si pasa, una Mac de GitHub genera el proyecto con XcodeGen y corre las pruebas en el simulador del iPhone.
- **Datos de prueba:** en Debug la app usa la API simulada. Para entrar, cualquier correo y contraseña; el código es `123456`. `ana@…` es administradora y liberadora, cualquier otro correo es solicitante y `varias@…` tiene dos empresas.
- **Seguridad:** la llave del teléfono vive en el Secure Enclave y solo firma con Face ID. La sesión va en el llavero, solo de este teléfono. Las capturas de pantalla salen en negro y una tapa cubre la app en el selector de apps.

Para trabajar en una Mac (opcional):

```sh
cd ios/FinsoNucleo && swift test        # núcleo
cd ios && brew install xcodegen && xcodegen generate && open FinsoEmpresarial.xcodeproj
```
