package mx.finsocial.empresarial.nucleo

import java.security.MessageDigest
import java.util.Base64

/**
 * Textos que firma la llave del teléfono (huella). El servidor arma el mismo
 * texto y verifica la firma con la llave pública registrada. Formato fijo del
 * contrato (§1.5, §1.6, §4.2, §4.4) y de la especificación compartida §2:
 * no cambiar sin cambiarlo allá.
 */
object TextoFirmado {
    /** Prueba que la llave vive en este teléfono (§1.5). */
    fun registrar(reto: String, huellaDeLlave: String) = "finso-movil:v1:registrar:$reto:$huellaDeLlave"

    fun entrar(dispositivo: String, reto: String) = "finso-movil:v1:entrar:$dispositivo:$reto"

    /** v1.1: sin referencia numérica (la asigna el servidor). */
    fun spei(dispositivo: String, reto: String, destino: String, importeCentavos: Long, concepto: String) =
        "finso-movil:v1:spei:$dispositivo:$reto:$destino:$importeCentavos:$concepto"

    fun liberar(dispositivo: String, reto: String, orden: String, importeCentavos: Long) =
        "finso-movil:v1:liberar:$dispositivo:$reto:$orden:$importeCentavos"

    fun rechazar(dispositivo: String, reto: String, orden: String) =
        "finso-movil:v1:rechazar:$dispositivo:$reto:$orden"

    /** SHA-256 en hex minúsculas de los bytes DER de la llave pública (base64). */
    fun huellaDeLlave(llavePublicaBase64: String): String = sha256Hex(Base64.getDecoder().decode(llavePublicaBase64))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
