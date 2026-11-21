package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

/** Caso 4: deben dar exactamente los textos de la especificación §2. */
class TextoFirmadoTest {
    @Test fun `registrar lleva la huella SHA-256 de la llave`() {
        val abc = Base64.getEncoder().encodeToString("abc".toByteArray())
        val huella = TextoFirmado.huellaDeLlave(abc)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", huella)
        assertEquals("finso-movil:v1:registrar:rto_X:$huella", TextoFirmado.registrar("rto_X", huella))
    }

    @Test fun `entrar`() {
        assertEquals("finso-movil:v1:entrar:dev_1:R", TextoFirmado.entrar("dev_1", "R"))
    }

    @Test fun `spei sin referencia numerica`() {
        val t = TextoFirmado.spei("dev_1", "R", "dst_9", 150000, "Pago factura 118")
        assertEquals("finso-movil:v1:spei:dev_1:R:dst_9:150000:Pago factura 118", t)
        assertFalse(t.contains("118:"))
    }

    @Test fun `liberar y rechazar`() {
        assertEquals("finso-movil:v1:liberar:dev_1:R:spo_2:1845000", TextoFirmado.liberar("dev_1", "R", "spo_2", 1845000))
        assertEquals("finso-movil:v1:rechazar:dev_1:R:spo_2", TextoFirmado.rechazar("dev_1", "R", "spo_2"))
    }
}
