package mx.finsocial.empresarial.sistema

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.builtins.ListSerializer
import mx.finsocial.empresarial.R
import mx.finsocial.empresarial.nucleo.api.JsonFinso
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.avisos.Avisos
import mx.finsocial.empresarial.nucleo.avisos.SeguimientoDeOrden
import mx.finsocial.empresarial.nucleo.avisos.Simbolo
import mx.finsocial.empresarial.nucleo.avisos.Tono
import mx.finsocial.empresarial.nucleo.avisos.VistaDeAviso
import java.time.Instant

/**
 * La notificación en vivo de cada SPEI (§11, «Android: equivalente»).
 *
 * - En Android 16 es un «Live Update»: `ProgressStyle` con
 *   la petición de «promoted ongoing», que sale como chip en la barra de estado con
 *   el importe.
 * - Antes, una notificación en curso con barra de avance.
 * - En la pantalla bloqueada solo dice «SPEI en proceso».
 * - No suena. Al terminar se queda 15 min. Sin noticias en 30 min dice
 *   «Sin confirmar, abre la app».
 */
class SpeiEnVivo(private val contexto: Context, private val reloj: () -> Instant = Instant::now) {
    companion object {
        const val CANAL = "spei-en-vivo"
        const val ACCION_VIEJO = "mx.finsocial.empresarial.AVISO_VIEJO"

        /** `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` de Android 16. */
        const val PEDIR_PROMOCION = "android.requestPromotedOngoing"
        private const val PREFERENCIAS = "finso.avisos"
        private const val CLAVE = "seguimientos"
        private val serie = ListSerializer(SeguimientoDeOrden.serializer())

        private const val MENTA = 0xFF1DBB9D.toInt()
        private const val AMBAR = 0xFFE0A100.toInt()
        private const val VERDE = 0xFF49C095.toInt()
        private const val GRIS = 0xFF7A8B8C.toInt()

        fun idDe(ref: String) = 7000 + (ref.hashCode() and 0x0FFFFFFF) % 100_000
    }

    private val prefs = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
    private val gestor = contexto.getSystemService(NotificationManager::class.java)

    @Synchronized
    fun seguimientos(): List<SeguimientoDeOrden> =
        runCatching { JsonFinso.decodeFromString(serie, prefs.getString(CLAVE, "[]") ?: "[]") }.getOrDefault(emptyList())

    @Synchronized
    private fun guardar(lista: List<SeguimientoDeOrden>) {
        prefs.edit().putString(CLAVE, JsonFinso.encodeToString(serie, lista)).apply()
    }

    /** Empieza a seguir la orden si no ha terminado, o actualiza la que ya se sigue. */
    @Synchronized
    fun alVer(o: Orden) {
        val lista = seguimientos()
        val seguida = lista.any { it.ref == o.id }
        if (!seguida && o.esFinal) return
        val (nueva, fuera) = Avisos.agregar(lista, SeguimientoDeOrden.de(o, reloj()))
        fuera.forEach { gestor.cancel(idDe(it.ref)) }
        guardar(nueva.filterNot { it.esFinal })
        nueva.firstOrNull { it.ref == o.id }?.let(::publicar)
    }

    /** Un mensaje de datos de FCM (contrato §5.1). */
    @Synchronized
    fun alMensaje(datos: Map<String, String>) {
        val lista = seguimientos()
        val nuevo = Avisos.aplicarMensaje(lista, datos) ?: return
        guardar(lista.map { if (it.ref == nuevo.ref) nuevo else it }.filterNot { it.esFinal })
        publicar(nuevo)
    }

    /** Vuelve a pintar todas (para marcar las viejas). */
    @Synchronized
    fun refrescar() = seguimientos().forEach(::publicar)

    fun enCurso(): List<String> = seguimientos().filterNot { it.esFinal }.map { it.ref }

    @Synchronized
    fun quitarTodas() {
        seguimientos().forEach { gestor.cancel(idDe(it.ref)) }
        guardar(emptyList())
    }

    private fun puedeAvisar(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(contexto).areNotificationsEnabled()

    private fun crearCanal() {
        if (gestor.getNotificationChannel(CANAL) != null) return
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, "SPEI en vivo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cómo va cada SPEI que pediste o liberaste."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    @DrawableRes
    private fun icono(s: Simbolo) = when (s) {
        Simbolo.RELOJ -> R.drawable.aviso_reloj
        Simbolo.ENVIANDO -> R.drawable.aviso_enviando
        Simbolo.PALOMITA -> R.drawable.aviso_palomita
        Simbolo.TACHE -> R.drawable.aviso_tache
        Simbolo.REGRESO -> R.drawable.aviso_regreso
        Simbolo.SIN_NOTICIAS -> R.drawable.aviso_sin_noticias
    }

    private fun color(t: Tono) = when (t) {
        Tono.AVISO -> AMBAR
        Tono.MENTA -> MENTA
        Tono.EXITO -> VERDE
        Tono.SECUNDARIO -> GRIS
    }

    private fun alAbrir(ref: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Avisos.enlaceDeOrden(ref)))
            .setPackage(contexto.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(contexto, idDe(ref), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun publicar(s: SeguimientoDeOrden) {
        if (!puedeAvisar()) return
        crearCanal()
        val v = Avisos.vista(s, reloj())
        val n = if (Build.VERSION.SDK_INT >= 36) enVivo16(s, v) else enCurso(s, v)
        gestor.notify(idDe(s.ref), n)
        if (v.enCurso) programarViejo(s)
    }

    /** La versión sin importe ni nombre para la pantalla bloqueada. */
    private fun publica(v: VistaDeAviso): Notification =
        NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(icono(v.simbolo))
            .setContentTitle(Avisos.PUBLICO)
            .setColor(color(v.tono))
            .build()

    private fun enCurso(s: SeguimientoDeOrden, v: VistaDeAviso): Notification {
        val b = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(icono(v.simbolo))
            .setContentTitle(v.detalle)
            .setContentText(v.titulo)
            .setColor(color(v.tono))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica(v))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(v.enCurso)
            .setAutoCancel(!v.enCurso)
            .setContentIntent(alAbrir(s.ref))
            .setWhen(runCatching { Instant.parse(s.actualizadaEn).toEpochMilli() }.getOrDefault(System.currentTimeMillis()))
            .setShowWhen(true)
        if (v.enCurso) b.setProgress(3, v.avance, false) else b.setTimeoutAfter(Avisos.DESPUES_DEL_FINAL.toMillis())
        if (v.simbolo == Simbolo.PALOMITA) b.addAction(0, "Ver", alAbrir(s.ref))
        return b.build()
    }

    /** Android 16: Live Update con barra de avance en tres partes (pedido, liberado, enviado). */
    private fun enVivo16(s: SeguimientoDeOrden, v: VistaDeAviso): Notification {
        val b = Notification.Builder(contexto, CANAL)
            .setSmallIcon(icono(v.simbolo))
            .setContentTitle(v.detalle)
            .setContentText(v.titulo)
            .setColor(color(v.tono))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publica(v))
            .setOnlyAlertOnce(true)
            .setOngoing(v.enCurso)
            .setAutoCancel(!v.enCurso)
            .setContentIntent(alAbrir(s.ref))
            .setWhen(runCatching { Instant.parse(s.actualizadaEn).toEpochMilli() }.getOrDefault(System.currentTimeMillis()))
            .setShowWhen(true)
        if (v.enCurso) {
            val estilo = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(
                    listOf(
                        Notification.ProgressStyle.Segment(100).setColor(AMBAR),
                        Notification.ProgressStyle.Segment(100).setColor(MENTA),
                        Notification.ProgressStyle.Segment(100).setColor(VERDE),
                    ),
                )
                .setProgress(v.avance * 100)
            // Pide salir como Live Update. El SDK con el que compilamos no trae el método
            // `setRequestPromotedOngoing`, pero el sistema lee este mismo dato.
            b.setStyle(estilo)
                .addExtras(Bundle().apply { putBoolean(PEDIR_PROMOCION, true) })
                .setShortCriticalText(s.importe)
        } else {
            b.setTimeoutAfter(Avisos.DESPUES_DEL_FINAL.toMillis())
        }
        if (v.simbolo == Simbolo.PALOMITA) {
            b.addAction(Notification.Action.Builder(null, "Ver", alAbrir(s.ref)).build())
        }
        return b.build()
    }

    /** Sin noticias en 30 min se vuelve a pintar como «Sin confirmar» (alarma inexacta, sin permisos extra). */
    private fun programarViejo(s: SeguimientoDeOrden) {
        val alarmas = contexto.getSystemService(AlarmManager::class.java) ?: return
        val cuando = runCatching { Instant.parse(s.actualizadaEn) }.getOrDefault(reloj()).plus(Avisos.VIEJO).plusSeconds(5)
        val intent = Intent(contexto, ReceptorDeAvisos::class.java).setAction(ACCION_VIEJO)
        val pi = PendingIntent.getBroadcast(contexto, idDe(s.ref), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarmas.set(AlarmManager.RTC, cuando.toEpochMilli(), pi)
    }
}

/** Despierta a los 30 min sin noticias para marcar las notificaciones viejas. */
class ReceptorDeAvisos : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SpeiEnVivo.ACCION_VIEJO) SpeiEnVivo(context.applicationContext).refrescar()
    }
}
