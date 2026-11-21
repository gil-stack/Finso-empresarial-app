package mx.finsocial.empresarial.pantallas.dentro

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.api.Cuenta
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.piezas.Importe
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Radios
import java.time.Instant

/**
 * La tarjeta de saldo (§3.3): degradado de marca, saldo disponible que cuenta
 * desde 0 la primera vez, el ojo, saldo total y la CLABE con «Copiar».
 */
@Composable
fun TarjetaDeSaldo(
    cuenta: Cuenta,
    sinConexion: Boolean,
    ocultar: Boolean,
    alCambiarOjo: () -> Unit,
    animarDesdeCero: Boolean,
    alAnimar: () -> Unit,
) {
    val c = Finso.colores
    val reducir = LocalReducirMovimiento.current
    val fondo = if (c.esOscuro) {
        Brush.radialGradient(listOf(c.saldoInicio, c.saldoFin), center = Offset.Zero, radius = 1400f)
    } else {
        Brush.linearGradient(listOf(c.saldoInicio, c.saldoFin), start = Offset.Zero, end = Offset.Infinite)
    }
    val avance = remember { Animatable(if (animarDesdeCero && !reducir) 0f else 1f) }
    LaunchedEffect(cuenta.disponibleCentavos) {
        if (avance.value < 1f) avance.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        alAnimar()
    }
    val disponible = (cuenta.disponibleCentavos * avance.value).toLong()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radios.tarjeta))
            .background(fondo)
            .then(if (c.esOscuro) Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = .08f)), RoundedCornerShape(Radios.tarjeta)) else Modifier)
            .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 20.dp)
            .testTag("saldo"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saldo disponible", style = MaterialTheme.typography.titleSmall, color = c.sobreSaldoSecundario, modifier = Modifier.weight(1f))
            if (cuenta.bloqueada) {
                Text(
                    "Bloqueada",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.background(c.peligro, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            FilledTonalIconButton(
                onClick = alCambiarOjo,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = c.sobreSaldo),
                modifier = Modifier.size(40.dp).testTag("ojo"),
            ) {
                Icon(
                    if (ocultar) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (ocultar) "Mostrar importes" else "Ocultar importes",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedContent(
            targetState = ocultar,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "ojo",
        ) { oculto ->
            Importe(if (oculto) Dinero.OCULTO else Dinero.pesos(disponible), 40, color = c.sobreSaldo, modifier = Modifier.testTag("disponible"))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Saldo total ${if (ocultar) Dinero.OCULTO else Dinero.pesos(cuenta.saldoCentavos)}",
            style = MaterialTheme.typography.bodyMedium,
            color = c.sobreSaldoSecundario,
        )
        if (sinConexion) {
            val hora = runCatching { Fechas.hora(Instant.parse(cuenta.horaDelSaldo)) }.getOrNull()
            if (hora != null) Text("Actualizado a las $hora", style = MaterialTheme.typography.bodySmall, color = c.sobreSaldoSecundario)
        }
        Spacer(Modifier.height(18.dp))
        Clabe(cuenta.clabe)
    }
}

@Composable
private fun Clabe(clabe: String) {
    val c = Finso.colores
    val contexto = LocalContext.current
    val vista = LocalView.current
    var copiada by remember { mutableStateOf(false) }
    LaunchedEffect(copiada) {
        if (copiada) { delay(1800); copiada = false }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text("CLABE", style = Estilos.sobretitulo, color = c.sobreSaldoSecundario)
            Text(
                Dinero.clabeAgrupada(clabe),
                style = MaterialTheme.typography.titleMedium,
                color = c.sobreSaldo,
                modifier = Modifier.testTag("clabe").semantics { contentDescription = "CLABE ${clabe.toList().joinToString(" ")}" },
            )
        }
        FilledTonalIconButton(
            onClick = {
                contexto.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("CLABE", clabe))
                Hapticos.ligero(vista)
                copiada = true
            },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = c.sobreSaldo),
            modifier = Modifier.size(40.dp).testTag("copiar-clabe"),
        ) {
            AnimatedContent(targetState = copiada, label = "copiar") { listo ->
                Icon(
                    if (listo) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = if (listo) "Copiada" else "Copiar CLABE",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (copiada) Text("Copiada", style = MaterialTheme.typography.labelMedium, color = c.sobreSaldoSecundario, modifier = Modifier.padding(top = 4.dp))
}

