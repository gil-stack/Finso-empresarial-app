package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class TextosTest {
    @Test fun `codigo incorrecto dice cuantos intentos quedan`() {
        assertEquals("El código no es correcto. Te quedan 4 intentos.", Textos.deError("CODIGO_INCORRECTO", 4))
        assertEquals("El código no es correcto. Te quedan 1 intento.", Textos.deError("CODIGO_INCORRECTO", 1))
    }

    @Test fun `un codigo desconocido usa el texto generico`() {
        assertEquals(Textos.OTRO, Textos.deError("ALGO_NUEVO"))
        assertEquals("La cuenta está bloqueada y no puede mandar dinero.", Textos.deError("CUENTA_BLOQUEADA"))
    }
}
