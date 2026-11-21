package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Casos 1 y 2 de la especificación compartida. */
class DineroTest {
    @Test fun `pesos formatea centavos con comas y dos decimales`() {
        assertEquals("$0.00", Dinero.pesos(0))
        assertEquals("$0.05", Dinero.pesos(5))
        assertEquals("$1,250.50", Dinero.pesos(125050))
        assertEquals("$1,248,215.37", Dinero.pesos(124821537))
        assertEquals("−$1,500.00", Dinero.pesos(-150000))
        assertEquals("−$1,500.00", Dinero.pesos(-150000, conSigno = true))
        assertEquals("+$1,500.00", Dinero.pesos(150000, conSigno = true))
        assertEquals("$1,250", Dinero.pesos(125050, sinCentavos = true))
    }

    @Test fun `centavosDe lee lo que la persona teclea sin decimales flotantes`() {
        assertEquals(125050L, Dinero.centavosDe("1,250.5"))
        assertEquals(8000L, Dinero.centavosDe("$80"))
        assertEquals(10L, Dinero.centavosDe("0.1"))
        assertEquals(1999L, Dinero.centavosDe("19.99"))
        assertNull(Dinero.centavosDe("1.234"))
        assertNull(Dinero.centavosDe("abc"))
        assertNull(Dinero.centavosDe(""))
    }

    @Test fun `el formato de importe agrupa al teclear y toma la coma decimal`() {
        assertEquals("1,250", Dinero.formatearAlTeclear("125", "1250"))
        assertEquals("18,450.00", Dinero.formatearAlTeclear("18,450.0", "18,450.00"))
        assertEquals("1,250.5", Dinero.formatearAlTeclear("1,250", "1,250.5"))
        assertEquals("12.5", Dinero.formatearAlTeclear("12", "12,5"))
        assertEquals("1.25", Dinero.formatearAlTeclear("1.25", "1.255"))
        assertEquals("", Dinero.formatearAlTeclear("1", ""))
        assertEquals("0.5", Dinero.formatearAlTeclear("", ".5"))
        assertEquals("7", Dinero.formatearAlTeclear("0", "07"))
    }

    @Test fun `la CLABE se agrupa de cuatro en cuatro`() {
        assertEquals("6461 8053 5000 0048 21", Dinero.clabeAgrupada("646180535000004821"))
    }
}
