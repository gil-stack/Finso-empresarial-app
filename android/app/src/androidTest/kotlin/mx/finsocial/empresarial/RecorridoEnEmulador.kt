package mx.finsocial.empresarial

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El recorrido completo en un Android de verdad (emulador de GitHub Actions),
 * con datos simulados y la huella simulada (el emulador no tiene huella).
 * Las fotos van a la carpeta de «salida adicional» de las pruebas, que
 * Gradle baja sola a app/build/outputs/connected_android_test_additional_output.
 *
 * Se arma con: -Pfinso.huellaSimulada=true -Pfinso.capturas=true
 */
@RunWith(AndroidJUnit4::class)
class RecorridoEnEmulador {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val carpeta: File by lazy {
        val dada = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        @Suppress("DEPRECATION")
        val base = dada?.let(::File) ?: File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "additional_test_output")
        base.apply { mkdirs() }
    }

    private fun recorrido() = Recorrido(compose, espera = 20_000) { nombre ->
        // Foto de la pantalla entera (incluye hojas, diálogos y teclado),
        // como la vería la persona.
        compose.waitForIdle()
        val img = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return@Recorrido
        File(carpeta, "emulador-$nombre.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun primerIngresoYDesbloqueo() {
        assumeTrue("Solo con la huella simulada", BuildConfig.HUELLA_SIMULADA)
        val r = recorrido().primerIngreso("ana@empresa.mx", codigoEquivocado = true, fotos = true)
        r.tocarEtiqueta("activar").esperar("Hola, Ana").foto("07-dentro")
        r.tocarEtiqueta("ajustes").esperar("Apariencia").foto("08-ajustes")
        r.tocarEtiqueta("salir")
        // Al volver, la huella se pide sola y entra.
        r.esperarEtiqueta("saludo").esperarEtiqueta("saldo").foto("09-de-vuelta")

        // Pedir un SPEI (queda por liberar).
        r.pedirSpei("dst_SIM_PAPELERA", "1500", "Pago diseño año", fotos = true)
        r.tocarEtiqueta("pedir").esperar("Listo, falta liberarlo").foto("10-spei-pedido")
        r.tocarEtiqueta("listo").esperarEtiqueta("por-liberar")

        // Liberar el de Luis: pasa a «Enviando…» y en unos segundos a «SPEI enviado».
        r.tocarEtiqueta("por-liberar").tocarEtiqueta("orden-spo_SIM_PENDIENTE").esperar("pidió mandar").foto("11-por-liberar")
        r.tocarEtiqueta("liberar").esperar("SPEI enviado").foto("12-spei-enviado")
        r.tocarEtiqueta("listo").esperarEtiqueta("saludo").foto("13-inicio")
    }
}
