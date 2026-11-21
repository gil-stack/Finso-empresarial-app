package mx.finsocial.empresarial.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.Entorno
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.tema.Apariencia
import mx.finsocial.empresarial.ui.tema.Finso

/** Ajustes en hoja desde abajo (especificación §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ajustes(contenedor: Contenedor, usuario: Usuario, alCerrar: () -> Unit) {
    val c = Finso.colores
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apariencia by contenedor.apariencia.collectAsState()
    var confirmarOlvidar by remember { mutableStateOf(false) }
    val vista = LocalView.current
    val filas = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = hoja, containerColor = if (c.esOscuro) c.elevado else c.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(20.dp))
            Text("Apariencia", style = MaterialTheme.typography.titleSmall, color = c.secundario, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            val opciones = listOf(Apariencia.SISTEMA to "Sistema", Apariencia.CLARO to "Claro", Apariencia.OSCURO to "Oscuro")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                opciones.forEachIndexed { i, (valor, texto) ->
                    SegmentedButton(
                        selected = apariencia == valor,
                        onClick = { Hapticos.ligero(vista); contenedor.cambiarApariencia(valor) },
                        shape = SegmentedButtonDefaults.itemShape(i, opciones.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = c.accion,
                            activeContentColor = c.sobreAccion,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = c.texto,
                            activeBorderColor = c.accion,
                            inactiveBorderColor = c.borde.copy(alpha = .5f),
                        ),
                        icon = {},
                        label = { Text(texto) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (usuario.empresas.size > 1) {
                ListItem(
                    headlineContent = { Text("Cambiar de empresa") },
                    supportingContent = { usuario.empresa?.let { Text(it.nombre) } },
                    leadingContent = { Icon(Icons.Rounded.Business, null, tint = c.acento) },
                    colors = filas,
                    modifier = Modifier.clickableFila { alCerrar(); contenedor.sesion.cambiarEmpresa() },
                )
            }
            ListItem(
                headlineContent = { Text("Salir") },
                supportingContent = { Text("La próxima vez entras con tu huella.") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = c.acento) },
                colors = filas,
                modifier = Modifier.testTag("salir").clickableFila { alCerrar(); contenedor.sesion.salir() },
            )
            ListItem(
                headlineContent = { Text("Olvidar este teléfono", color = c.peligro) },
                supportingContent = { Text("Para volver a entrar se necesitan correo, contraseña y los códigos.") },
                leadingContent = { Icon(Icons.Rounded.PhonelinkErase, null, tint = c.peligro) },
                colors = filas,
                modifier = Modifier.testTag("olvidar").clickableFila { confirmarOlvidar = true },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                Entorno.versionVisible,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmarOlvidar) {
        AlertDialog(
            onDismissRequest = { confirmarOlvidar = false },
            title = { Text("¿Olvidar este teléfono?") },
            text = { Text("Para volver a entrar se necesitan correo, contraseña y los códigos.") },
            confirmButton = {
                TextButton(onClick = { confirmarOlvidar = false; alCerrar(); contenedor.sesion.olvidarTelefono() }) {
                    Text("Olvidar", color = c.peligro)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarOlvidar = false }) { Text("Cancelar") } },
        )
    }
}

private fun Modifier.clickableFila(alTocar: () -> Unit): Modifier = this.clickable(onClick = alTocar)
