package mx.finsocial.empresarial.ui.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mx.finsocial.empresarial.R

/** Gully: títulos, botones e importes. */
val Gully = FontFamily(
    Font(R.font.gully_medium, FontWeight.Medium),
    Font(R.font.gully_semibold, FontWeight.SemiBold),
    Font(R.font.gully_bold, FontWeight.Bold),
)

/** Wanted Sans Std: el texto corrido. */
val WantedSans = FontFamily(
    Font(R.font.wanted_regular, FontWeight.Normal),
    Font(R.font.wanted_medium, FontWeight.Medium),
    Font(R.font.wanted_semibold, FontWeight.SemiBold),
    Font(R.font.wanted_bold, FontWeight.Bold),
)

/** Escala tipográfica de la plataforma Finso (especificación §4): 30, 25, 17, 15, 14, 13. */
fun tipografiaFinso(c: Colores) = Typography(
    headlineLarge = TextStyle(fontFamily = Gully, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp, color = c.texto),
    headlineMedium = TextStyle(fontFamily = Gully, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp, color = c.texto),
    titleLarge = TextStyle(fontFamily = Gully, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp, color = c.texto),
    titleMedium = TextStyle(fontFamily = Gully, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp, color = c.texto),
    titleSmall = TextStyle(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = c.texto),
    bodyLarge = TextStyle(fontFamily = WantedSans, fontSize = 15.sp, lineHeight = 22.sp, color = c.texto),
    bodyMedium = TextStyle(fontFamily = WantedSans, fontSize = 14.sp, lineHeight = 1.45.em, color = c.texto),
    bodySmall = TextStyle(fontFamily = WantedSans, fontSize = 13.sp, lineHeight = 18.sp, color = c.secundario),
    labelLarge = Estilos.boton,
    labelMedium = TextStyle(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = WantedSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

object Estilos {
    val boton = TextStyle(fontFamily = Gully, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp)

    /** Importes: Gully Bold con cifras tabulares, para que no bailen al cambiar. */
    fun importe(tamano: Int) = TextStyle(
        fontFamily = Gully,
        fontWeight = FontWeight.Bold,
        fontSize = tamano.sp,
        lineHeight = (tamano * 1.15f).sp,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = "tnum",
    )

    /** La etiqueta en mayúsculas espaciadas de arriba de cada paso («PASO 2 DE 4»). */
    val sobretitulo = TextStyle(fontFamily = WantedSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 2.4.sp)
}
