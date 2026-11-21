# Finso Empresarial · Android

App nativa en **Kotlin + Jetpack Compose** (Material 3). La de iPhone vive en `ios/`; las dos siguen la misma especificación (`especificacion-apps-nativas.md` en los archivos del proyecto) y el contrato de la API móvil de Finso Empresas (`/movil/v1`, v1.1).

## Cómo está armada

| Módulo | Qué tiene | Dónde se prueba |
|---|---|---|
| `nucleo/` | Kotlin puro, sin Android: cliente de la API `/movil/v1` (con renovación de sesión), servidor simulado, dinero en centavos, fechas en hora de México, concepto SPEI, textos que firma la llave del teléfono, nombres legibles, textos de error. | Cualquier computadora con Java: `./gradlew -Pfinso.soloNucleo=true :nucleo:test` |
| `app/` | La app: diseño Finso (tema, letras Gully y Wanted Sans, botón menta), pantallas, llave del teléfono y huella. | GitHub Actions (necesita el SDK de Android). |

## Identidad (un solo lugar)

`android/gradle.properties`:

- `finso.appId=mx.finsocial.empresarial`: si Play Console dice que está ocupado, se cambia solo aquí.
- `finso.appNombre`, `finso.version`.

## API simulada o real

- `assembleDebug` usa **datos simulados** (código `123456`; usuarios `ana@…`, `nuevo@…`, `varias@…`).
- `assembleRelease` usa **Finso Empresas real** (`https://empresas.finsocial.app/movil/v1`).
- Se puede forzar con `-Pfinso.api=real|simulada` y `-Pfinso.apiUrl=…`.

## En la nube

- **Cada PR que toca `android/`** corre «Android · pruebas»: lógica, pantallas con Robolectric (con capturas en claro y oscuro), lint y un APK de prueba. Las capturas y el APK quedan como artefactos de la corrida.
- **Recorrido en emulador** («Android · recorrido en emulador»): enciende un teléfono Android de verdad (emulador Pixel 6, Android 14) dentro de GitHub, instala la app y hace el primer ingreso completo con datos simulados (correo, código, celular, huella, desbloqueo). Guarda una captura por pantalla en el artefacto `recorrido-android`.
  - En el emulador no hay dedo: se arma con `-Pfinso.huellaSimulada=true`, que cambia la llave del teléfono por una de software. Nunca se usa en la versión de la tienda.
- **Entrega a prueba interna de Google Play**: llega en un PR posterior.

## Fuera de la app: SPEI en vivo, widget y accesos directos

Es el equivalente en Android de la Isla Dinámica del iPhone (especificación §10 a §12). Vive en `app/.../sistema/`; los textos y reglas están en `nucleo/avisos/` y tienen sus pruebas.

- **Notificación en vivo de cada SPEI** (`SpeiEnVivo`): sale al pedir, liberar o abrir una orden, y avanza de «Falta que la liberen» a «Enviando…» y luego a «Enviado» (o «Rechazado» / «Devuelto»).
  - En Android 16 es un *Live Update*: barra de avance en tres partes y un chip con el importe en la barra de estado.
  - En versiones anteriores es una notificación en curso con barra de avance.
  - No suena. En la pantalla bloqueada solo dice «SPEI en proceso».
  - Sigue máximo 3 órdenes. Al terminar se queda 15 min. Si pasan 30 min sin noticias, dice «Sin confirmar, abre la app».
  - Con la app abierta se actualiza cada 15 s. Con la app cerrada se va a actualizar con avisos del servidor (FCM); eso se enciende cuando exista el proyecto de Firebase, en el PR de entrega.
- **Widget**: «SPEI por liberar: N» con «Revisar», o «Mandar SPEI». Nunca muestra saldo.
- **Accesos directos** (mantener presionado el ícono): «Mandar SPEI» y «Por liberar», según los permisos. Al salir se quitan, junto con el widget y las notificaciones.
- **Ligas internas** `finso-empresarial://orden?ref=…`, `://mandar` y `://por-liberar`: solo dicen a dónde ir. La orden se vuelve a pedir al servidor con la sesión, y cualquier otra liga se ignora. No se pueden abrir desde una página web.
- El permiso de notificaciones se pide una sola vez, al ver el primer resultado de un SPEI. Si la persona dice que no, todo lo demás funciona igual.

## Seguridad

- Sin respaldo en la nube (`allowBackup=false` y `sin_respaldo.xml`).
- Capturas de pantalla en negro (`FLAG_SECURE`), salvo al armar capturas para la tienda con `-Pfinso.capturas=true`.
- Solo HTTPS con certificados del sistema (`res/xml/red.xml`).
