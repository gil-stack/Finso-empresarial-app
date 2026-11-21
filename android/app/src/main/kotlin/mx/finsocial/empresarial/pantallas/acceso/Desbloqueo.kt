package mx.finsocial.empresarial.pantallas.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.FondoAcceso
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.piezas.MarcaFinso
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Finso

/**
 * Ingresos siguientes (especificación §3.1, paso 5): saluda y pide la huella
 * sola al abrir. «Entrar con otra cuenta» olvida este teléfono.
 */
@Composable
fun Desbloqueo(contenedor: Contenedor, nombre: String?, pedirAlAbrir: Boolean = true) {
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmarOtra by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val vista = LocalView.current

    fun entrar() {
        if (cargando) return
        cargando = true
        error = null
        alcance.launch {
            try {
                contenedor.sesion.entrarConHuella()
                Hapticos.exito(vista)
            } catch (e: FallaBiometria) {
                // Cancelar no es un error: solo se queda en la pantalla.
                if (e.tipo != FallaBiometria.Tipo.CANCELO) error = textoDeBiometria(e)
            } catch (e: FallaApi) {
                Hapticos.error(vista)
                error = e.texto
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(Unit) { if (pedirAlAbrir) entrar() }

    FondoAcceso {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = Espacios.columnaMaxima)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Espacios.lateral),
        ) {
            Spacer(Modifier.weight(1f))
            Aparicion { MarcaFinso(ancho = 150.dp) }
            Spacer(Modifier.height(36.dp))
            Aparicion(retraso = 80) {
                Text(
                    if (nombre.isNullOrBlank()) "Hola de nuevo" else "Hola, $nombre",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(8.dp))
            Aparicion(retraso = 140) {
                Text("Entra con tu huella.", style = MaterialTheme.typography.bodyLarge, color = Finso.colores.secundario)
            }
            AvisoEnLinea(error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(1.4f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                BotonPrincipal("Entrar con huella", ::entrar, cargando = cargando, icono = Icons.Rounded.Fingerprint, modifier = Modifier.testTag("entrar-huella"))
                TextButton(onClick = { confirmarOtra = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Entrar con otra cuenta", style = MaterialTheme.typography.titleSmall, color = Finso.colores.texto)
                }
            }
        }

        if (confirmarOtra) {
            AlertDialog(
                onDismissRequest = { confirmarOtra = false },
                title = { Text("¿Entrar con otra cuenta?") },
                text = {
                    Text("Este teléfono se olvidará de tu cuenta. Para volver a usarla tendrás que entrar con tu correo y un código por SMS.")
                },
                confirmButton = {
                    TextButton(onClick = { confirmarOtra = false; contenedor.sesion.olvidarTelefono() }) {
                        Text("Sí, otra cuenta", color = Finso.colores.peligro)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmarOtra = false }) { Text("Cancelar") } },
            )
        }
    }
}
