package mx.finsocial.empresarial.pantallas.dentro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.pantallas.spei.MandarSpei
import mx.finsocial.empresarial.pantallas.spei.PorLiberar
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Movimiento

/** Las pantallas de adentro. Inicio es la base; lo demás entra desde la derecha. */
sealed interface Vista {
    data object Inicio : Vista
    data object Mandar : Vista
    data object PorLiberar : Vista
}

@Composable
fun Dentro(contenedor: Contenedor, usuario: Usuario) {
    // Si cambia la empresa o los permisos, todo se vuelve a leer.
    val datos = remember(usuario.empresa?.id, usuario.permisos) { DatosDeInicio(contenedor, usuario) }
    var vista by remember(datos) { mutableStateOf<Vista>(Vista.Inicio) }
    val alcance = rememberCoroutineScope()
    val reducir = LocalReducirMovimiento.current

    LaunchedEffect(datos) { datos.refrescar() }

    val volverAInicio: () -> Unit = {
        vista = Vista.Inicio
        alcance.launch { datos.refrescar() }
    }
    BackHandler(enabled = vista != Vista.Inicio, onBack = volverAInicio)

    AnimatedContent(
        targetState = vista,
        transitionSpec = {
            val adelante = targetState != Vista.Inicio
            val d = if (reducir) 0 else 1
            (fadeIn(tween(Movimiento.NORMAL)) + slideInHorizontally(tween(Movimiento.NORMAL, easing = Movimiento.curva)) { (if (adelante) it / 5 else -it / 8) * d })
                .togetherWith(fadeOut(tween(Movimiento.RAPIDO)) + slideOutHorizontally(tween(Movimiento.NORMAL, easing = Movimiento.curva)) { (if (adelante) -it / 8 else it / 5) * d })
        },
        label = "pantallas de adentro",
    ) { v ->
        when (v) {
            Vista.Inicio -> Inicio(
                contenedor = contenedor,
                datos = datos,
                alMandar = { vista = Vista.Mandar },
                alPorLiberar = { vista = Vista.PorLiberar },
            )
            Vista.Mandar -> MandarSpei(contenedor, datos, alTerminar = volverAInicio)
            Vista.PorLiberar -> PorLiberar(contenedor, datos, alTerminar = volverAInicio)
        }
    }
}
