package mx.finsocial.empresarial.nucleo.api

import java.time.Instant

/**
 * Dónde se guardan los secretos del teléfono. En Android es un almacén
 * cifrado con una llave del Keystore (sin respaldo en la nube); en pruebas,
 * memoria.
 */
interface Llavero {
    fun leer(clave: String): String?
    fun escribir(clave: String, valor: String?)
    fun borrarTodo()
}

class LlaveroEnMemoria : Llavero {
    private val datos = mutableMapOf<String, String>()
    override fun leer(clave: String) = datos[clave]
    override fun escribir(clave: String, valor: String?) {
        if (valor == null) datos.remove(clave) else datos[clave] = valor
    }
    override fun borrarTodo() = datos.clear()
}

/** Lo que la app recuerda entre aperturas, sobre un [Llavero]. */
class Guardado(private val llavero: Llavero) {
    /** `dev_…`: el teléfono está registrado. */
    var dispositivo: String?
        get() = llavero.leer("dispositivo")
        set(v) = llavero.escribir("dispositivo", v)

    var sesion: Sesion?
        get() = llavero.leer("sesion")?.let { runCatching { JsonFinso.decodeFromString(Sesion.serializer(), it) }.getOrNull() }
        set(v) = llavero.escribir("sesion", v?.let { JsonFinso.encodeToString(Sesion.serializer(), it) })

    /** `emp_…` elegida si la persona trabaja con varias (va en `X-Finso-Empresa`). */
    var empresa: String?
        get() = llavero.leer("empresa")
        set(v) = llavero.escribir("empresa", v)

    /** «Ana», para saludar en el desbloqueo sin pedir nada al servidor. */
    var nombreCorto: String?
        get() = llavero.leer("nombreCorto")
        set(v) = llavero.escribir("nombreCorto", v)

    /** Cuándo fue la última huella: la sesión dura 1 h desde ahí (§1.7). */
    var ultimaBiometria: Instant?
        get() = llavero.leer("ultimaBiometria")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        set(v) = llavero.escribir("ultimaBiometria", v?.toString())

    /** Aviso para la bienvenida después de olvidar el teléfono («Este teléfono se dio de baja…»). */
    var avisoDeSalida: String?
        get() = llavero.leer("avisoDeSalida")
        set(v) = llavero.escribir("avisoDeSalida", v)

    /** Sale: la próxima vez se entra con la huella. */
    fun olvidarSesion() {
        sesion = null
    }

    /** Olvida el teléfono: para volver hay que entrar con correo y códigos. */
    fun olvidarTodo(aviso: String? = null) {
        llavero.borrarTodo()
        if (aviso != null) avisoDeSalida = aviso
    }
}
