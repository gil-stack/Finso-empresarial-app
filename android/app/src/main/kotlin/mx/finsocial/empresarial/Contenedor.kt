package mx.finsocial.empresarial

import android.content.Context
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mx.finsocial.empresarial.estado.ControlSesion
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDeSoftware
import mx.finsocial.empresarial.nucleo.api.LlaveDelTelefono
import mx.finsocial.empresarial.nucleo.api.Llavero
import mx.finsocial.empresarial.nucleo.api.ServidorSimulado
import mx.finsocial.empresarial.nucleo.avisos.Enlace
import mx.finsocial.empresarial.nucleo.spei.SpeiSinConfirmar
import mx.finsocial.empresarial.seguridad.LlaveKeystore
import mx.finsocial.empresarial.seguridad.LlaveroAndroid
import mx.finsocial.empresarial.seguridad.Telefono
import mx.finsocial.empresarial.sistema.Sistema
import mx.finsocial.empresarial.sistema.SistemaAndroid
import mx.finsocial.empresarial.ui.tema.Apariencia
import java.lang.ref.WeakReference
import java.time.Instant

/**
 * Las piezas compartidas de la app, armadas una vez. En las pruebas se arma
 * otro con el servidor simulado, la llave en software y un llavero en memoria.
 */
class Contenedor(
    val guardado: Guardado,
    val api: ApiFinso,
    val llave: LlaveDelTelefono,
    val telefono: DatosDelTelefono,
    val simulada: Boolean,
    private val preferencias: Preferencias,
    val reloj: () -> Instant = Instant::now,
    val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    /** Notificación en vivo, widget y accesos directos. En las pruebas de pantallas, [Sistema.Nada]. */
    val sistema: Sistema = Sistema.Nada,
) {
    val sesion = ControlSesion(api, guardado, llave, reloj, alcance)

    private val _apariencia = MutableStateFlow(preferencias.apariencia)
    val apariencia: StateFlow<Apariencia> = _apariencia

    fun cambiarApariencia(a: Apariencia) {
        preferencias.apariencia = a
        _apariencia.value = a
    }

    private val _enlace = MutableStateFlow<Enlace?>(null)

    /** A dónde pidió ir una notificación, el widget o un acceso directo. Se consume al navegar. */
    val enlace: StateFlow<Enlace?> = _enlace

    fun abrirEnlace(texto: String?) {
        Enlace.leer(texto)?.let { _enlace.value = it }
    }

    fun enlaceAtendido() {
        _enlace.value = null
    }

    /** El SPEI que se mandó sin saber si llegó (ver [SpeiSinConfirmar]). Se pierde al cerrar la app. */
    var speiSinConfirmar: SpeiSinConfirmar? = null

    private val _ocultarImportes = MutableStateFlow(preferencias.ocultarImportes)

    /** El ojo de la tarjeta de saldo: oculta todos los importes (`$ •••••`). */
    val ocultarImportes: StateFlow<Boolean> = _ocultarImportes

    fun cambiarOcultarImportes(ocultar: Boolean) {
        preferencias.ocultarImportes = ocultar
        _ocultarImportes.value = ocultar
    }

    /** Pedir el permiso de notificaciones solo una vez y solo si hay sistema real. */
    fun debePedirAvisos(): Boolean = sistema !== Sistema.Nada && !preferencias.pidioAvisos

    fun yaPidioAvisos() {
        preferencias.pidioAvisos = true
    }

    /** Preferencias que no son secretas (la apariencia y el ojo). */
    interface Preferencias {
        var apariencia: Apariencia
        var ocultarImportes: Boolean
        /** Ya se pidió el permiso de notificaciones (se pide una sola vez). */
        var pidioAvisos: Boolean
    }

    class PreferenciasEnMemoria(
        override var apariencia: Apariencia = Apariencia.SISTEMA,
        override var ocultarImportes: Boolean = false,
        override var pidioAvisos: Boolean = false,
    ) : Preferencias

    private class PreferenciasAndroid(contexto: Context) : Preferencias {
        private val p = contexto.getSharedPreferences("finso.preferencias", Context.MODE_PRIVATE)
        override var apariencia: Apariencia
            get() = runCatching { Apariencia.valueOf(p.getString("apariencia", null) ?: "SISTEMA") }.getOrDefault(Apariencia.SISTEMA)
            set(v) = p.edit().putString("apariencia", v.name).apply()
        override var ocultarImportes: Boolean
            get() = p.getBoolean("ocultarImportes", false)
            set(v) = p.edit().putBoolean("ocultarImportes", v).apply()
        override var pidioAvisos: Boolean
            get() = p.getBoolean("pidioAvisos", false)
            set(v) = p.edit().putBoolean("pidioAvisos", v).apply()
    }

    companion object {
        /** La pantalla al frente, para la huella (BiometricPrompt). */
        var actividad: WeakReference<FragmentActivity?> = WeakReference(null)

        fun real(contexto: Context): Contenedor {
            val app = contexto.applicationContext
            val llavero: Llavero = LlaveroAndroid(app)
            val guardado = Guardado(llavero)
            val simulada = Entorno.usarSimulada
            val cliente = ApiFinso.clienteBase().newBuilder().apply {
                if (simulada) addInterceptor(ServidorSimulado())
            }.build()
            val api = ApiFinso(Entorno.apiUrl, Entorno.versionApp, guardado, cliente)
            // En el emulador del CI no hay huella: la versión simulada puede usar
            // una llave en software (-Pfinso.huellaSimulada=true). Nunca con la API real.
            val llave: LlaveDelTelefono = if (simulada && BuildConfig.HUELLA_SIMULADA) {
                LlaveDeSoftware()
            } else {
                LlaveKeystore(app) { actividad.get() }
            }
            return Contenedor(guardado, api, llave, Telefono.datos(app), simulada, PreferenciasAndroid(app), sistema = SistemaAndroid(app) { orden, token -> api.registrarAviso(orden, token) })
        }
    }
}
