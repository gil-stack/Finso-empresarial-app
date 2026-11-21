package mx.finsocial.empresarial.pantallas.dentro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.LecturaDeCuenta
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.PaginaDeMovimientos
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.nucleo.spei.SpeiSinConfirmar

/**
 * Lo que muestra Inicio (§3.3): cuenta, movimientos por mes y SPEI por
 * liberar. Vive mientras la persona está adentro, así al volver de un SPEI no
 * se vuelve a ver vacío.
 */
class DatosDeInicio(private val contenedor: Contenedor, val usuario: Usuario) {
    companion object {
        const val MESES_MAXIMOS = 12
    }

    private val permisos = usuario.permisos

    var lectura by mutableStateOf<LecturaDeCuenta?>(null)
        private set
    val meses = mutableStateListOf<PaginaDeMovimientos>()
    var porLiberar by mutableStateOf<List<Orden>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set
    var cargandoMes by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * El SPEI sin confirmar (ver [SpeiSinConfirmar]). Vive en el contenedor para
     * que sobreviva al bloqueo de la app.
     */
    var sinConfirmar: SpeiSinConfirmar?
        get() = contenedor.speiSinConfirmar
        set(v) { contenedor.speiSinConfirmar = v }

    /** Para que el saldo cuente desde 0 solo la primera vez. */
    var saldoYaAnimado = false

    val puedeVerMas: Boolean
        get() = permisos.verCuenta && meses.isNotEmpty() && meses.size < MESES_MAXIMOS

    val siguienteMes: String?
        get() = meses.lastOrNull()?.let { Fechas.mesAnterior(it.mes) }

    /** Primera carga o deslizar hacia abajo: cuenta, el mes actual y lo por liberar. */
    suspend fun refrescar() {
        error = null
        try {
            coroutineScope {
                val cuenta = if (permisos.verCuenta) async { contenedor.api.cuenta() } else null
                val mes = if (permisos.verCuenta) async { contenedor.api.movimientos(Fechas.mesActual(contenedor.reloj())) } else null
                val pendientes = if (permisos.liberarSpei) async { contenedor.api.ordenes(EstadoOrden.POR_LIBERAR).filter { it.puedoLiberar } } else null
                cuenta?.let { lectura = it.await() }
                mes?.let { val m = it.await(); meses.clear(); meses.add(m) }
                pendientes?.let { porLiberar = it.await() }
                contenedor.sistema.alContarPorLiberar(porLiberar.size, permisos.liberarSpei)
            }
        } catch (e: FallaApi) {
            error = e.texto
        } finally {
            cargando = false
        }
    }

    /** «Ver octubre»: el mes anterior al último cargado. */
    suspend fun cargarMesAnterior() {
        val mes = siguienteMes ?: return
        if (cargandoMes) return
        cargandoMes = true
        try {
            meses.add(contenedor.api.movimientos(mes))
        } catch (e: FallaApi) {
            error = e.texto
        } finally {
            cargandoMes = false
        }
    }
}
