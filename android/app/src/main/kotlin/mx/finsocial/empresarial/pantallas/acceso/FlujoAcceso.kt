package mx.finsocial.empresarial.pantallas.acceso

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.api.Paso
import mx.finsocial.empresarial.nucleo.api.RetoDeEntrada
import mx.finsocial.empresarial.ui.piezas.FondoAcceso
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Movimiento
import java.time.Instant

/** Las pantallas del primer ingreso, en el orden que manda el servidor. */
sealed interface PantallaAcceso {
    data object Correo : PantallaAcceso
    data class Codigo(val paso: String) : PantallaAcceso
    data object Celular : PantallaAcceso
    data object Activar : PantallaAcceso
}

/**
 * Lo que se va sabiendo durante el primer ingreso: el reto, los pasos que
 * faltan, a dónde se mandaron los códigos y cuándo se puede pedir otro.
 */
class Ingreso(val reto: RetoDeEntrada) {
    /** N: pasos del servidor + correo y contraseña + activar la huella. No cambia a mitad del camino. */
    val total = reto.pasos.size + 2
    var pendientes by mutableStateOf(reto.pasos)
    var celular by mutableStateOf(reto.celularEnmascarado)
    var reenvioEn by mutableStateOf(reto.reenvioDisponibleEn?.let { runCatching { Instant.parse(it) }.getOrNull() })

    /** k = N − pasos pendientes. */
    val numeroDePaso: Int get() = total - pendientes.size

    fun siguiente(): PantallaAcceso = when (pendientes.firstOrNull()) {
        null -> PantallaAcceso.Activar
        Paso.CAPTURAR_CELULAR -> PantallaAcceso.Celular
        else -> PantallaAcceso.Codigo(pendientes.first())
    }
}

/**
 * Primer ingreso en este teléfono (especificación §3.1): correo y
 * contraseña, los pasos que pida el servidor y activar la huella. Cada paso
 * entra deslizándose 6 % desde la derecha con fundido.
 */
@Composable
fun FlujoAcceso(contenedor: Contenedor, alSalir: () -> Unit) {
    var pantalla by remember { mutableStateOf<PantallaAcceso>(PantallaAcceso.Correo) }
    var ingreso by remember { mutableStateOf<Ingreso?>(null) }
    var avisoDeReinicio by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val reducir = LocalReducirMovimiento.current

    fun reiniciar(aviso: String) {
        alcance.launch {
            delay(1600)
            ingreso = null
            avisoDeReinicio = aviso
            pantalla = PantallaAcceso.Correo
        }
    }

    BackHandler {
        if (pantalla == PantallaAcceso.Correo) alSalir() else {
            ingreso = null
            pantalla = PantallaAcceso.Correo
        }
    }

    FondoAcceso {
        AnimatedContent(
            targetState = pantalla,
            transitionSpec = {
                val desliza = if (reducir) 0 else 1
                (fadeIn(tween(Movimiento.NORMAL, easing = Movimiento.curva)) +
                    slideInHorizontally(tween(Movimiento.NORMAL, easing = Movimiento.curva)) { it * 6 / 100 * desliza })
                    .togetherWith(fadeOut(tween(Movimiento.RAPIDO)) + slideOutHorizontally(tween(Movimiento.NORMAL)) { -it * 3 / 100 * desliza })
            },
            label = "paso del acceso",
        ) { p ->
            val i = ingreso
            if (i == null || p == PantallaAcceso.Correo) {
                PasoCorreo(
                    contenedor = contenedor,
                    avisoInicial = avisoDeReinicio,
                    alVolver = alSalir,
                    alEmpezar = { reto ->
                        avisoDeReinicio = null
                        val nuevo = Ingreso(reto)
                        ingreso = nuevo
                        pantalla = nuevo.siguiente()
                    },
                )
            } else {
                val volver = { ingreso = null; pantalla = PantallaAcceso.Correo }
                when (p) {
                    is PantallaAcceso.Codigo -> PasoCodigo(
                        contenedor = contenedor,
                        ingreso = i,
                        paso = p.paso,
                        alAvanzar = { pantalla = i.siguiente() },
                        alReiniciar = ::reiniciar,
                        alVolver = volver,
                    )
                    PantallaAcceso.Celular -> PasoCelular(
                        contenedor = contenedor,
                        ingreso = i,
                        alAvanzar = { pantalla = i.siguiente() },
                        alReiniciar = ::reiniciar,
                        alVolver = volver,
                    )
                    else -> PasoActivar(contenedor = contenedor, ingreso = i, alReiniciar = ::reiniciar)
                }
            }
        }
    }
}
