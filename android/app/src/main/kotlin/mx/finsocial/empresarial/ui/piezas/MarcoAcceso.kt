package mx.finsocial.empresarial.ui.piezas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso

/**
 * El armazón de cada paso del acceso (como las capturas de referencia):
 * «finso» arriba, la etiqueta del paso en menta, el título, la explicación,
 * el contenido y la acción principal pegada abajo (sube con el teclado).
 */
@Composable
fun MarcoAcceso(
    etiqueta: String,
    titulo: String,
    explicacion: String?,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    abajo: @Composable ColumnScope.() -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = Finso.colores
    Box(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = Espacios.columnaMaxima)
                .fillMaxSize()
                .padding(horizontal = Espacios.lateral),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver, modifier = Modifier.align(Alignment.CenterStart).padding(end = 8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = c.texto)
                        }
                        MarcaFinso(ancho = 84.dp, modifier = Modifier.align(Alignment.Center))
                    } else {
                        MarcaFinso(ancho = 84.dp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(etiqueta.uppercase(), style = Estilos.sobretitulo, color = c.acento)
                Spacer(Modifier.height(10.dp))
                Text(titulo, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                if (explicacion != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(explicacion, style = MaterialTheme.typography.bodyLarge, color = c.secundario)
                }
                Spacer(Modifier.height(28.dp))
                contenido()
                Spacer(Modifier.height(24.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                content = abajo,
            )
        }
    }
}
