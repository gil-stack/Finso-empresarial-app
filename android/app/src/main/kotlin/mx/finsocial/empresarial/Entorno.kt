package mx.finsocial.empresarial

/**
 * Configuración que cambia por ambiente. Se elige al compilar (ver
 * app/build.gradle.kts): debug usa datos simulados y release la API real.
 *
 *   ./gradlew assembleDebug                         → datos simulados
 *   ./gradlew assembleDebug -Pfinso.api=real        → Finso Empresas real
 *   ./gradlew assembleRelease -Pfinso.apiUrl=https://…
 */
object Entorno {
    val usarSimulada: Boolean get() = BuildConfig.FINSO_API != "real"

    /** Base de la API móvil (contrato §0). Cambiar de dominio es cambiar una propiedad. */
    val apiUrl: String get() = BuildConfig.FINSO_API_URL

    /** Lo que va en `X-Finso-App`: `empresarial/1.0.0+12`. */
    val versionApp: String get() = "empresarial/${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

    /** Pie de Ajustes: «Finso Empresarial 1.0.0+12 · datos simulados». */
    val versionVisible: String
        get() = "Finso Empresarial ${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}" +
            if (usarSimulada) " · datos simulados" else ""
}
