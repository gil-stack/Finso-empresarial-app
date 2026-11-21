package mx.finsocial.empresarial.pantallas

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.estado.EstadoSesion
import mx.finsocial.empresarial.pantallas.dentro.Dentro
import mx.finsocial.empresarial.pantallas.acceso.Desbloqueo
import mx.finsocial.empresarial.pantallas.acceso.FlujoAcceso
import mx.finsocial.empresarial.ui.piezas.BaseAcceso
import mx.finsocial.empresarial.ui.tema.Apariencia
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.FinsoTema
import mx.finsocial.empresarial.ui.tema.Movimiento

/**
 * La raíz de la app: según el estado de la sesión muestra la bienvenida, el
 * primer ingreso, el desbloqueo, elegir empresa o el inicio. Al cambiar de
 * estado todo lo de encima se cierra con un fundido suave (especificación §3).
 */
@Composable
fun Raiz(contenedor: Contenedor) {
    val estado by contenedor.sesion.estado.collectAsState()
    val apariencia by contenedor.apariencia.collectAsState()
    LaunchedEffect(Unit) { contenedor.sesion.arrancar() }

    val oscuro = when (apariencia) {
        Apariencia.SISTEMA -> isSystemInDarkTheme()
        Apariencia.CLARO -> false
        Apariencia.OSCURO -> true
    }
    val enAcceso = estado !is EstadoSesion.Dentro && estado !is EstadoSesion.ElegirEmpresa

    // Íconos de la barra de estado: claros sobre el acceso y el modo oscuro.
    val vista = LocalView.current
    SideEffect {
        val ventana = (vista.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(ventana, vista).apply {
            isAppearanceLightStatusBars = !(enAcceso || oscuro)
            isAppearanceLightNavigationBars = !(enAcceso || oscuro)
        }
    }

    FinsoTema(oscuro = oscuro) {
        Box(Modifier.fillMaxSize().background(if (enAcceso) BaseAcceso else Finso.colores.fondo)) {
            AnimatedContent(
                targetState = estado,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(Movimiento.LENTO, easing = Movimiento.curva)) togetherWith fadeOut(tween(Movimiento.NORMAL)) },
                label = "estado de la sesión",
            ) { e ->
                when (e) {
                    EstadoSesion.Arrancando -> Box(Modifier.fillMaxSize().background(BaseAcceso))
                    is EstadoSesion.Fuera -> Fuera(contenedor, e.aviso)
                    is EstadoSesion.Bloqueada -> Desbloqueo(contenedor, e.nombre)
                    is EstadoSesion.ElegirEmpresa -> ElegirEmpresa(contenedor, e.usuario)
                    is EstadoSesion.Dentro -> Dentro(contenedor, e.usuario)
                }
            }
        }
    }
}

/** Bienvenida y, al tocar «Entrar», el primer ingreso. */
@Composable
private fun Fuera(contenedor: Contenedor, aviso: String?) {
    var enFlujo by rememberSaveable { mutableStateOf(false) }
    Crossfade(targetState = enFlujo, animationSpec = tween(Movimiento.NORMAL), label = "bienvenida") { flujo ->
        if (flujo) {
            FlujoAcceso(contenedor, alSalir = { enFlujo = false })
        } else {
            Bienvenida(alEntrar = { enFlujo = true }, aviso = aviso, simulada = contenedor.simulada)
        }
    }
}
