package mx.finsocial.empresarial

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        File(carpeta, "emulador-$nombre.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun primerIngresoYDesbloqueo() {
        assumeTrue("Solo con la huella simulada", BuildConfig.HUELLA_SIMULADA)
        val r = recorrido().primerIngreso("ana@empresa.mx", codigoEquivocado = true, fotos = true)
        r.tocarEtiqueta("activar").esperar("Hola, Ana").foto("07-dentro")
        r.tocarEtiqueta("ajustes").esperar("Apariencia").foto("08-ajustes")
        r.tocarEtiqueta("salir")
        // Al volver, la huella se pide sola y entra.
        r.esperarEtiqueta("saludo").foto("09-de-vuelta")
    }
}
