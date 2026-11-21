package mx.finsocial.empresarial.ui.piezas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Movimiento
import mx.finsocial.empresarial.ui.tema.Radios

/**
 * Seis casillas sobre un campo oculto (especificación §3.1, paso 3). Acepta
 * el autollenado del código de SMS y avisa al completar el sexto dígito.
 * Cada vez que cambia [sacudir], las casillas se sacuden (código equivocado).
 */
@Composable
fun CasillasCodigo(
    codigo: String,
    alCambiar: (String) -> Unit,
    alCompletar: (String) -> Unit,
    sacudir: Int,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Finso.colores
    val foco = remember { FocusRequester() }
    val sacudida = remember { Animatable(0f) }
    val paso = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    LaunchedEffect(sacudir) {
        if (sacudir > 0) {
            sacudida.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    paso at 50
                    -paso at 120
                    paso * .7f at 190
                    -paso * .5f at 260
                    paso * .25f at 330
                },
            )
        }
    }
    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = codigo,
            onValueChange = { nuevo ->
                val limpio = nuevo.filter { it.isDigit() }.take(6)
                alCambiar(limpio)
                if (limpio.length == 6 && codigo.length < 6) alCompletar(limpio)
            },
            enabled = habilitado,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .alpha(0.01f)
                .focusRequester(foco)
                .testTag("codigo")
                .semantics {
                    contentType = ContentType.SmsOtpCode
                    contentDescription = "Código de 6 dígitos"
                },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = sacudida.value },
        ) {
            repeat(6) { i ->
                val lleno = i < codigo.length
                val actual = i == codigo.length && habilitado
                val borde by animateColorAsState(
                    when {
                        actual -> c.acento
                        lleno -> c.borde
                        else -> c.borde.copy(alpha = .45f)
                    },
                    tween(Movimiento.RAPIDO),
                    label = "borde casilla",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.86f)
                        .heightIn(max = 64.dp)
                        .background(if (c.esOscuro) c.elevado.copy(alpha = .55f) else c.tarjeta, RoundedCornerShape(Radios.chico))
                        .border(if (actual) 1.5.dp else 1.dp, borde, RoundedCornerShape(Radios.chico)),
                ) {
                    if (lleno) Text(codigo[i].toString(), style = Estilos.importe(26), color = c.texto)
                }
            }
        }
    }
}
