package mx.finsocial.empresarial.nucleo.spei

import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.Destino
import mx.finsocial.empresarial.nucleo.api.Movimiento
import java.time.Duration
import java.time.Instant

/** Movimientos de un día con su título («Hoy», «Ayer», «19 nov 2026»). */
data class DiaDeMovimientos(val titulo: String, val movimientos: List<Movimiento>)

object Listas {
    /** Agrupa por día en hora de México, del más reciente al más viejo (§3.3). */
    fun porDia(movimientos: List<Movimiento>, ahora: Instant): List<DiaDeMovimientos> =
        movimientos
            .sortedByDescending { Instant.parse(it.fecha) }
            .groupBy { Fechas.enMexico(Instant.parse(it.fecha)).toLocalDate() }
            .map { (_, lista) -> DiaDeMovimientos(Fechas.dia(Instant.parse(lista.first().fecha), ahora), lista) }

    /** Primero los que se pueden pagar, luego por nombre (§3.4 paso 1). */
    fun destinosOrdenados(destinos: List<Destino>): List<Destino> =
        destinos.sortedWith(compareBy<Destino>({ !it.sePuedePagar }, { NombreLegible.de(it.titular).lowercase() }))

    /** La línea de abajo de un destino. */
    fun lineaDeDestino(d: Destino, ahora: Instant): String {
        val cuenta = listOfNotNull(d.banco, "•••• ${d.clabeFinal}").joinToString(" · ")
        return when {
            d.esColaborador -> "Colaborador · se paga desde Nómina en el portal"
            d.enEspera -> {
                val desde = d.activoDesde?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (desde == null || !desde.isAfter(ahora)) "Nueva · se está activando"
                else "Nueva · se activa ${Fechas.faltan(Duration.between(ahora, desde))}"
            }
            else -> "Proveedor · $cuenta"
        }
    }

    /** Cuándo vuelve a pedir la lista: al cumplirse el primer destino en espera. */
    fun proximaActivacion(destinos: List<Destino>, ahora: Instant): Instant? =
        destinos.filter { it.enEspera }
            .mapNotNull { d -> d.activoDesde?.let { runCatching { Instant.parse(it) }.getOrNull() } }
            .filter { it.isAfter(ahora) }
            .minOrNull()
}
