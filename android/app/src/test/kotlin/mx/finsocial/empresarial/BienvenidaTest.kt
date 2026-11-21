package mx.finsocial.empresarial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import mx.finsocial.empresarial.pantallas.Bienvenida
import mx.finsocial.empresarial.ui.tema.ColoresClaro
import mx.finsocial.empresarial.ui.tema.ColoresOscuro
import mx.finsocial.empresarial.ui.tema.Menta
import mx.finsocial.empresarial.ui.tema.Tinta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Caso 8 de la especificación compartida. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Capturas.TELEFONO)
class BienvenidaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `el boton principal es menta con letra tinta en claro y en oscuro`() {
        assertEquals(Menta, ColoresClaro.accion)
        assertEquals(Tinta, ColoresClaro.sobreAccion)
        assertEquals(Menta, ColoresOscuro.accion)
        assertEquals(Tinta, ColoresOscuro.sobreAccion)
    }

    @Test fun `la bienvenida se arma con el telefono en claro`() {
        var tocado = false
        compose.setContent { Bienvenida(alEntrar = { tocado = true }, simulada = true) }
        compose.onNodeWithText("Tu empresa, en tu bolsillo").assertIsDisplayed()
        compose.onNodeWithText("Modo de prueba: datos simulados").assertIsDisplayed()
        compose.onNodeWithTag("entrar").assertIsDisplayed().assertIsEnabled()
        compose.onRoot().captureRoboImage(Capturas.ruta("01-bienvenida-claro"))
        compose.onNodeWithTag("entrar").performClick()
        assertTrue(tocado)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `la bienvenida se arma con el telefono en oscuro y muestra el aviso`() {
        compose.setContent {
            Bienvenida(alEntrar = {}, aviso = "Este teléfono se dio de baja. Vuelve a registrarlo.", simulada = false)
        }
        compose.onNodeWithText("Este teléfono se dio de baja. Vuelve a registrarlo.").assertIsDisplayed()
        compose.onNodeWithTag("entrar").assertIsDisplayed()
        compose.onRoot().captureRoboImage(Capturas.ruta("02-bienvenida-oscuro-aviso"))
    }
}
