package mx.finsocial.empresarial.nucleo.api

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * La llave del teléfono (contrato §1.5): EC P-256 que solo firma después de
 * la huella. En Android vive en el Keystore (StrongBox si hay); aquí está la
 * forma común para que la lógica y las pruebas no dependan de Android.
 */
interface LlaveDelTelefono {
    fun existe(): Boolean

    /** Crea (o reemplaza) la llave. Regresa la pública: SubjectPublicKeyInfo DER en base64. */
    fun crear(): String

    /**
     * Pide la huella con [motivo] («Mandar $1,500.00 a Papelera…») y firma
     * [texto] en UTF-8 con ECDSA-SHA256. Regresa la firma DER en base64.
     * Lanza [FallaBiometria] si se cancela o no se puede.
     */
    suspend fun firmar(texto: String, motivo: String): String

    fun borrar()
}

/** Lo que puede pasar con la huella (especificación §6, segunda tabla). */
class FallaBiometria(val tipo: Tipo) : Exception(tipo.name) {
    enum class Tipo { CANCELO, SIN_BIOMETRIA, BLOQUEADA, INVALIDADA, NO_EXISTE, OTRA }
}

/**
 * Llave en software, para pruebas y para la app con datos simulados en un
 * emulador sin huella. Nunca se usa con el servidor real.
 */
class LlaveDeSoftware(
    /** Qué contesta la «huella»: null = acepta; si no, la falla que simula. */
    var respuesta: FallaBiometria.Tipo? = null,
) : LlaveDelTelefono {
    private var par: KeyPair? = null

    /** Los textos que se firmaron, en orden (para revisar en pruebas). */
    val firmados = mutableListOf<String>()
    val motivos = mutableListOf<String>()

    override fun existe() = par != null

    override fun crear(): String {
        val g = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val p = g.generateKeyPair()
        par = p
        return Base64.getEncoder().encodeToString(p.public.encoded)
    }

    override suspend fun firmar(texto: String, motivo: String): String {
        respuesta?.let { throw FallaBiometria(it) }
        val p = par ?: throw FallaBiometria(FallaBiometria.Tipo.NO_EXISTE)
        motivos += motivo
        firmados += texto
        return Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(p.private)
                update(texto.toByteArray(Charsets.UTF_8))
                sign()
            },
        )
    }

    override fun borrar() {
        par = null
    }
}
