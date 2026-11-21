package mx.finsocial.empresarial.pantallas.dentro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.nucleo.avisos.Enlace
import mx.finsocial.empresarial.pantallas.spei.MarcoSpei
import mx.finsocial.empresarial.pantallas.spei.ResultadoDeOrden
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.pantallas.spei.MandarSpei
import mx.finsocial.empresarial.pantallas.spei.PorLiberar
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Movimiento

/** Las pantallas de adentro. Inicio es la base; lo demás entra desde la derecha. */
sealed interface Vista {
    data object Inicio : Vista
    data object Mandar : Vista
    data object PorLiberar : Vista

    /** Desde una notificación: cómo va un SPEI. */
    data class Orden(val ref: String) : Vista
}

/** Cada 15 s, mientras la app está al frente, se actualizan los SPEI que se siguen en vivo. */
private const val CADA_CUANTO_MS = 15_000L

@Composable
fun Dentro(contenedor: Contenedor, usuario: Usuario) {
    // Si cambia la empresa o los permisos, todo se vuelve a leer.
    val datos = remember(usuario.empresa?.id, usuario.permisos) { DatosDeInicio(contenedor, usuario) }
    var vista by remember(datos) { mutableStateOf<Vista>(Vista.Inicio) }
    val alcance = rememberCoroutineScope()
    val reducir = LocalReducirMovimiento.current

    LaunchedEffect(datos) { datos.refrescar() }

    // Ligas de la notificación, el widget y los accesos directos (solo si hay permiso).
    val enlace by contenedor.enlace.collectAsState()
    LaunchedEffect(enlace, datos) {
        val p = usuario.permisos
        when (val e = enlace) {
            is Enlace.Orden -> if (p.verOrdenes || p.pedirSpei || p.liberarSpei) vista = Vista.Orden(e.ref)
            Enlace.Mandar -> if (p.pedirSpei) vista = Vista.Mandar
            Enlace.PorLiberar -> if (p.liberarSpei) vista = Vista.PorLiberar
            null -> return@LaunchedEffect
        }
        contenedor.enlaceAtendido()
    }

    val ciclo = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(datos, ciclo) {
        ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                contenedor.sistema.ordenesEnCurso().forEach { ref ->
                    try { contenedor.sistema.alVerOrden(contenedor.api.orden(ref)) } catch (_: FallaApi) { /* a la siguiente */ }
                }
                delay(CADA_CUANTO_MS)
            }
        }
    }

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
            is Vista.Orden -> OrdenDesdeAviso(contenedor, v.ref, alListo = volverAInicio)
        }
    }
}

/** Abre la orden de la notificación: se pide al servidor con la sesión (la liga no trae datos). */
@Composable
private fun OrdenDesdeAviso(contenedor: Contenedor, ref: String, alListo: () -> Unit) {
    var orden by remember(ref) { mutableStateOf<Orden?>(null) }
    var falla by remember(ref) { mutableStateOf<String?>(null) }
    LaunchedEffect(ref) {
        try { orden = contenedor.api.orden(ref) } catch (e: FallaApi) { falla = e.texto }
    }
    val o = orden
    if (o != null) {
        ResultadoDeOrden(contenedor, o, alListo)
    } else {
        MarcoSpei(titulo = "SPEI", alVolver = alListo) {
            Box(Modifier.fillMaxWidth().height(240.dp).testTag("orden-cargando"), contentAlignment = Alignment.Center) {
                val f = falla
                if (f != null) Text(f, color = Finso.colores.secundario) else CircularProgressIndicator(color = Finso.colores.acento)
            }
        }
    }
}
