package mx.finsocial.empresarial.seguridad

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.nucleo.api.LlaveDelTelefono
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * La llave del teléfono en el Android Keystore (contrato §1.5,
 * especificación §2): EC P-256, en StrongBox si el teléfono lo tiene, que
 * **solo firma después de una huella fuerte** (clase 3). Si cambian las
 * huellas del teléfono la llave queda inválida y hay que volver a entrar con
 * el correo.
 */
class LlaveKeystore(
    private val contexto: Context,
    /** La pantalla al frente: BiometricPrompt necesita una. */
    private val actividad: () -> FragmentActivity?,
) : LlaveDelTelefono {
    companion object {
        const val ALIAS = "finso.empresarial.dispositivo"
    }

    private fun almacen() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun existe(): Boolean = runCatching { almacen().containsAlias(ALIAS) }.getOrDefault(false)

    override fun crear(): String {
        borrar()
        val par = try {
            generar(strongBox = Build.VERSION.SDK_INT >= 28)
        } catch (_: StrongBoxUnavailableException) {
            generar(strongBox = false)
        } catch (e: Exception) {
            if (e.cause is StrongBoxUnavailableException || e.javaClass.simpleName.contains("StrongBox")) generar(strongBox = false) else throw e
        }
        return Base64.encodeToString(par.public.encoded, Base64.NO_WRAP)
    }

    private fun generar(strongBox: Boolean) = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
                if (strongBox) setIsStrongBoxBacked(true)
            }
            .build()
        initialize(spec)
    }.generateKeyPair()

    /** ¿Se puede usar la huella ahora? Si no, la falla que corresponde. */
    private fun revisarBiometria() {
        when (BiometricManager.from(contexto).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            -> throw FallaBiometria(FallaBiometria.Tipo.SIN_BIOMETRIA)
            else -> Unit
        }
    }

    override suspend fun firmar(texto: String, motivo: String): String {
        revisarBiometria()
        val firma = withContext(Dispatchers.Default) {
            val llave = almacen().getKey(ALIAS, null) as? PrivateKey ?: throw FallaBiometria(FallaBiometria.Tipo.NO_EXISTE)
            try {
                Signature.getInstance("SHA256withECDSA").apply { initSign(llave) }
            } catch (_: KeyPermanentlyInvalidatedException) {
                throw FallaBiometria(FallaBiometria.Tipo.INVALIDADA)
            }
        }
        val autorizada = pedirHuella(firma, motivo)
        return withContext(Dispatchers.Default) {
            autorizada.update(texto.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(autorizada.sign(), Base64.NO_WRAP)
        }
    }

    private suspend fun pedirHuella(firma: Signature, motivo: String): Signature = withContext(Dispatchers.Main) {
        val act = actividad() ?: throw FallaBiometria(FallaBiometria.Tipo.OTRA)
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                act,
                ContextCompat.getMainExecutor(act),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        val s = r.cryptoObject?.signature
                        if (s == null) cont.resumeWithException(FallaBiometria(FallaBiometria.Tipo.OTRA)) else cont.resume(s)
                    }

                    override fun onAuthenticationError(codigo: Int, texto: CharSequence) {
                        val tipo = when (codigo) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED,
                            -> FallaBiometria.Tipo.CANCELO
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                            -> FallaBiometria.Tipo.BLOQUEADA
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            -> FallaBiometria.Tipo.SIN_BIOMETRIA
                            else -> FallaBiometria.Tipo.OTRA
                        }
                        if (cont.isActive) cont.resumeWithException(FallaBiometria(tipo))
                    }
                    // onAuthenticationFailed: huella no reconocida; el sistema deja reintentar.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(motivo)
                .setSubtitle("Finso Empresarial")
                .setNegativeButtonText("Cancelar")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(firma))
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    override fun borrar() {
        runCatching { almacen().deleteEntry(ALIAS) }
    }
}
