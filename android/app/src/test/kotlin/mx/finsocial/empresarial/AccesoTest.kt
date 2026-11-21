package mx.finsocial.empresarial

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import mx.finsocial.empresarial.estado.EstadoSesion
import mx.finsocial.empresarial.nucleo.Textos
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.pantallas.Raiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Casos 9 a 15 de la especificación compartida. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Capturas.TELEFONO)
class AccesoTest {
    @get:Rule val compose = createComposeRule()
    private val app = AppDePrueba()

    private fun abrir(): Recorrido {
        compose.setContent { Raiz(app.contenedor) }
        return Recorrido(compose) { nombre -> compose.onRoot().captureRoboImage(Capturas.ruta("acceso-$nombre")) }
    }

    @Test fun `9 - primer ingreso completo`() {
        val r = abrir().primerIngreso("ana@empresa.mx", codigoEquivocado = true, fotos = true)
        // «PASO k DE N»: correo 2 de 4, SMS 3 de 4.
        assertTrue(r.hay("Último paso"))
        r.tocarEtiqueta("activar").esperar("Hola, Ana").foto("07-dentro")
        assertNotNull(app.guardado.dispositivo)
        assertTrue(app.llave.firmados.single().startsWith("finso-movil:v1:registrar:"))
        assertEquals("Activar tu huella en Finso Empresarial", app.llave.motivos.single())
    }

    @Test fun `9b - los pasos dicen PASO 2 DE 4 y PASO 3 DE 4`() {
        val r = abrir()
        r.tocarEtiqueta("entrar").escribir("correo", "ana@empresa.mx").escribir("contrasena", "x").tocarEtiqueta("continuar")
        r.esperar("Paso 2 de 4")
        r.teclear("codigo", "123456").esperar("Paso 3 de 4")
    }

    @Test fun `10 - si cancela la huella no queda registrado y puede reintentar`() {
        val r = abrir().primerIngreso("ana@empresa.mx")
        app.llave.respuesta = FallaBiometria.Tipo.CANCELO
        r.tocarEtiqueta("activar").esperar(Textos.BIO_CANCELO).foto("10-cancelo")
        assertNull(app.guardado.dispositivo)
        assertTrue(!app.llave.existe())
        app.llave.respuesta = null
        r.tocarEtiqueta("activar").esperar("Hola, Ana")
        assertNotNull(app.guardado.dispositivo)
    }

    @Test fun `11 - sin celular lo captura despues del correo`() {
        val r = abrir()
        r.tocarEtiqueta("entrar").escribir("correo", "nuevo@empresa.mx").escribir("contrasena", "x").tocarEtiqueta("continuar")
        r.esperar("Paso 2 de 5").teclear("codigo", "123456")
        r.esperar("Tu celular").esperar("Paso 3 de 5")
        r.escribir("celular", "5512345678").tocar("Mandar código").esperar("Revisa tus mensajes").esperar("5678")
    }

    @Test fun `12 - varias empresas - elige y entra a la elegida`() {
        val r = abrir().primerIngreso("varias@empresa.mx")
        r.tocarEtiqueta("activar").esperar("Trabajas con varias empresas").foto("12-elegir-empresa")
        r.tocar("Transportes del Bajío").esperar("Hola, Luis")
        assertTrue(r.hay("Transportes del Bajío"))
        assertEquals("emp_SIM2", app.guardado.empresa)
    }

    @Test fun `13 - telefono registrado - sale y vuelve a entrar con huella`() {
        val r = abrir().primerIngreso("ana@empresa.mx")
        r.tocarEtiqueta("activar").esperar("Hola, Ana")
        app.llave.respuesta = FallaBiometria.Tipo.CANCELO
        compose.runOnIdle { app.contenedor.sesion.salir() }
        r.esperarEtiqueta("entrar-huella").foto("13-desbloqueo")
        app.llave.respuesta = null
        r.tocarEtiqueta("entrar-huella").esperarEtiqueta("saludo")
        assertTrue(app.llave.firmados.last().startsWith("finso-movil:v1:entrar:${app.guardado.dispositivo}:"))
        assertEquals("Entrar a Finso Empresarial", app.llave.motivos.last())
    }

    @Test fun `14 - si cambiaron las huellas olvida el telefono y vuelve con el aviso`() {
        val r = abrir().primerIngreso("ana@empresa.mx")
        r.tocarEtiqueta("activar").esperar("Hola, Ana")
        app.llave.respuesta = FallaBiometria.Tipo.INVALIDADA
        compose.runOnIdle { app.contenedor.sesion.salir() }
        r.esperar(Textos.BIO_INVALIDADA).esperarEtiqueta("entrar").foto("14-huellas-cambiaron")
        assertNull(app.guardado.dispositivo)
        assertTrue(app.contenedor.sesion.estado.value is EstadoSesion.Fuera)
    }

    @Test fun `15 - tras 5 minutos en segundo plano pide la huella, 4 no`() {
        val r = abrir().primerIngreso("ana@empresa.mx")
        r.tocarEtiqueta("activar").esperar("Hola, Ana")
        val sesion = app.contenedor.sesion
        compose.runOnIdle {
            sesion.alIrAlFondo()
            app.ahora = app.ahora.plus(Duration.ofMinutes(4))
            sesion.alVolver()
        }
        compose.waitForIdle()
        assertTrue(sesion.estado.value is EstadoSesion.Dentro)
        app.llave.respuesta = FallaBiometria.Tipo.CANCELO
        compose.runOnIdle {
            sesion.alIrAlFondo()
            app.ahora = app.ahora.plus(Duration.ofMinutes(5))
            sesion.alVolver()
        }
        r.esperarEtiqueta("entrar-huella")
        assertTrue(sesion.estado.value is EstadoSesion.Bloqueada)
    }

    @Test fun `ajustes - olvidar este telefono vuelve a la bienvenida`() {
        val r = abrir().primerIngreso("ana@empresa.mx")
        r.tocarEtiqueta("activar").esperar("Hola, Ana")
        r.tocarEtiqueta("ajustes").esperar("Apariencia").foto("16-ajustes")
        r.tocarEtiqueta("olvidar").esperar("¿Olvidar este teléfono?").tocar("Olvidar", contiene = false)
        r.esperarEtiqueta("entrar")
        assertNull(app.guardado.dispositivo)
    }
}
