package mx.finsocial.empresarial.ui.piezas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Movimiento
import mx.finsocial.empresarial.ui.tema.Radios

/**
 * La acción principal de cada pantalla: menta con letra tinta, en claro y en
 * oscuro (regla de marca). Al tocarlo se encoge a 0.97 con un leve resorte.
 * Mientras [cargando] muestra un indicador y no se puede volver a tocar
 * (evita dobles envíos).
 */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    icono: ImageVector? = null,
) {
    val c = Finso.colores
    val vista = LocalView.current
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "escala del botón",
    )
    Button(
        onClick = {
            if (!cargando) {
                Hapticos.ligero(vista)
                alTocar()
            }
        },
        enabled = habilitado,
        interactionSource = fuente,
        shape = RoundedCornerShape(Radios.grande),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accion,
            contentColor = c.sobreAccion,
            disabledContainerColor = c.accion.copy(alpha = .2f),
            disabledContentColor = c.sobreAccion.copy(alpha = .5f).let { if (c.esOscuro) c.texto.copy(alpha = .5f) else it },
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .semantics { if (cargando) contentDescription = "$texto, cargando" },
    ) {
        AnimatedContent(
            targetState = cargando,
            transitionSpec = { fadeIn(tween(Movimiento.RAPIDO)) togetherWith fadeOut(tween(Movimiento.RAPIDO)) },
            contentAlignment = Alignment.Center,
            label = "cargando",
        ) { estaCargando ->
            if (estaCargando) {
                CircularProgressIndicator(color = c.sobreAccion, strokeWidth = 2.4.dp, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icono != null) {
                        Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(texto, style = Estilos.boton, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
