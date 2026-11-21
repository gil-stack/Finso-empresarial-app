package mx.finsocial.empresarial.nucleo.api

import mx.finsocial.empresarial.nucleo.Textos

/**
 * Lo que puede salir mal al hablar con Finso Empresas. [codigo] es el del
 * contrato (`CREDENCIALES_INVALIDAS`, …); [texto] es lo que se le muestra a la
 * persona: el `mensaje` del servidor si viene, si no el de la especificación §6.
 */
open class FallaApi(
    val codigo: String,
    val estadoHttp: Int,
    mensajeDelServidor: String? = null,
    val intentosRestantes: Int? = null,
    val reintentarEn: String? = null,
    val campos: List<String> = emptyList(),
) : Exception(codigo) {
    val texto: String = mensajeDelServidor?.takeIf { it.isNotBlank() } ?: Textos.deError(codigo, intentosRestantes)

    /** Errores que obligan a volver a empezar el primer ingreso (§3.1, paso 3). */
    val reiniciaElIngreso: Boolean
        get() = codigo in setOf("RETO_VENCIDO", "DEMASIADOS_INTENTOS", "PASO_FUERA_DE_ORDEN", "RETO_INCOMPLETO")

    /** La sesión terminó: hay que pedir la huella otra vez. */
    val pideBiometria: Boolean get() = codigo == "SESION_VENCIDA" || codigo == "SESION_REVOCADA"

    /** El teléfono se dio de baja: se olvida todo y se vuelve al correo. */
    val olvidaElTelefono: Boolean get() = codigo == "DISPOSITIVO_REVOCADO"

    override val message: String get() = "$codigo ($estadoHttp): $texto"
}

/** No hubo respuesta: sin internet, el servidor no contestó o se cortó. */
class SinRed(causa: Throwable? = null) : FallaApi("SIN_RED", 0, Textos.SIN_RED) {
    init { if (causa != null) initCause(causa) }
}
