package mx.finsocial.empresarial

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import mx.finsocial.empresarial.nucleo.api.DestinoBreve
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.pantallas.Raiz
import mx.finsocial.empresarial.sistema.SpeiEnVivo
import mx.finsocial.empresarial.sistema.WidgetFinso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** La notificación en vivo del SPEI (§11), las ligas internas y el widget (§12). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Capturas.TELEFONO)
class AvisosEnVivoTest {
    @get:Rule val compose = createComposeRule()
    private val contexto = ApplicationProvider.getApplicationContext<Application>()
    private val gestor = contexto.getSystemService(NotificationManager::class.java)
    private var ahora = Instant.parse("2026-11-21T18:00:00Z")
    private val enVivo by lazy { SpeiEnVivo(contexto) { ahora } }

    @Before fun permisos() {
        shadowOf(contexto).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun orden(id: String = "spo_1", estado: String = EstadoOrden.POR_LIBERAR, liberadaPor: String? = null) = Orden(
        id = id, estado = estado,
        destino = DestinoBreve("dst_1", "FLETES RAPIDOS DEL NORTE", "BANORTE", "0921"),
        importeCentavos = 1_845_000, concepto = "Flete", pedidaPor = "Luis Pérez Soto", liberadaPor = liberadaPor,
        creadaEn = ahora.toString(), actualizadaEn = ahora.toString(),
    )

    private fun aviso(ref: String): Notification? = shadowOf(gestor).getNotification(SpeiEnVivo.idDe(ref))

    private fun Notification.titulo() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun Notification.texto() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private val Notification.enCurso get() = flags and Notification.FLAG_ONGOING_EVENT != 0

    @Test fun `de pedido a enviado, en curso y sin sonido, y en la pantalla bloqueada solo dice SPEI en proceso`() {
        enVivo.alVer(orden())
        val n = aviso("spo_1")!!
        assertTrue(n.titulo()!!.startsWith("$18,450 a "))
        assertEquals("Falta que la liberen", n.texto())
        assertTrue(n.enCurso)
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("SPEI en proceso", n.publicVersion.titulo())
        assertNull(n.publicVersion.texto())
        val canal = gestor.getNotificationChannel(SpeiEnVivo.CANAL)
        assertNull(canal.sound)
        assertFalse(canal.shouldVibrate())
        assertEquals(listOf("spo_1"), enVivo.enCurso())

        enVivo.alVer(orden(estado = EstadoOrden.EN_PROCESO, liberadaPor = "Ana López Garza"))
        assertTrue(aviso("spo_1")!!.texto()!!.startsWith("Enviando"))

        enVivo.alVer(orden(estado = EstadoOrden.LIQUIDADA, liberadaPor = "Ana López Garza"))
        val fin = aviso("spo_1")!!
        assertEquals("Enviado", fin.texto())
        assertFalse(fin.enCurso)
        assertEquals("Ver", fin.actions.single().title.toString())
        assertTrue(enVivo.enCurso().isEmpty())
    }

    @Test fun `una orden que ya termino y no se seguia no avisa`() {
        enVivo.alVer(orden(estado = EstadoOrden.LIQUIDADA))
        assertNull(aviso("spo_1"))
    }

    @Test fun `sin noticias en 30 min dice sin confirmar`() {
        enVivo.alVer(orden())
        ahora = ahora.plusSeconds(31 * 60)
        enVivo.refrescar()
        assertEquals("Sin confirmar, abre la app", aviso("spo_1")!!.texto())
    }

    @Test fun `maximo 3 y al salir se quitan todas`() {
        listOf("spo_a", "spo_b", "spo_c", "spo_d").forEach {
            enVivo.alVer(orden(id = it))
            ahora = ahora.plusSeconds(1)
        }
        assertNull(aviso("spo_a"))
        assertEquals(listOf("spo_b", "spo_c", "spo_d"), enVivo.enCurso())
        enVivo.quitarTodas()
        assertTrue(shadowOf(gestor).allNotifications.isEmpty())
        assertTrue(enVivo.enCurso().isEmpty())
    }

    @Test fun `sin permiso de notificaciones no publica nada`() {
        shadowOf(contexto).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        enVivo.alVer(orden())
        assertNull(aviso("spo_1"))
    }

    @Test fun `el widget nunca muestra saldo`() {
        assertEquals("SPEI por liberar: 2" to "Revisar", WidgetFinso.textos(2))
        assertEquals("Finso Empresarial" to "Mandar SPEI", WidgetFinso.textos(0))
    }

    @Test fun `la liga de la notificacion abre la orden y una liga rara se ignora`() {
        val app = AppDePrueba()
        compose.setContent { Raiz(app.contenedor) }
        val r = Recorrido(compose) { nombre -> compose.onRoot().captureRoboImage(Capturas.ruta("avisos-$nombre")) }
        r.entrarComo("ana@empresa.mx", "Ana")
        app.contenedor.abrirEnlace("https://otro.sitio/orden?ref=spo_SIM_PENDIENTE")
        app.contenedor.abrirEnlace("finso-empresarial://orden?ref=../../yo")
        compose.waitForIdle()
        assertNull(app.contenedor.enlace.value)
        r.esperarEtiqueta("saludo")
        app.contenedor.abrirEnlace("finso-empresarial://orden?ref=spo_SIM_PENDIENTE")
        r.esperar("Listo, falta liberarlo").esperarEtiqueta("referencia").foto("orden")
        assertNull(app.contenedor.enlace.value)
        r.tocarEtiqueta("listo").esperarEtiqueta("saludo")
        app.contenedor.abrirEnlace("finso-empresarial://mandar")
        r.esperarEtiqueta("destino-dst_SIM_PAPELERA")
    }

    @Test fun `quien no libera no entra a por liberar desde una liga`() {
        val app = AppDePrueba()
        compose.setContent { Raiz(app.contenedor) }
        val r = Recorrido(compose) { }
        r.entrarComo("luis@empresa.mx", "Luis")
        app.contenedor.abrirEnlace("finso-empresarial://por-liberar")
        compose.waitForIdle()
        r.esperarEtiqueta("saludo")
        assertFalse(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("orden-spo_SIM_PENDIENTE")).fetchSemanticsNodes().isNotEmpty())
    }
}

/** Android 16: Live Update con ProgressStyle y chip en la barra de estado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AvisosAndroid16Test {
    @Test fun `en Android 16 pide salir como Live Update con el importe en el chip`() {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(contexto).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val ahora = Instant.parse("2026-11-21T18:00:00Z")
        SpeiEnVivo(contexto) { ahora }.alVer(
            Orden(
                id = "spo_1", estado = EstadoOrden.POR_LIBERAR,
                destino = DestinoBreve("dst_1", "FLETES RAPIDOS DEL NORTE", "BANORTE", "0921"),
                importeCentavos = 1_845_000, concepto = "Flete", pedidaPor = "Luis Pérez Soto",
                creadaEn = ahora.toString(), actualizadaEn = ahora.toString(),
            ),
        )
        val n = shadowOf(contexto.getSystemService(NotificationManager::class.java)).getNotification(SpeiEnVivo.idDe("spo_1"))!!
        assertTrue(n.extras.getBoolean(SpeiEnVivo.PEDIR_PROMOCION))
        assertEquals(Notification.ProgressStyle::class.java.name, n.extras.getString(Notification.EXTRA_TEMPLATE))
    }
}
