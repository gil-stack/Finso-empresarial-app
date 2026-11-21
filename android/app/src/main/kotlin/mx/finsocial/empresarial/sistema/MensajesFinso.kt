package mx.finsocial.empresarial.sistema

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.FinsoApp
import mx.finsocial.empresarial.nucleo.api.FallaApi

/**
 * Recibe los mensajes de datos de FCM (contrato §5.1): solo traen estado,
 * id y primer nombre de quien liberó. Nunca importes ni nombres del destino.
 */
class MensajesFinso : FirebaseMessagingService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        SpeiEnVivo(applicationContext).alMensaje(message.data)
    }

    /** Token nuevo: se vuelve a registrar para las órdenes que se siguen. */
    override fun onNewToken(token: String) {
        val enCurso = SpeiEnVivo(applicationContext).enCurso()
        if (enCurso.isEmpty()) return
        val api = (application as FinsoApp).contenedor.api
        alcance.launch {
            enCurso.forEach { ref ->
                try { api.registrarAviso(ref, token) } catch (_: FallaApi) { /* sin sesión o sin red: a la siguiente */ }
            }
        }
    }
}
