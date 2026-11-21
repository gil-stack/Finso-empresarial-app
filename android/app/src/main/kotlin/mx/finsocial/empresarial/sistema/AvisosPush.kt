package mx.finsocial.empresarial.sistema

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import mx.finsocial.empresarial.BuildConfig
import kotlin.coroutines.resume

/**
 * Avisos con la app cerrada (FCM, contrato §5.1). Firebase se arranca a mano
 * con los datos que pone el CI de entrega; sin ellos todo esto no hace nada y
 * la notificación en vivo solo se actualiza con la app abierta.
 */
object AvisosPush {
    val configurado: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROYECTO.isNotBlank() && BuildConfig.FIREBASE_REMITENTE.isNotBlank()

    /** Arranca Firebase una vez. Dice si quedó listo. */
    @Synchronized
    fun encender(contexto: Context): Boolean {
        if (!configurado) return false
        if (FirebaseApp.getApps(contexto).isNotEmpty()) return true
        return runCatching {
            FirebaseApp.initializeApp(
                contexto,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROYECTO)
                    .setGcmSenderId(BuildConfig.FIREBASE_REMITENTE)
                    .build(),
            )
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            true
        }.getOrDefault(false)
    }

    /** El token FCM del teléfono, o null si no hay Firebase o falla. */
    suspend fun token(contexto: Context): String? {
        if (!encender(contexto)) return null
        return suspendCancellableCoroutine { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                c.resume(if (t.isSuccessful) t.result else null)
            }
        }
    }
}
