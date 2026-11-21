package mx.finsocial.empresarial.ui.piezas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Radios
import mx.finsocial.empresarial.ui.tema.VerdeExito

/** Barra de arriba de las pantallas de adentro: flecha para volver y título. */
@Composable
fun Encabezado(titulo: String?, alVolver: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().height(56.dp)) {
        IconButton(onClick = alVolver) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = Finso.colores.texto)
        }
        if (titulo != null) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
        }
    }
}

/** Tarjeta sobre el fondo: renglones de dato y valor. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Finso.colores
    Column(
        modifier
            .fillMaxWidth()
            .background(c.tarjeta, RoundedCornerShape(Radios.tarjeta))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        content = contenido,
    )
}

/** Un renglón «Cuenta · BBVA •••• 4821» dentro de una [Tarjeta]. */
@Composable
fun Renglon(dato: String, valor: String, modifier: Modifier = Modifier, fuerte: Boolean = false, extra: @Composable (() -> Unit)? = null) {
    val c = Finso.colores
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(dato, style = MaterialTheme.typography.bodyMedium, color = c.secundario)
        Spacer(Modifier.width(16.dp))
        if (extra != null) {
            extra()
        } else {
            Text(
                valor,
                style = if (fuerte) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (fuerte) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.End,
                color = c.texto,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** La inicial en un círculo de color de marca (renglones de movimientos y destinos). */
@Composable
fun Inicial(nombre: String, modifier: Modifier = Modifier, tamano: Dp = 44.dp, atenuado: Boolean = false) {
    val c = Finso.colores
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(tamano)
            .alpha(if (atenuado) .5f else 1f)
            .background(c.positivoFondo, CircleShape),
    ) {
        Text(NombreLegible.inicial(nombre), style = MaterialTheme.typography.titleMedium, color = c.acento)
    }
}

/** Texto y colores de la insignia de estado de una orden (§3.4 paso 4). */
fun textoDeEstado(estado: String): String = when (estado) {
    EstadoOrden.POR_LIBERAR -> "Por liberar"
    EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> "Enviando"
    EstadoOrden.LIQUIDADA -> "Enviado"
    EstadoOrden.RECHAZADA -> "Rechazado"
    EstadoOrden.DEVUELTA -> "Devuelto"
    EstadoOrden.CANCELADA -> "Cancelado"
    else -> estado
}

@Composable
fun Insignia(estado: String, modifier: Modifier = Modifier) {
    val c = Finso.colores
    val (color, fondo) = when (estado) {
        EstadoOrden.LIQUIDADA -> c.positivo to c.positivoFondo
        EstadoOrden.POR_LIBERAR, EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> c.aviso to c.avisoFondo
        else -> c.peligro to c.peligroFondo
    }
    Text(
        textoDeEstado(estado),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.background(fondo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Bloque gris que late mientras carga (esqueleto). */
@Composable
fun Esqueleto(ancho: Dp, alto: Dp, modifier: Modifier = Modifier, redondo: Boolean = false) {
    val c = Finso.colores
    val reducir = LocalReducirMovimiento.current
    val latido = if (reducir) {
        .6f
    } else {
        val t = rememberInfiniteTransition(label = "esqueleto")
        val a by t.animateFloat(.35f, .75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "latido")
        a
    }
    Box(
        modifier
            .size(ancho, alto)
            .alpha(latido)
            .background(c.borde, if (redondo) CircleShape else RoundedCornerShape(8.dp)),
    )
}

/**
 * La palomita de éxito: un disco con el trazo verde que se dibuja (§10).
 * Sin texto; TalkBack dice [descripcion].
 */
@Composable
fun Palomita(modifier: Modifier = Modifier, tamano: Dp = 88.dp, descripcion: String = "Listo") {
    val c = Finso.colores
    val reducir = LocalReducirMovimiento.current
    val trazo = remember { Animatable(if (reducir) 1f else 0f) }
    LaunchedEffect(Unit) { trazo.animateTo(1f, tween(520, delayMillis = 120, easing = FastOutSlowInEasing)) }
    Canvas(modifier.size(tamano).semantics { contentDescription = descripcion }) {
        drawCircle(c.positivoFondo)
        val p = Path().apply {
            moveTo(size.width * .30f, size.height * .52f)
            lineTo(size.width * .44f, size.height * .66f)
            lineTo(size.width * .71f, size.height * .38f)
        }
        val medida = PathMeasure().apply { setPath(p, false) }
        val parcial = Path()
        medida.getSegment(0f, medida.length * trazo.value, parcial, true)
        drawPath(parcial, VerdeExito, style = Stroke(width = size.width * .075f, cap = StrokeCap.Round))
    }
}

/** Un importe grande (Gully, cifras tabulares). */
@Composable
fun Importe(texto: String, tamano: Int, modifier: Modifier = Modifier, color: Color = Finso.colores.texto) {
    Text(texto, style = Estilos.importe(tamano), color = color, modifier = modifier, maxLines = 1)
}
