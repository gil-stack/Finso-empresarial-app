package mx.finsocial.empresarial.sistema

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import mx.finsocial.empresarial.R
import mx.finsocial.empresarial.nucleo.avisos.Enlace

/**
 * El widget (§12): «SPEI por liberar: N» con «Revisar», o «Mandar SPEI» si no
 * hay nada. Nunca muestra saldo ni movimientos: se ve sin desbloquear.
 */
class WidgetFinso : AppWidgetProvider() {
    companion object {
        private const val PREFERENCIAS = "finso.widget"

        fun textos(porLiberar: Int): Pair<String, String> =
            if (porLiberar > 0) "SPEI por liberar: $porLiberar" to "Revisar" else "Finso Empresarial" to "Mandar SPEI"

        fun actualizar(contexto: Context, porLiberar: Int) {
            contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit().putInt("porLiberar", porLiberar).apply()
            val gestor = AppWidgetManager.getInstance(contexto) ?: return
            val ids = runCatching { gestor.getAppWidgetIds(ComponentName(contexto, WidgetFinso::class.java)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) pintar(contexto, gestor, ids)
        }

        private fun pintar(contexto: Context, gestor: AppWidgetManager, ids: IntArray) {
            val n = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).getInt("porLiberar", 0)
            val (titulo, boton) = textos(n)
            val enlace = if (n > 0) Enlace.POR_LIBERAR else Enlace.MANDAR
            val abrir = PendingIntent.getActivity(
                contexto,
                if (n > 0) 2 else 1,
                Intent(Intent.ACTION_VIEW, Uri.parse(enlace)).setPackage(contexto.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val vistas = RemoteViews(contexto.packageName, R.layout.widget_finso).apply {
                setTextViewText(R.id.widget_titulo, titulo)
                setTextViewText(R.id.widget_boton, boton)
                setOnClickPendingIntent(R.id.widget_boton, abrir)
                setOnClickPendingIntent(R.id.widget_raiz, abrir)
            }
            gestor.updateAppWidget(ids, vistas)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        pintar(context, appWidgetManager, appWidgetIds)
}
