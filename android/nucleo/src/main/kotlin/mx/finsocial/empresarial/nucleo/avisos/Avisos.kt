package mx.finsocial.empresarial.nucleo.avisos

import kotlinx.serialization.Serializable
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.Orden
import java.time.Duration
import java.time.Instant

/** El símbolo de cada estado (especificación §11). */
enum class Simbolo { RELOJ, ENVIANDO, PALOMITA, TACHE, REGRESO, SIN_NOTICIAS }

/** El color de cada estado: ámbar, menta, verde o secundario. */
enum class Tono { AVISO, MENTA, EXITO, SECUNDARIO }

/**
 * Lo que sigue una notificación en vivo de un SPEI. Nunca lleva saldo ni CLABE
 * completa: se ve sin desbloquear el teléfono.
 */
@Serializable
data class SeguimientoDeOrden(
    val ref: String,
    val importeCentavos: Long,
    val destino: String,
    val estado: String,
    val liberadaPor: String? = null,
    val actualizadaEn: String,
    val desde: String,
) {
    val esFinal: Boolean get() = estado in EstadoOrden.finales

    /** `$18,450` si no hay centavos; `$18,450.50` si los hay. */
    val importe: String get() = Dinero.pesos(importeCentavos, sinCentavos = importeCentavos % 100 == 0L)

    companion object {
        fun de(o: Orden, ahora: Instant): SeguimientoDeOrden = SeguimientoDeOrden(
            ref = o.id,
            importeCentavos = o.importeCentavos,
            destino = NombreLegible.de(o.destino.titular),
            estado = o.estado,
            liberadaPor = o.liberadaPor?.let(NombreLegible::corto),
            actualizadaEn = o.actualizadaEn,
            desde = ahora.toString(),
        )
    }
}

/** Lo que se ve en la notificación para un seguimiento en un momento dado. */
data class VistaDeAviso(
    val simbolo: Simbolo,
    val tono: Tono,
    val titulo: String,
    val detalle: String,
    val enCurso: Boolean,
    /** Avance de 0 a 3: pedido, liberado, enviado. */
    val avance: Int,
)

object Avisos {
    /** Sin noticias en 30 min se marca como viejo (§11). */
    val VIEJO: Duration = Duration.ofMinutes(30)

    /** Al terminar, la notificación se queda 15 min. */
    val DESPUES_DEL_FINAL: Duration = Duration.ofMinutes(15)

    const val MAXIMO = 3

    /** Lo que se ve en la pantalla bloqueada (sin importe ni nombre). */
    const val PUBLICO = "SPEI en proceso"

    fun vista(s: SeguimientoDeOrden, ahora: Instant): VistaDeAviso {
        val a = "${s.importe} a ${s.destino}"
        val viejo = !s.esFinal && runCatching { Duration.between(Instant.parse(s.actualizadaEn), ahora) > VIEJO }.getOrDefault(false) &&
            runCatching { Duration.between(Instant.parse(s.desde), ahora) > VIEJO }.getOrDefault(true)
        if (viejo) return VistaDeAviso(Simbolo.SIN_NOTICIAS, Tono.SECUNDARIO, "Sin confirmar, abre la app", a, enCurso = true, avance = avanceDe(s.estado))
        return when (s.estado) {
            EstadoOrden.POR_LIBERAR -> VistaDeAviso(Simbolo.RELOJ, Tono.AVISO, "Falta que la liberen", a, true, 1)
            EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> VistaDeAviso(
                Simbolo.ENVIANDO, Tono.MENTA,
                s.liberadaPor?.let { "Enviando… · Liberado por $it" } ?: "Enviando…",
                a, true, 2,
            )
            EstadoOrden.LIQUIDADA -> VistaDeAviso(Simbolo.PALOMITA, Tono.EXITO, "Enviado", a, false, 3)
            EstadoOrden.RECHAZADA -> VistaDeAviso(Simbolo.TACHE, Tono.SECUNDARIO, "Rechazado", a, false, 0)
            EstadoOrden.DEVUELTA -> VistaDeAviso(Simbolo.REGRESO, Tono.SECUNDARIO, "Devuelto", a, false, 0)
            else -> VistaDeAviso(Simbolo.TACHE, Tono.SECUNDARIO, "Cancelado", a, false, 0)
        }
    }

    private fun avanceDe(estado: String) = when (estado) {
        EstadoOrden.POR_LIBERAR -> 1
        EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> 2
        else -> 0
    }

    /**
     * Agrega o actualiza un seguimiento. Máximo [MAXIMO] a la vez: si ya hay
     * 3, se termina el más viejo. Regresa la lista nueva y los que salieron.
     */
    fun agregar(lista: List<SeguimientoDeOrden>, nuevo: SeguimientoDeOrden): Pair<List<SeguimientoDeOrden>, List<SeguimientoDeOrden>> {
        val previo = lista.firstOrNull { it.ref == nuevo.ref }
        val actualizado = if (previo != null) nuevo.copy(desde = previo.desde) else nuevo
        val resto = lista.filter { it.ref != nuevo.ref }
        val todos = (resto + actualizado).sortedBy { it.desde }
        val fuera = if (todos.size > MAXIMO) todos.take(todos.size - MAXIMO) else emptyList()
        return todos.drop(fuera.size) to fuera
    }

    /**
     * Un mensaje de datos de FCM (contrato §5.1): todos los valores son texto.
     * Aplica el cambio solo si es de una orden que se sigue y es más nuevo.
     */
    fun aplicarMensaje(lista: List<SeguimientoDeOrden>, datos: Map<String, String>): SeguimientoDeOrden? {
        if (datos["tipo"] != "ORDEN") return null
        val id = datos["id"] ?: return null
        val estado = datos["estado"] ?: return null
        val actual = lista.firstOrNull { it.ref == id } ?: return null
        val cuando = datos["actualizadaEn"] ?: return null
        val nuevo = runCatching { Instant.parse(cuando) }.getOrNull() ?: return null
        val anterior = runCatching { Instant.parse(actual.actualizadaEn) }.getOrNull()
        if (anterior != null && nuevo.isBefore(anterior)) return null
        return actual.copy(estado = estado, liberadaPor = datos["liberadaPor"]?.let(NombreLegible::corto) ?: actual.liberadaPor, actualizadaEn = cuando)
    }

    /** `finso-empresarial://orden?ref=spo_…` */
    fun enlaceDeOrden(ref: String) = "finso-empresarial://orden?ref=$ref"
}

/** A dónde lleva un enlace `finso-empresarial://…` (notificación, widget o acceso directo). */
sealed interface Enlace {
    data class Orden(val ref: String) : Enlace
    data object Mandar : Enlace
    data object PorLiberar : Enlace

    companion object {
        const val ESQUEMA = "finso-empresarial"
        const val MANDAR = "$ESQUEMA://mandar"
        const val POR_LIBERAR = "$ESQUEMA://por-liberar"

        private val ref = Regex("^spo_[A-Za-z0-9_]{1,64}$")

        /** Solo acepta los tres enlaces conocidos; cualquier otra cosa es null. */
        fun leer(texto: String?): Enlace? {
            if (texto == null || !texto.startsWith("$ESQUEMA://")) return null
            val resto = texto.removePrefix("$ESQUEMA://")
            val (ruta, consulta) = resto.split('?', limit = 2).let { it[0].trimEnd('/') to it.getOrNull(1) }
            return when (ruta) {
                "mandar" -> Mandar
                "por-liberar" -> PorLiberar
                "orden" -> consulta?.split('&')?.firstNotNullOfOrNull { p ->
                    p.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "ref" }?.get(1)
                }?.takeIf { ref.matches(it) }?.let(::Orden)
                else -> null
            }
        }
    }
}
