package mx.finsocial.empresarial.ui.piezas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Movimiento
import mx.finsocial.empresarial.ui.tema.Radios

enum class TipoAviso { ERROR, ESPERA, INFO }

/**
 * Aviso dentro de la pantalla (error rojo bajo los campos, espera ámbar,
 * información). Aparece y desaparece deslizándose; TalkBack lo lee solo.
 */
@Composable
fun AvisoEnLinea(texto: String?, tipo: TipoAviso = TipoAviso.ERROR, modifier: Modifier = Modifier) {
    // Conserva el último texto mientras se va, para que no se vacíe al desaparecer.
    var ultimo by remember { mutableStateOf(texto) }
    if (texto != null) ultimo = texto
    AnimatedVisibility(
        visible = texto != null,
        enter = fadeIn(tween(Movimiento.NORMAL)) + expandVertically(tween(Movimiento.NORMAL, easing = Movimiento.curva)),
        exit = fadeOut(tween(Movimiento.RAPIDO)) + shrinkVertically(tween(Movimiento.NORMAL)),
        modifier = modifier,
    ) {
        val c = Finso.colores
        val (color, fondo, icono: ImageVector) = when (tipo) {
            TipoAviso.ERROR -> Triple(c.peligro, c.peligroFondo, Icons.Rounded.ErrorOutline)
            TipoAviso.ESPERA -> Triple(c.aviso, c.avisoFondo, Icons.Rounded.HourglassTop)
            TipoAviso.INFO -> Triple(c.acento, c.positivoFondo, Icons.Rounded.Info)
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(fondo, RoundedCornerShape(Radios.medio))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(ultimo.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}
