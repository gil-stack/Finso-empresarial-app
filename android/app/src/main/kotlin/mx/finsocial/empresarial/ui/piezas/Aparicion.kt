package mx.finsocial.empresarial.ui.piezas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Movimiento

/**
 * Aparece subiendo 8 dp con fundido, después de [retraso] ms. Así entran las
 * piezas de cada pantalla y los renglones de las listas (escalonados 30 ms).
 * Con «quitar animaciones» del teléfono, solo el fundido.
 */
@Composable
fun Aparicion(retraso: Int = 0, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    val reducir = LocalReducirMovimiento.current
    val avance = remember { Animatable(0f) }
    val desplazamiento = with(LocalDensity.current) { 8.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(retraso.toLong())
        avance.animateTo(1f, tween(Movimiento.NORMAL + 80, easing = Movimiento.curva))
    }
    Box(
        modifier.graphicsLayer {
            alpha = avance.value
            translationY = if (reducir) 0f else (1f - avance.value) * desplazamiento
        },
    ) { contenido() }
}
