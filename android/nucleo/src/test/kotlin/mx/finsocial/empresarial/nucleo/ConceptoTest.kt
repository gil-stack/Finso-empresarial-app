package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caso 5 de la especificación compartida. */
class ConceptoTest {
    @Test fun `acepta de 1 a 35 caracteres`() {
        assertTrue(Concepto.esValido("A"))
        assertTrue(Concepto.esValido("Pago factura 118"))
        assertTrue(Concepto.esValido("Fletes, semana 47 & cajas - lote.1"))
        assertTrue(Concepto.esValido("x".repeat(35)))
        assertFalse(Concepto.esValido("x".repeat(36)))
    }

    @Test fun `rechaza espacios mal puestos, acentos y vacio`() {
        assertFalse(Concepto.esValido(""))
        assertFalse(Concepto.esValido(" Pago"))
        assertFalse(Concepto.esValido("Pago "))
        assertFalse(Concepto.esValido("Pago  doble"))
        assertFalse(Concepto.esValido("Pago diseño"))
        assertFalse(Concepto.esValido("Café"))
        assertFalse(Concepto.esValido("Pago #3"))
    }

    @Test fun `el formateador quita acentos y enies y colapsa espacios`() {
        assertEquals("Pago diseno ano", Concepto.limpiar("Pago diseño año"))
        assertEquals("Cafe y mas", Concepto.limpiar("  Café   y   más"))
        assertEquals("Pago 3", Concepto.limpiar("Pago #3"))
        assertEquals(35, Concepto.limpiar("y".repeat(50)).length)
        assertEquals("Pago ", Concepto.limpiar("Pago "))
        assertEquals("Pago", Concepto.paraMandar("Pago "))
    }
}
