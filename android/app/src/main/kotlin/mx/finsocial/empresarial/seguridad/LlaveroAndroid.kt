package mx.finsocial.empresarial.seguridad

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import mx.finsocial.empresarial.nucleo.api.Llavero
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Llavero del teléfono: cada valor se cifra con AES-256-GCM usando una llave
 * del Android Keystore que nunca sale del chip. El archivo queda fuera de los
 * respaldos (ver `sin_respaldo.xml`), así que nada viaja a la nube.
 */
class LlaveroAndroid(contexto: Context) : Llavero {
    private val prefs = contexto.getSharedPreferences("finso.llavero", Context.MODE_PRIVATE)
    private val alias = "finso.empresarial.llavero"

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    @Synchronized override fun leer(clave: String): String? {
        val guardado = prefs.getString(clave, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(guardado, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, bytes, 0, 12))
            String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized override fun escribir(clave: String, valor: String?) {
        if (valor == null) {
            prefs.edit().remove(clave).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = c.iv + c.doFinal(valor.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(clave, Base64.encodeToString(cifrado, Base64.NO_WRAP)).apply()
    }

    @Synchronized override fun borrarTodo() {
        prefs.edit().clear().apply()
    }
}
