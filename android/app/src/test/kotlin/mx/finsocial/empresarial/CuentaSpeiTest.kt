package mx.finsocial.empresarial

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.pantallas.Raiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Casos 16 a 21 de la especificación compartida: cuenta, pedir, liberar y rechazar SPEI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Capturas.TELEFONO)
class CuentaSpeiTest {
    @get:Rule val compose = createComposeRule()
    private val app = AppDePrueba()

    private fun abrir(): Recorrido {
        compose.setContent { Raiz(app.contenedor) }
        return Recorrido(compose) { nombre -> compose.onRoot().captureRoboImage(Capturas.ruta("cuenta-$nombre")) }
    }

    private fun etiquetaQueEmpieza(prefijo: String) = SemanticsMatcher("etiqueta que empieza con $prefijo") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefijo) == true
    }

    private fun hayEtiqueta(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test fun `16 - inicio muestra saldo, CLABE agrupada, movimientos y Mandar SPEI y el ojo oculta importes`() {
        val r = abrir().entrarComo("ana@empresa.mx", "Ana")
        r.esperarEtiqueta("saldo").esperar("6461 8053 5000 0048 21").esperarEtiqueta("mandar")
        assertTrue(compose.onAllNodes(hasTestTag("inicio")).fetchSemanticsNodes().isNotEmpty())
        r.esperar("Hoy")
        assertTrue(compose.onAllNodes(etiquetaQueEmpieza("movimiento-")).fetchSemanticsNodes().isNotEmpty())
        r.foto("16-inicio")
        r.tocarEtiqueta("ojo").esperar(Dinero.OCULTO).foto("16-oculto")
        assertTrue(r.hay("Saldo total ${Dinero.OCULTO}"))
        r.tocarEtiqueta("ojo")
        compose.onAllNodes(etiquetaQueEmpieza("movimiento-")).onFirst().performClick()
        compose.waitForIdle()
        r.esperarEtiqueta("detalle").foto("16-detalle")
        assertTrue(r.hay("Recibiste") || r.hay("Enviaste"))
    }

    @Test fun `17 - pedir un SPEI`() {
        val r = abrir().entrarComo("ana@empresa.mx", "Ana")
        r.tocarEtiqueta("mandar").esperarEtiqueta("destino-dst_SIM_EMPAQUES")
        r.esperar("se activa en 18 min").esperar("se paga desde Nómina")
        // El destino en espera y el colaborador no se pueden tocar.
        r.tocarEtiqueta("destino-dst_SIM_EMPAQUES").tocarEtiqueta("destino-dst_SIM_LUCIA")
        assertTrue(r.hay("¿A quién le mandas?"))
        r.foto("17-destinos")
        r.tocarEtiqueta("destino-dst_SIM_PAPELERA").esperarEtiqueta("importe")
        r.teclear("importe", "1500").escribir("concepto", "Pago diseño año")
        r.esperar("Pago diseno ano").esperar("15/35").foto("17-importe")
        r.tocarEtiqueta("revisar").esperar("Revisa tu SPEI").esperar("Sin comisión").esperar("otra persona de tu empresa").foto("17-revisar")
        r.tocarEtiqueta("pedir").esperar("Listo, falta liberarlo").esperarEtiqueta("referencia").foto("17-resultado")
        val texto = app.llave.firmados.last()
        assertTrue(texto.startsWith("finso-movil:v1:spei:${app.guardado.dispositivo}:"))
        assertTrue(texto.endsWith(":dst_SIM_PAPELERA:150000:Pago diseno ano"))
        r.tocarEtiqueta("listo").esperarEtiqueta("saludo")
    }

    @Test fun `18 - el solicitante no ve saldo ni movimientos ni por liberar pero pide SPEI`() {
        val r = abrir().entrarComo("luis@empresa.mx", "Luis")
        r.esperar("no ve el saldo ni los movimientos").esperarEtiqueta("mandar").foto("18-solicitante")
        assertFalse(hayEtiqueta("saldo"))
        assertFalse(hayEtiqueta("por-liberar"))
        assertTrue(compose.onAllNodes(etiquetaQueEmpieza("movimiento-")).fetchSemanticsNodes().isEmpty())
        r.pedirSpei("dst_SIM_FLETES", "250", "Flete noviembre")
        r.tocarEtiqueta("pedir").esperar("Listo, falta liberarlo")
    }

    @Test fun `19 - un importe mayor al disponible no deja continuar`() {
        val r = abrir().entrarComo("ana@empresa.mx", "Ana")
        r.tocarEtiqueta("mandar").tocarEtiqueta("destino-dst_SIM_PAPELERA").esperarEtiqueta("importe")
        r.teclear("importe", "99999999").escribir("concepto", "Prueba")
        r.esperar("Pasa tu disponible de").foto("19-se-pasa")
        compose.onNode(hasTestTag("revisar")).assertIsNotEnabled()
    }

    @Test fun `20 - liberar un SPEI de otra persona`() {
        val r = abrir().entrarComo("ana@empresa.mx", "Ana")
        r.esperarEtiqueta("por-liberar").foto("20-inicio-con-pendientes")
        r.tocarEtiqueta("por-liberar").esperarEtiqueta("orden-spo_SIM_PENDIENTE").esperar("pidió Luis").foto("20-lista")
        r.tocarEtiqueta("orden-spo_SIM_PENDIENTE").esperar("pidió mandar").foto("20-detalle")
        r.tocarEtiqueta("liberar").esperar("Enviando").esperar("Liberada por Ana López Garza").foto("20-enviando")
        assertTrue(app.llave.firmados.last().startsWith("finso-movil:v1:liberar:${app.guardado.dispositivo}:"))
        assertTrue(app.llave.firmados.last().endsWith(":spo_SIM_PENDIENTE:1845000"))
    }

    @Test fun `21 - rechazar pide motivo y firma rechazar`() {
        val r = abrir().entrarComo("ana@empresa.mx", "Ana")
        r.tocarEtiqueta("por-liberar").tocarEtiqueta("orden-spo_SIM_PENDIENTE").tocarEtiqueta("rechazar")
        r.esperar("¿Rechazar este SPEI?")
        compose.onNode(hasTestTag("confirmar-rechazo")).assertIsNotEnabled()
        r.escribir("motivo", "Factura duplicada").foto("21-motivo")
        r.tocarEtiqueta("confirmar-rechazo").esperar("SPEI rechazado").esperar("Factura duplicada").foto("21-rechazado")
        assertTrue(app.llave.firmados.last().startsWith("finso-movil:v1:rechazar:"))
        assertEquals("Rechazar SPEI de $18,450.00 a ${NombreLegible.de("FLETES RAPIDOS DEL NORTE")}", app.llave.motivos.last())
    }
}
