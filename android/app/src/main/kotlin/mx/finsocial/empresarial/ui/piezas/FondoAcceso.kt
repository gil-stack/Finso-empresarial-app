package mx.finsocial.empresarial.ui.piezas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.R
import mx.finsocial.empresarial.ui.tema.FinsoTema

/** La parada más oscura del fondo: si parpadea un cuadro, que parpadee hacia el negro. */
val BaseAcceso = Color(0xFF061518)

/**
 * Fondo del flujo de acceso, igual al de finsocial-v2 (especificación §3.1):
 * degradado de petróleo, halo menta tenue bajo la marca, viñeta abajo para que
 * el botón menta quede sobre lo más oscuro y el patrón Finso apenas visible.
 * Siempre en oscuro, aunque el teléfono esté en claro.
 */
@Composable
fun FondoAcceso(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    FinsoTema(oscuro = true) {
        Box(
            modifier
                .fillMaxSize()
                .background(BaseAcceso)
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            0f to Color(0xFF123035), 0.38f to Color(0xFF0C2327), 1f to BaseAcceso,
                            start = Offset.Zero, end = Offset(size.width, size.height),
                        ),
                    )
                    // Halo centrado al 28 % de la altura: lo que se ilumina es la marca.
                    drawRect(
                        Brush.radialGradient(
                            0f to Color(0x6B2A6B73), 0.45f to Color(0x3817474E), 1f to Color(0x00061518),
                            center = Offset(size.width / 2f, size.height * 0.28f),
                            radius = size.maxDimension * 0.8f,
                        ),
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color(0x0003090A), 0.55f to Color(0x8C03090A), 1f to Color(0xF003090A),
                            startY = size.height * 0.36f, endY = size.height,
                        ),
                    )
                },
        ) {
            Image(
                painter = painterResource(R.drawable.patron_finso),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color(0xFF2CB397), BlendMode.SrcIn),
                modifier = Modifier.fillMaxSize().alpha(0.04f),
            )
            contenido()
        }
    }
}

/** Logotipo «finso» (blanco por omisión; se tiñe con [color]). */
@Composable
fun MarcaFinso(ancho: Dp = 150.dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.finso_marca),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .width(ancho)
            .aspectRatio(900f / 302f)
            .semantics { contentDescription = "Finso" },
    )
}
