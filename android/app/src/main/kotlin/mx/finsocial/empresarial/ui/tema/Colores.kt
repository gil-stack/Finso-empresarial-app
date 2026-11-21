package mx.finsocial.empresarial.ui.tema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Marca Finso (especificación §4), la misma de finsocial-v2. */
val Tinta = Color(0xFF102D31)
val Menta = Color(0xFF1DBB9D)
val Lienzo = Color(0xFFF2F7F2)

/** Verde de la palomita de éxito (especificación §10 y §11). */
val VerdeExito = Color(0xFF49C095)

/** Colores de la app según el modo. Se leen con `Finso.colores`. */
@Immutable
data class Colores(
    val esOscuro: Boolean,
    val fondo: Color,
    val tarjeta: Color,
    val elevado: Color,
    val texto: Color,
    val secundario: Color,
    /** Letra y enlaces en el color de marca (menta oscurecido en claro para contrastar). */
    val acento: Color,
    val borde: Color,
    val sombra: Color,
    /** Relleno del botón principal: menta en los dos modos. */
    val accion: Color,
    /** Letra del botón principal: tinta en los dos modos. */
    val sobreAccion: Color,
    val saldoInicio: Color,
    val saldoFin: Color,
    val sobreSaldo: Color,
    val sobreSaldoSecundario: Color,
    val positivo: Color,
    val positivoFondo: Color,
    val aviso: Color,
    val avisoFondo: Color,
    val peligro: Color,
    val peligroFondo: Color,
)

val ColoresClaro = Colores(
    esOscuro = false,
    fondo = Lienzo,
    tarjeta = Color(0xFFFFFFFF),
    elevado = Color(0xFFDCF1E9),
    texto = Tinta,
    secundario = Color(0xFF4A6668),
    acento = Color(0xFF0A6F5D),
    borde = Color(0xFF63827D),
    sombra = Color(0x14102D31),
    accion = Menta,
    sobreAccion = Tinta,
    saldoInicio = Color(0xFF17494B),
    saldoFin = Tinta,
    sobreSaldo = Lienzo,
    sobreSaldoSecundario = Color(0xFFA9C4BF),
    positivo = Color(0xFF0A6F5D),
    positivoFondo = Color(0xFFDCF1E9),
    aviso = Color(0xFF8A5A00),
    avisoFondo = Color(0xFFFBF0D9),
    peligro = Color(0xFFB3261E),
    peligroFondo = Color(0xFFFBE6E4),
)

/** Oscuro casi negro con tinte verde. */
val ColoresOscuro = Colores(
    esOscuro = true,
    fondo = Color(0xFF04100F),
    tarjeta = Color(0xFF0C1C1D),
    elevado = Color(0xFF142628),
    texto = Lienzo,
    secundario = Color(0xFFA9C4BF),
    acento = Menta,
    borde = Color(0xFF5E8A86),
    sombra = Color(0x66061417),
    accion = Menta,
    sobreAccion = Tinta,
    saldoInicio = Color(0xFF162B2D),
    saldoFin = Color(0xFF0A1517),
    sobreSaldo = Lienzo,
    sobreSaldoSecundario = Color(0xFFA9C4BF),
    positivo = Menta,
    positivoFondo = Color(0xFF143A36),
    aviso = Color(0xFFF5B544),
    avisoFondo = Color(0xFF33291B),
    peligro = Color(0xFFFFB4AB),
    peligroFondo = Color(0xFF3A1A18),
)

val LocalColores = staticCompositionLocalOf { ColoresClaro }

/** Acceso corto a los tokens de Finso desde cualquier pantalla. */
object Finso {
    val colores: Colores
        @Composable @ReadOnlyComposable
        get() = LocalColores.current
}
