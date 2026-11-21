package mx.finsocial.empresarial.nucleo.spei

import mx.finsocial.empresarial.nucleo.TextoFirmado
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.Destino
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDelTelefono
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.PeticionSpei
import mx.finsocial.empresarial.nucleo.api.PreparacionLiberar
import mx.finsocial.empresarial.nucleo.api.PreparacionSpei
import mx.finsocial.empresarial.nucleo.api.SinRed
import mx.finsocial.empresarial.nucleo.api.SolicitudSpei
import java.time.Instant

/** Un reto que vence en [expiraEn]; se toma como vencido 5 s antes para no llegar tarde. */
private fun vencio(expiraEn: String, ahora: Instant): Boolean =
    runCatching { !ahora.isBefore(Instant.parse(expiraEn).minusSeconds(5)) }.getOrDefault(true)

/**
 * Pedir un SPEI con las reglas de reintento de la especificación §3.4:
 *
 * - Un reto, una firma y una `Idempotency-Key` por intento.
 * - Si falla la red, el siguiente intento manda **lo mismo** (cuerpo, llave y
 *   firma) sin volver a pedir la huella: si el primero sí llegó, el servidor
 *   contesta la misma orden y no se duplica.
 * - Cualquier otro error gasta el reto: el siguiente intento prepara otro.
 * - Si el reto ya venció, se prepara otro antes de firmar.
 */
class PedidoSpei(
    private val api: ApiFinso,
    private val guardado: Guardado,
    private val llave: LlaveDelTelefono,
    val solicitud: SolicitudSpei,
    private val reloj: () -> Instant = Instant::now,
) {
    private class Listo(val peticion: PeticionSpei, val idempotencia: String)

    var preparacion: PreparacionSpei? = null
        private set
    private var listo: Listo? = null

    /** Hay un envío que no se supo si llegó: el reintento no pide huella. */
    val tieneReintento: Boolean get() = listo != null

    suspend fun preparar(): PreparacionSpei = api.prepararSpei(solicitud).also {
        preparacion = it
        listo = null
    }

    suspend fun pedir(motivo: String): Orden {
        val envio = listo ?: run {
            val p = preparacion?.takeUnless { vencio(it.expiraEn, reloj()) } ?: preparar()
            val texto = TextoFirmado.spei(guardado.dispositivo.orEmpty(), p.reto, solicitud.destino, solicitud.importeCentavos, solicitud.concepto)
            val firma = llave.firmar(texto, motivo)
            Listo(
                PeticionSpei(solicitud.destino, solicitud.importeCentavos, solicitud.concepto, p.reto, firma),
                ApiFinso.nuevaLlave(),
            ).also { listo = it }
        }
        try {
            return api.pedirSpei(envio.peticion, envio.idempotencia).also { listo = null; preparacion = null }
        } catch (e: SinRed) {
            throw e
        } catch (e: FallaApi) {
            listo = null
            preparacion = null
            throw e
        }
    }
}

/**
 * Un SPEI que se mandó pero no se supo si llegó (se cortó la red). Mientras
 * exista, la app vuelve a ese mismo pedido (misma llave y firma) y no deja
 * preparar otro: así nunca quedan dos órdenes por un solo intento.
 */
class SpeiSinConfirmar(val destino: Destino, val pedido: PedidoSpei)

/**
 * Liberar o rechazar una orden de otra persona (§3.5). Mismas reglas de
 * reintento que [PedidoSpei]. Antes de firmar se prepara y se comprueba que
 * el importe y la versión sean los que la persona está viendo.
 */
class FirmaDeOrden(
    private val api: ApiFinso,
    private val guardado: Guardado,
    private val llave: LlaveDelTelefono,
    val orden: Orden,
    private val reloj: () -> Instant = Instant::now,
) {
    private enum class Accion { LIBERAR, RECHAZAR }
    private class Listo(val accion: Accion, val reto: String, val firma: String, val motivo: String?, val idempotencia: String)

    private var preparacion: PreparacionLiberar? = null
    private var listo: Listo? = null

    private suspend fun reto(): String {
        val p = preparacion?.takeUnless { vencio(it.expiraEn, reloj()) }
            ?: api.prepararLiberacion(orden.id).also { preparacion = it }
        if (p.importeCentavos != orden.importeCentavos || p.version != orden.version) {
            preparacion = null
            throw FallaApi("ORDEN_CAMBIO", 409)
        }
        return p.reto
    }

    suspend fun liberar(motivoBiometria: String): Orden {
        val envio = listo?.takeIf { it.accion == Accion.LIBERAR } ?: run {
            val r = reto()
            val texto = TextoFirmado.liberar(guardado.dispositivo.orEmpty(), r, orden.id, orden.importeCentavos)
            Listo(Accion.LIBERAR, r, llave.firmar(texto, motivoBiometria), null, ApiFinso.nuevaLlave()).also { listo = it }
        }
        return mandar { api.liberar(orden.id, envio.reto, envio.firma, envio.idempotencia) }
    }

    suspend fun rechazar(motivo: String, motivoBiometria: String): Orden {
        val envio = listo?.takeIf { it.accion == Accion.RECHAZAR && it.motivo == motivo } ?: run {
            val r = reto()
            val texto = TextoFirmado.rechazar(guardado.dispositivo.orEmpty(), r, orden.id)
            Listo(Accion.RECHAZAR, r, llave.firmar(texto, motivoBiometria), motivo, ApiFinso.nuevaLlave()).also { listo = it }
        }
        return mandar { api.rechazar(orden.id, motivo, envio.reto, envio.firma, envio.idempotencia) }
    }

    private suspend fun mandar(llamada: suspend () -> Orden): Orden {
        try {
            return llamada().also { listo = null; preparacion = null }
        } catch (e: SinRed) {
            throw e
        } catch (e: FallaApi) {
            listo = null
            preparacion = null
            throw e
        }
    }

    companion object {
        const val MOTIVO_MINIMO = 5
        const val MOTIVO_MAXIMO = 500
        fun motivoValido(m: String): Boolean = m.trim().length in MOTIVO_MINIMO..MOTIVO_MAXIMO
    }
}
