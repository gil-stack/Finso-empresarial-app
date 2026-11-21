# Finso Empresarial (app móvil)

La banca de la empresa en el teléfono, para iPhone y Android. Hecha en **Flutter**, con el diseño de la app finsocial-v2 (marca Finso: verde petróleo, menta y las letras Gully y Wanted Sans).

El servidor es **Finso Empresas** (`gil-stack/plataforma-administrativa`, https://empresas.finsocial.app). La app solo muestra y pide: toda la lógica de dinero, límites y permisos vive en el servidor.

## Qué hace la versión 1

- Entrar la primera vez con correo, contraseña y código por SMS (Vonage); después con Face ID o huella.
- Ver saldo, CLABE y movimientos.
- Mandar SPEI **solo a cuentas destino registradas** (las nuevas esperan 30 minutos), y liberar los de otras personas.
- Sin tarjetas por ahora (Pomelo llega en otra fase).

## Cómo correrla en tu computadora

```bash
flutter pub get
flutter run                     # con datos simulados (no necesita servidor)
flutter run --dart-define=FINSO_API=real   # contra Finso Empresas
```

Con datos simulados, el código de SMS es **123456**. Correos para probar casos: `ana@…` (puede autorizarse sola, «operación del dueño»), cualquier otro (sus SPEI quedan por liberar), `nuevo@…` (pide capturar el celular), `bloqueado@…` (acceso pausado).

## Antes de subir cambios

```bash
dart format lib test
flutter analyze
flutter test
```

El CI de GitHub corre lo mismo en cada PR (unos 2 minutos). Si cambias algo de `android/`, `config/` o las dependencias, además compila un APK de prueba.

## Dónde está cada cosa

| Carpeta | Qué hay |
|---|---|
| `config/Identidad.xcconfig` | **El identificador de la app en las tiendas y su nombre.** Único lugar donde se cambian (ver `docs/identidad.md`). |
| `lib/config/entorno.dart` | Dirección del servidor y si se usan datos simulados. |
| `lib/api/` | Cómo habla la app con el servidor: modelos, la API real (`api_http.dart`) y la simulada (`api_simulada.dart`). |
| `lib/ui/` | Piezas de diseño: tema claro/oscuro, fondo de acceso, botones, avisos, animaciones. |
| `lib/pantallas/` | Las pantallas. |
| `lib/util/` | Dinero (siempre en centavos enteros) y fechas (hora de México). |

Contrato con el servidor: `/mnt/project-files/app-empresarial/contrato-api-movil.md` (archivos del proyecto). Si cambia, se cambia allá primero.

## Reglas de diseño

- Acción principal: botón **menta `#1DBB9D` con letra tinta `#102D31`**, en claro y en oscuro (`BotonPrincipal`).
- Todo en claro y oscuro; el acceso siempre va sobre el fondo oscuro de la marca.
- Animaciones con los tiempos de `Animacion` (`lib/ui/tema.dart`) y respetando «Reducir movimiento».
- Textos en español de México, cortos y claros.
