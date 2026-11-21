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
import mx.finsocial.empresarial.seguridad.LlaveKeystore
import mx.finsocial.empresarial.seguridad.LlaveroAndroid
import mx.finsocial.empresarial.seguridad.Telefono
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
) {
    val sesion = ControlSesion(api, guardado, llave, reloj, alcance)

    private val _apariencia = MutableStateFlow(preferencias.apariencia)
    val apariencia: StateFlow<Apariencia> = _apariencia

    fun cambiarApariencia(a: Apariencia) {
        preferencias.apariencia = a
        _apariencia.value = a
    }

    private val _ocultarImportes = MutableStateFlow(preferencias.ocultarImportes)

    /** El ojo de la tarjeta de saldo: oculta todos los importes (`$ •••••`). */
    val ocultarImportes: StateFlow<Boolean> = _ocultarImportes

    fun cambiarOcultarImportes(ocultar: Boolean) {
        preferencias.ocultarImportes = ocultar
        _ocultarImportes.value = ocultar
    }

    /** Preferencias que no son secretas (la apariencia y el ojo). */
    interface Preferencias {
        var apariencia: Apariencia
        var ocultarImportes: Boolean
    }

    class PreferenciasEnMemoria(
        override var apariencia: Apariencia = Apariencia.SISTEMA,
        override var ocultarImportes: Boolean = false,
    ) : Preferencias

    private class PreferenciasAndroid(contexto: Context) : Preferencias {
        private val p = contexto.getSharedPreferences("finso.preferencias", Context.MODE_PRIVATE)
        override var apariencia: Apariencia
            get() = runCatching { Apariencia.valueOf(p.getString("apariencia", null) ?: "SISTEMA") }.getOrDefault(Apariencia.SISTEMA)
            set(v) = p.edit().putString("apariencia", v.name).apply()
        override var ocultarImportes: Boolean
            get() = p.getBoolean("ocultarImportes", false)
            set(v) = p.edit().putBoolean("ocultarImportes", v).apply()
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
            return Contenedor(guardado, api, llave, Telefono.datos(app), simulada, PreferenciasAndroid(app))
        }
    }
}
