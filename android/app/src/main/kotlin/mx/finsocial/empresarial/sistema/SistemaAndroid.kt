package mx.finsocial.empresarial.sistema

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.R
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.nucleo.avisos.Enlace

/**
 * El [Sistema] de verdad: notificaciones, widget y accesos directos. Al
 * empezar a seguir una orden registra su aviso push ([registrarAviso]), si hay
 * Firebase.
 */
class SistemaAndroid(
    private val contexto: Context,
    private val registrarAviso: suspend (orden: String, token: String) -> Unit,
) : Sistema {
    private val enVivo = SpeiEnVivo(contexto)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun alVerOrden(orden: Orden) {
        if (enVivo.alVer(orden) && AvisosPush.configurado) {
            alcance.launch {
                val token = AvisosPush.token(contexto) ?: return@launch
                // Si falla, la notificación sigue con la app abierta y a los 30 min dice «Sin confirmar».
                runCatching { registrarAviso(orden.id, token) }
            }
        }
    }

    override fun ordenesEnCurso(): List<String> = enVivo.enCurso()

    override fun alContarPorLiberar(cantidad: Int, puedeLiberar: Boolean) =
        WidgetFinso.actualizar(contexto, if (puedeLiberar) cantidad else 0)

    override fun alEntrar(usuario: Usuario) {
        val p = usuario.permisos
        val accesos = buildList {
            if (p.pedirSpei) add(acceso("mandar", "Mandar SPEI", Enlace.MANDAR, R.drawable.aviso_enviando))
            if (p.liberarSpei) add(acceso("por-liberar", "Por liberar", Enlace.POR_LIBERAR, R.drawable.aviso_reloj))
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(contexto, accesos) }
    }

    override fun alSalir() {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(contexto) }
        WidgetFinso.actualizar(contexto, 0)
        enVivo.quitarTodas()
    }

    private fun acceso(id: String, texto: String, enlace: String, icono: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(contexto, id)
            .setShortLabel(texto)
            .setIcon(IconCompat.createWithResource(contexto, icono))
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(enlace)).setPackage(contexto.packageName))
            .build()
}
