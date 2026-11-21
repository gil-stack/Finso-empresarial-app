package mx.finsocial.empresarial.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.finsocial.empresarial.Entorno
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.FondoAcceso
import mx.finsocial.empresarial.ui.piezas.MarcaFinso
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Menta

/**
 * Primera pantalla: la marca y la entrada (especificación §3.1, paso 1).
 * Siempre sobre el fondo oscuro de acceso, como finsocial-v2.
 *
 * [aviso]: un mensaje que se muestra una vez (p. ej. «Este teléfono se dio de baja»).
 */
@Composable
fun Bienvenida(alEntrar: () -> Unit, aviso: String? = null, simulada: Boolean = Entorno.usarSimulada) {
    FondoAcceso {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = Espacios.columnaMaxima)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Espacios.lateral),
        ) {
            Spacer(Modifier.weight(3f))
            Aparicion { MarcaFinso(ancho = 168.dp) }
            Spacer(Modifier.height(14.dp))
            Aparicion(retraso = 80) {
                Text("EMPRESARIAL", style = Estilos.sobretitulo.copy(letterSpacing = 4.2.sp), color = Menta.copy(alpha = .9f))
            }
            Spacer(Modifier.weight(2f))
            Aparicion(retraso = 160) {
                Text(
                    "Tu empresa, en tu bolsillo",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Aparicion(retraso = 220) {
                Text(
                    "Consulta tu saldo, revisa movimientos y manda SPEI a tus cuentas registradas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Finso.colores.secundario,
                    textAlign = TextAlign.Center,
                )
            }
            if (aviso != null) {
                Spacer(Modifier.height(16.dp))
                Aparicion(retraso = 260) {
                    Text(aviso, style = MaterialTheme.typography.bodyMedium, color = Finso.colores.aviso, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(3f))
            Aparicion(retraso = 300, modifier = Modifier.fillMaxWidth()) {
                BotonPrincipal(texto = "Entrar", alTocar = alEntrar, modifier = Modifier.testTag("entrar"))
            }
            Spacer(Modifier.height(12.dp))
            if (simulada) {
                Text("Modo de prueba: datos simulados", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
