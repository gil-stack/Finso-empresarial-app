package mx.finsocial.empresarial.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Caso 3 de la especificación compartida. */
class FechasTest {
    private val utc = Instant.parse("2026-11-21T01:30:00Z")

    @Test fun `las horas se muestran en hora de Mexico`() {
        assertEquals("19:30", Fechas.hora(utc))
        assertEquals("20 nov 2026", Fechas.fechaCorta(utc))
        assertEquals("20 nov 2026, 19:30", Fechas.fechaYHora(utc))
        assertEquals("2026-11", Fechas.mesActual(utc))
        assertEquals("2026-11", Fechas.mesActual(Instant.parse("2026-12-01T03:00:00Z")))
    }

    @Test fun `dia dice Hoy, Ayer o la fecha`() {
        val ahora = Instant.parse("2026-11-21T18:00:00Z")
        assertEquals("Hoy", Fechas.dia(Instant.parse("2026-11-21T15:00:00Z"), ahora))
        assertEquals("Ayer", Fechas.dia(Instant.parse("2026-11-20T15:00:00Z"), ahora))
        assertEquals("2 nov 2026", Fechas.dia(Instant.parse("2026-11-02T15:00:00Z"), ahora))
    }

    @Test fun `faltan en palabras redondeando hacia arriba`() {
        assertEquals("en unos segundos", Fechas.faltan(Duration.ofSeconds(20)))
        assertEquals("en 18 min", Fechas.faltan(Duration.ofSeconds(17 * 60 + 30)))
        assertEquals("en 1 h", Fechas.faltan(Duration.ofHours(1)))
        assertEquals("en 1 h 5 min", Fechas.faltan(Duration.ofMinutes(65)))
    }

    @Test fun `meses`() {
        assertEquals("noviembre 2026", Fechas.nombreDelMes("2026-11"))
        assertEquals("2026-10", Fechas.mesAnterior("2026-11"))
        assertEquals("2025-12", Fechas.mesAnterior("2026-01"))
        assertEquals("octubre", Fechas.soloMes("2026-10"))
    }

    @Test fun `hace cuanto`() {
        val ahora = Instant.parse("2026-11-21T18:00:00Z")
        assertEquals("hace 40 min", Fechas.hace(ahora.minusSeconds(40 * 60), ahora))
        assertEquals("hace 2 h", Fechas.hace(ahora.minusSeconds(2 * 3600 + 60), ahora))
        assertEquals("hace un momento", Fechas.hace(ahora.minusSeconds(10), ahora))
    }
}
