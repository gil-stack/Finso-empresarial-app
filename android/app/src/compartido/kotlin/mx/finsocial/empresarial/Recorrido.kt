package mx.finsocial.empresarial

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement

/**
 * Pasos de una persona usando la app, para las pruebas de pantallas y el
 * recorrido en emulador. [capturar] guarda una foto de la pantalla con un nombre.
 */
class Recorrido(
    val compose: ComposeTestRule,
    private val espera: Long = 10_000,
    private val capturar: (String) -> Unit = {},
) {
    fun esperar(texto: String, contiene: Boolean = true): Recorrido {
        compose.waitUntil(espera) {
            compose.onAllNodesWithText(texto, substring = contiene, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        return this
    }

    fun esperarEtiqueta(tag: String): Recorrido {
        compose.waitUntil(espera) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return this
    }

    fun nodo(texto: String, contiene: Boolean = true): SemanticsNodeInteraction =
        compose.onAllNodesWithText(texto, substring = contiene, ignoreCase = true).onFirst()

    fun etiqueta(tag: String): SemanticsNodeInteraction = compose.onAllNodes(hasTestTag(tag)).onFirst()

    fun tocar(texto: String, contiene: Boolean = true): Recorrido {
        nodo(texto, contiene).performClick()
        compose.waitForIdle()
        return this
    }

    fun tocarEtiqueta(tag: String): Recorrido {
        esperarEtiqueta(tag)
        etiqueta(tag).performClick()
        compose.waitForIdle()
        return this
    }

    fun escribir(tag: String, texto: String): Recorrido {
        esperarEtiqueta(tag)
        compose.onAllNodes(campo(tag)).onFirst().performTextReplacement(texto)
        compose.waitForIdle()
        return this
    }

    fun teclear(tag: String, texto: String): Recorrido {
        esperarEtiqueta(tag)
        compose.onAllNodes(campo(tag)).onFirst().performTextInput(texto)
        compose.waitForIdle()
        return this
    }

    fun foto(nombre: String): Recorrido {
        compose.waitForIdle()
        capturar(nombre)
        return this
    }

    fun hay(texto: String): Boolean =
        compose.onAllNodes(hasText(texto, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

    // ───── Recorridos completos ─────

    /** Bienvenida → correo y contraseña → código del correo (con un error) → SMS → huella. */
    fun primerIngreso(correo: String, codigoEquivocado: Boolean = false, fotos: Boolean = false): Recorrido {
        esperarEtiqueta("entrar")
        if (fotos) foto("01-bienvenida")
        tocarEtiqueta("entrar")
        escribir("correo", correo)
        escribir("contrasena", "Secreta123")
        if (fotos) foto("02-correo")
        tocarEtiqueta("continuar")
        esperar("Revisa tu correo")
        if (codigoEquivocado) {
            teclear("codigo", "000000")
            esperar("Te quedan 4 intentos")
            if (fotos) foto("03-codigo-equivocado")
        }
        teclear("codigo", "123456")
        if (correo.startsWith("nuevo")) {
            esperar("Tu celular")
            escribir("celular", "5512345678")
            if (fotos) foto("04-celular")
            tocar("Mandar código")
        }
        esperar("Revisa tus mensajes")
        if (fotos) foto("05-codigo-sms")
        teclear("codigo", "123456")
        esperar("Activa tu huella")
        if (fotos) foto("06-activar-huella")
        return this
    }

    /** Primer ingreso completo hasta Inicio («Hola, …»). */
    fun entrarComo(correo: String, hola: String): Recorrido {
        primerIngreso(correo)
        tocarEtiqueta("activar")
        esperar("Hola, $hola")
        return this
    }

    /** Desde Inicio: elige el destino, teclea importe y concepto y llega a «Revisa tu SPEI». */
    fun pedirSpei(destino: String, importe: String, concepto: String, fotos: Boolean = false): Recorrido {
        tocarEtiqueta("mandar")
        esperarEtiqueta("destino-$destino")
        if (fotos) foto("spei-1-destinos")
        tocarEtiqueta("destino-$destino")
        esperarEtiqueta("importe")
        teclear("importe", importe)
        escribir("concepto", concepto)
        if (fotos) foto("spei-2-importe")
        tocarEtiqueta("revisar")
        esperar("Revisa tu SPEI")
        if (fotos) foto("spei-3-revisar")
        return this
    }
}

/** El campo de texto con esa etiqueta, o el que está dentro de algo con esa etiqueta. */
private fun campo(tag: String) =
    (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and hasSetTextAction()
