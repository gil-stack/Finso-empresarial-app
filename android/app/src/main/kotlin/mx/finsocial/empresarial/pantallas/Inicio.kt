package mx.finsocial.empresarial.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Finso

/**
 * Inicio (especificación §3.3): saludo, empresa y ajustes. El saldo, los
 * movimientos y el SPEI se agregan aquí con la cuenta.
 */
@Composable
fun Inicio(contenedor: Contenedor, usuario: Usuario) {
    var ajustes by remember { mutableStateOf(false) }
    val c = Finso.colores
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacios.lateral),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).widthIn(max = Espacios.columnaMaxima)) {
            Column(Modifier.weight(1f)) {
                Aparicion {
                    Text(
                        "Hola, ${NombreLegible.corto(usuario.nombre)}",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() }.testTag("saludo"),
                    )
                }
                usuario.empresa?.let {
                    Aparicion(retraso = 60) { Text(it.nombre, style = MaterialTheme.typography.bodyLarge, color = c.secundario) }
                }
            }
            FilledTonalIconButton(
                onClick = { ajustes = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.elevado, contentColor = c.texto),
                modifier = Modifier.testTag("ajustes"),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Ajustes")
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tarjeta de saldo, «Mandar SPEI», «Por liberar» y movimientos: siguiente PR.
        }
    }
    if (ajustes) Ajustes(contenedor, usuario, alCerrar = { ajustes = false })
}
