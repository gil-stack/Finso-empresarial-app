package mx.finsocial.empresarial.nucleo

import mx.finsocial.empresarial.nucleo.api.DestinoBreve
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.avisos.Avisos
import mx.finsocial.empresarial.nucleo.avisos.Enlace
import mx.finsocial.empresarial.nucleo.avisos.SeguimientoDeOrden
import mx.finsocial.empresarial.nucleo.avisos.Simbolo
import mx.finsocial.empresarial.nucleo.avisos.Tono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** La notificación en vivo del SPEI (§11): textos por estado, máximo 3 y mensajes de FCM. */
class AvisosTest {
    private val ahora = Instant.parse("2026-11-21T18:00:00Z")

    private fun orden(id: String = "spo_1", importe: Long = 1_845_000, estado: String = EstadoOrden.POR_LIBERAR) = Orden(
        id = id, estado = estado,
        destino = DestinoBreve("dst_1", "FLETES RAPIDOS DEL NORTE", "BANORTE", "0921"),
        importeCentavos = importe, concepto = "Flete", pedidaPor = "Luis Pérez",
        creadaEn = ahora.toString(), actualizadaEn = ahora.toString(),
    )

    @Test fun `textos y simbolos por estado, sin saldo ni CLABE`() {
        val s = SeguimientoDeOrden.de(orden(), ahora)
        assertEquals("$18,450", s.importe)
        assertEquals("$18,450.50", SeguimientoDeOrden.de(orden(importe = 1_845_050), ahora).importe)
        val v = Avisos.vista(s, ahora)
        assertEquals(Simbolo.RELOJ, v.simbolo)
        assertEquals(Tono.AVISO, v.tono)
        assertEquals("Falta que la liberen", v.titulo)
        assertEquals("$18,450 a ${NombreLegible.de("FLETES RAPIDOS DEL NORTE")}", v.detalle)
        assertTrue(v.enCurso)
        assertFalse(v.detalle.contains("0921"))

        val enviando = Avisos.vista(s.copy(estado = EstadoOrden.EN_PROCESO, liberadaPor = "Ana"), ahora)
        assertEquals("Enviando… · Liberado por Ana", enviando.titulo)
        assertEquals(Tono.MENTA, enviando.tono)
        val enviado = Avisos.vista(s.copy(estado = EstadoOrden.LIQUIDADA), ahora)
        assertEquals(Simbolo.PALOMITA, enviado.simbolo)
        assertEquals("Enviado", enviado.titulo)
        assertFalse(enviado.enCurso)
        assertEquals("Rechazado", Avisos.vista(s.copy(estado = EstadoOrden.RECHAZADA), ahora).titulo)
        assertEquals("Devuelto", Avisos.vista(s.copy(estado = EstadoOrden.DEVUELTA), ahora).titulo)
        assertEquals("Cancelado", Avisos.vista(s.copy(estado = EstadoOrden.CANCELADA), ahora).titulo)
    }

    @Test fun `sin noticias en 30 minutos dice sin confirmar, pero un final no`() {
        val s = SeguimientoDeOrden.de(orden(), ahora)
        val despues = ahora.plusSeconds(31 * 60)
        assertEquals("Sin confirmar, abre la app", Avisos.vista(s, despues).titulo)
        assertEquals(Simbolo.SIN_NOTICIAS, Avisos.vista(s, despues).simbolo)
        assertEquals("Enviado", Avisos.vista(s.copy(estado = EstadoOrden.LIQUIDADA), despues).titulo)
    }

    @Test fun `maximo 3 a la vez y se va la mas vieja`() {
        var lista = emptyList<SeguimientoDeOrden>()
        for (i in 1..3) lista = Avisos.agregar(lista, SeguimientoDeOrden.de(orden("spo_$i"), ahora.plusSeconds(i.toLong()))).first
        val (nueva, fuera) = Avisos.agregar(lista, SeguimientoDeOrden.de(orden("spo_4"), ahora.plusSeconds(4)))
        assertEquals(listOf("spo_2", "spo_3", "spo_4"), nueva.map { it.ref })
        assertEquals(listOf("spo_1"), fuera.map { it.ref })
        // Actualizar una existente no la mueve ni saca a nadie.
        val (otra, nadie) = Avisos.agregar(nueva, SeguimientoDeOrden.de(orden("spo_2", estado = EstadoOrden.EN_PROCESO), ahora.plusSeconds(9)))
        assertEquals(listOf("spo_2", "spo_3", "spo_4"), otra.map { it.ref })
        assertTrue(nadie.isEmpty())
        assertEquals(EstadoOrden.EN_PROCESO, otra.first().estado)
    }

    @Test fun `enlaces conocidos y nada mas`() {
        assertEquals(Enlace.Orden("spo_SIM_PENDIENTE"), Enlace.leer("finso-empresarial://orden?ref=spo_SIM_PENDIENTE"))
        assertEquals(Enlace.Mandar, Enlace.leer("finso-empresarial://mandar"))
        assertEquals(Enlace.PorLiberar, Enlace.leer("finso-empresarial://por-liberar/"))
        assertNull(Enlace.leer("finso-empresarial://orden?ref=../../x"))
        assertNull(Enlace.leer("finso-empresarial://pagar?a=1"))
        assertNull(Enlace.leer("https://empresas.finsocial.app/orden?ref=spo_1"))
        assertNull(Enlace.leer(null))
    }

    @Test fun `mensaje de FCM actualiza solo ordenes seguidas y mas nuevas`() {
        val lista = listOf(SeguimientoDeOrden.de(orden(), ahora))
        val datos = mapOf("tipo" to "ORDEN", "id" to "spo_1", "estado" to "LIQUIDADA", "liberadaPor" to "Ana López Garza", "actualizadaEn" to "2026-11-21T18:05:00Z")
        val n = Avisos.aplicarMensaje(lista, datos)!!
        assertEquals(EstadoOrden.LIQUIDADA, n.estado)
        assertEquals("Ana", n.liberadaPor)
        assertNull(Avisos.aplicarMensaje(lista, datos + ("id" to "spo_otra")))
        assertNull(Avisos.aplicarMensaje(lista, datos + ("tipo" to "OTRO")))
        assertNull(Avisos.aplicarMensaje(lista, datos + ("actualizadaEn" to "2026-11-21T17:00:00Z")))
        assertEquals("finso-empresarial://orden?ref=spo_1", Avisos.enlaceDeOrden("spo_1"))
    }
}
