package mx.finsocial.empresarial.nucleo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * México (zona centro) está en UTC−6 todo el año desde 2022 (ya no hay horario
 * de verano). Las fechas llegan del servidor en UTC y se muestran así.
 */
object Fechas {
    val MEXICO: ZoneOffset = ZoneOffset.ofHours(-6)

    private val meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val mesesLargos = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    fun enMexico(utc: Instant): ZonedDateTime = utc.atZone(MEXICO)

    private fun dos(n: Int) = n.toString().padStart(2, '0')

    /** `14:32` */
    fun hora(utc: Instant): String = enMexico(utc).let { "${dos(it.hour)}:${dos(it.minute)}" }

    /** `21 nov 2026` */
    fun fechaCorta(utc: Instant): String = enMexico(utc).let { "${it.dayOfMonth} ${meses[it.monthValue - 1]} ${it.year}" }

    /** `21 nov 2026, 14:32` */
    fun fechaYHora(utc: Instant): String = "${fechaCorta(utc)}, ${hora(utc)}"

    /** `noviembre 2026` a partir de `2026-11`. */
    fun nombreDelMes(mes: String): String {
        val (anio, m) = mes.split('-')
        return "${mesesLargos[m.toInt() - 1]} $anio"
    }

    /** Solo el nombre del mes: `octubre` a partir de `2026-10` (para «Ver octubre»). */
    fun soloMes(mes: String): String = mesesLargos[mes.split('-')[1].toInt() - 1]

    /** `Hoy`, `Ayer` o `21 nov 2026`, para agrupar movimientos. */
    fun dia(utc: Instant, ahora: Instant = Instant.now()): String {
        val d: LocalDate = enMexico(utc).toLocalDate()
        val h: LocalDate = enMexico(ahora).toLocalDate()
        return when (h.toEpochDay() - d.toEpochDay()) {
            0L -> "Hoy"
            1L -> "Ayer"
            else -> fechaCorta(utc)
        }
    }

    /** `2026-11` del mes actual en México. */
    fun mesActual(ahora: Instant = Instant.now()): String = enMexico(ahora).let { "${it.year}-${dos(it.monthValue)}" }

    /** El mes anterior a [mes]: `2026-11` → `2026-10`, `2026-01` → `2025-12`. */
    fun mesAnterior(mes: String): String {
        val (anio, m) = mes.split('-').map { it.toInt() }
        return if (m == 1) "${anio - 1}-12" else "$anio-${dos(m - 1)}"
    }

    /** Cuánto falta, en palabras: `en 12 min`, `en 1 h 5 min`, `en unos segundos`. Redondea los minutos hacia arriba. */
    fun faltan(d: Duration): String {
        val s = d.seconds
        if (s <= 59) return "en unos segundos"
        val totalMin = (s + 59) / 60
        val h = totalMin / 60
        val min = totalMin % 60
        return when {
            h == 0L -> "en $min min"
            min == 0L -> "en $h h"
            else -> "en $h h $min min"
        }
    }

    /** `hace 40 min`, `hace 2 h`, `hace un momento` (para «pidió Luis · hace 40 min»). */
    fun hace(desde: Instant, ahora: Instant = Instant.now()): String {
        val min = Duration.between(desde, ahora).toMinutes()
        return when {
            min < 1 -> "hace un momento"
            min < 60 -> "hace $min min"
            min < 60 * 24 -> "hace ${min / 60} h"
            else -> "el ${fechaCorta(desde)}"
        }
    }
}
