package mx.finsocial.empresarial.sistema

import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.Usuario

/**
 * Las piezas del sistema fuera de la app (especificación §11 y §12): la
 * notificación en vivo del SPEI, el widget y los accesos directos. En las
 * pruebas de pantallas se usa [Nada].
 */
interface Sistema {
    /** Se vio una orden (al pedir, liberar, en el resultado o al consultarla). */
    fun alVerOrden(orden: Orden)

    /** Las órdenes que se siguen en vivo y aún no terminan. */
    fun ordenesEnCurso(): List<String>

    /** Cuántos SPEI hay por liberar, para el widget. */
    fun alContarPorLiberar(cantidad: Int, puedeLiberar: Boolean)

    /** Entró: accesos directos según los permisos. */
    fun alEntrar(usuario: Usuario)

    /** Salió u olvidó el teléfono: se quitan accesos, widget y notificaciones. */
    fun alSalir()

    object Nada : Sistema {
        override fun alVerOrden(orden: Orden) = Unit
        override fun ordenesEnCurso(): List<String> = emptyList()
        override fun alContarPorLiberar(cantidad: Int, puedeLiberar: Boolean) = Unit
        override fun alEntrar(usuario: Usuario) = Unit
        override fun alSalir() = Unit
    }
}
