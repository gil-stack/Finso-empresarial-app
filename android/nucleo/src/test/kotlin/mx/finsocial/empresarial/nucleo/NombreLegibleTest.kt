package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class NombreLegibleTest {
    @Test fun `los nombres en mayusculas se vuelven legibles`() {
        assertEquals("Papelera del Centro SA de CV", NombreLegible.de("PAPELERA DEL CENTRO SA DE CV"))
        assertEquals("Empaques y Carton MX", NombreLegible.de("EMPAQUES Y CARTON MX"))
        assertEquals("De la Rosa Hermanos", NombreLegible.de("DE LA ROSA HERMANOS"))
        assertEquals("Fletes Rapidos del Norte", NombreLegible.de("FLETES RAPIDOS DEL NORTE"))
    }

    @Test fun `si ya trae minusculas se deja igual`() {
        assertEquals("Distribuidora del Bajío", NombreLegible.de("Distribuidora del Bajío"))
    }

    @Test fun `inicial y nombre corto`() {
        assertEquals("P", NombreLegible.inicial("papelera"))
        assertEquals("Ana", NombreLegible.corto("Ana López Garza"))
    }
}
