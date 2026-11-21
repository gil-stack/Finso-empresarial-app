package mx.finsocial.empresarial.sistema

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import mx.finsocial.empresarial.R
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.nucleo.avisos.Enlace

/** El [Sistema] de verdad: notificaciones, widget y accesos directos. */
class SistemaAndroid(private val contexto: Context) : Sistema {
    private val enVivo = SpeiEnVivo(contexto)

    override fun alVerOrden(orden: Orden) = enVivo.alVer(orden)

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
