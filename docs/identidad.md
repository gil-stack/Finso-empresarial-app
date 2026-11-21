# Identidad de la app en las tiendas

El identificador (Apple lo llama **Bundle ID**, Google **applicationId**) y el nombre visible viven en **un solo archivo**: `config/Identidad.xcconfig`.

```
APP_ID = mx.finsocial.empresarial
APP_NOMBRE = Finso Empresarial
```

- iOS lo lee porque el proyecto de Xcode usa ese archivo como configuración base (`ios/Runner.xcodeproj`), y `Info.plist` usa `$(APP_NOMBRE)`.
- Android lo lee en `android/app/build.gradle.kts` (`applicationId` y la etiqueta del ícono).
- El CI compila un APK y revisa que su identificador sea el de este archivo.

## Por qué `mx.finsocial.empresarial`

- La app finsocial-v2 usa `mx.financiera.fin_social` (Android), `mx.financiera.finsocial` y `mx.finso.demo` (iPhone). Este no choca con ninguno.
- Sigue el dominio `finsocial.app` / `finsocial.mx` al revés, como piden Apple y Google, y dice qué es la app.

**Importante:** una vez publicada, el identificador ya no se puede cambiar. Antes de registrarlo, confirma en **App Store Connect** (Identifiers) y en **Google Play Console** que esté libre en las cuentas donde va a salir.

## Cómo cambiarlo

1. Edita `APP_ID` (y si quieres `APP_NOMBRE`) en `config/Identidad.xcconfig`.
2. Listo: no hay otro lugar. (`namespace` en `android/app/build.gradle.kts` es solo el paquete interno del código Kotlin y no se publica.)
