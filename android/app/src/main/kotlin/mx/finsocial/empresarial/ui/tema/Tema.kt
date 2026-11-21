package mx.finsocial.empresarial.ui.tema

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Cómo se ve la app: lo elige la persona en Ajustes. */
enum class Apariencia { SISTEMA, CLARO, OSCURO }

/** Radios de la plataforma Finso: superficies generosas. */
object Radios {
    val chico = 12.dp
    val medio = 16.dp
    val grande = 20.dp
    val tarjeta = 28.dp
}

/** Espacios: margen lateral 20 y columna de máximo 520 en tabletas. */
object Espacios {
    val lateral = 20.dp
    val columnaMaxima = 520.dp
}

/**
 * Tiempos y curvas: todas las pantallas usan estos para que la app se sienta
 * de una sola pieza (especificación §4, «Movimiento»).
 */
object Movimiento {
    const val RAPIDO = 180
    const val NORMAL = 320
    const val LENTO = 520
    const val ESCALON = 30
    val curva = CubicBezierEasing(0.33f, 1f, 0.68f, 1f) // easeOutCubic
    val curvaEntrada = CubicBezierEasing(0.65f, 0f, 0.35f, 1f) // easeInOutCubic
}

/** `true` si la persona pidió «quitar animaciones» en el teléfono: solo fundidos. */
val LocalReducirMovimiento = staticCompositionLocalOf { false }

@Composable
fun FinsoTema(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    val c = if (oscuro) ColoresOscuro else ColoresClaro
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = c.acento,
        onPrimary = if (oscuro) Tinta else Color.White,
        primaryContainer = c.accion,
        onPrimaryContainer = c.sobreAccion,
        secondaryContainer = c.elevado,
        onSecondaryContainer = c.texto,
        background = c.fondo,
        onBackground = c.texto,
        surface = c.tarjeta,
        onSurface = c.texto,
        onSurfaceVariant = c.secundario,
        surfaceContainerLowest = c.tarjeta,
        surfaceContainerLow = c.tarjeta,
        surfaceContainer = if (oscuro) c.elevado else c.tarjeta,
        surfaceContainerHigh = if (oscuro) c.elevado else c.tarjeta,
        surfaceContainerHighest = c.elevado,
        surfaceTint = Color.Transparent,
        outline = c.borde,
        outlineVariant = c.borde.copy(alpha = .25f),
        error = c.peligro,
        errorContainer = c.peligroFondo,
        inverseSurface = if (oscuro) c.elevado else Tinta,
        inverseOnSurface = if (oscuro) c.texto else Lienzo,
        inversePrimary = Menta,
        scrim = Color(0x99000000),
    )
    val contexto = LocalContext.current
    val reducir = remember(contexto) {
        runCatching {
            Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalColores provides c,
        LocalReducirMovimiento provides reducir,
    ) {
        MaterialTheme(
            colorScheme = esquema,
            typography = tipografiaFinso(c),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(Radios.chico),
                medium = RoundedCornerShape(Radios.medio),
                large = RoundedCornerShape(Radios.grande),
                extraLarge = RoundedCornerShape(Radios.tarjeta),
            ),
            content = contenido,
        )
    }
}
