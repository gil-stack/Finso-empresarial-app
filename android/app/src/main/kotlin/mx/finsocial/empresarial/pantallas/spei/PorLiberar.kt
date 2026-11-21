package mx.finsocial.empresarial.pantallas.spei

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.spei.FirmaDeOrden
import mx.finsocial.empresarial.pantallas.dentro.DatosDeInicio
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.Importe
import mx.finsocial.empresarial.ui.piezas.Inicial
import mx.finsocial.empresarial.ui.piezas.Renglon
import mx.finsocial.empresarial.ui.piezas.Tarjeta
import mx.finsocial.empresarial.ui.piezas.TipoAviso
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Radios
import java.time.Instant

private sealed interface PasoLiberar {
    data object Lista : PasoLiberar
    data class Detalle(val orden: Orden) : PasoLiberar
    data class Rechazar(val orden: Orden) : PasoLiberar
    data class Resultado(val orden: Orden) : PasoLiberar
}

/** SPEI por liberar (§3.5): lista, detalle con liberar o rechazar, y resultado. */
@Composable
fun PorLiberar(contenedor: Contenedor, datos: DatosDeInicio, alTerminar: () -> Unit) {
    var paso by remember { mutableStateOf<PasoLiberar>(PasoLiberar.Lista) }
    val atras: () -> Unit = {
        when (val p = paso) {
            is PasoLiberar.Detalle -> paso = PasoLiberar.Lista
            is PasoLiberar.Rechazar -> paso = PasoLiberar.Detalle(p.orden)
            else -> alTerminar()
        }
    }
    BackHandler(onBack = atras)
    AnimatedContent(
        targetState = paso,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "por liberar",
    ) { p ->
        when (p) {
            PasoLiberar.Lista -> ListaPorLiberar(contenedor, datos, alVolver = atras, alElegir = { paso = PasoLiberar.Detalle(it) })
            is PasoLiberar.Detalle -> DetalleParaLiberar(
                contenedor, p.orden,
                alVolver = atras,
                alRechazar = { paso = PasoLiberar.Rechazar(p.orden) },
                alTerminar = { paso = PasoLiberar.Resultado(it) },
            )
            is PasoLiberar.Rechazar -> RechazarOrden(contenedor, p.orden, alVolver = atras, alTerminar = { paso = PasoLiberar.Resultado(it) })
            is PasoLiberar.Resultado -> ResultadoDeOrden(contenedor, p.orden, alListo = alTerminar)
        }
    }
}

private fun pidio(o: Orden, ahora: Instant): String {
    val cuando = runCatching { Fechas.hace(Instant.parse(o.creadaEn), ahora) }.getOrDefault("")
    return "pidió ${o.pedidaPor} · $cuando"
}

@Composable
private fun ListaPorLiberar(contenedor: Contenedor, datos: DatosDeInicio, alVolver: () -> Unit, alElegir: (Orden) -> Unit) {
    val c = Finso.colores
    var ordenes by remember { mutableStateOf(datos.porLiberar) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            ordenes = contenedor.api.ordenes(EstadoOrden.POR_LIBERAR).filter { it.puedoLiberar }
        } catch (e: FallaApi) {
            error = e.texto
        }
    }
    val ahora = contenedor.reloj()
    MarcoSpei(titulo = "SPEI por liberar", alVolver = alVolver) {
        AvisoEnLinea(error)
        if (ordenes.isEmpty() && error == null) {
            Text("No hay SPEI por liberar.", style = MaterialTheme.typography.bodyLarge, color = c.secundario, modifier = Modifier.padding(vertical = 24.dp))
        }
        ordenes.forEachIndexed { i, o ->
            val nombre = NombreLegible.de(o.destino.titular)
            Aparicion(retraso = i * 30) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { alElegir(o) }.padding(vertical = 12.dp).testTag("orden-${o.id}"),
                ) {
                    Inicial(nombre)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pidio(o, ahora), style = MaterialTheme.typography.bodySmall, color = c.secundario)
                    }
                    Text(Dinero.pesos(o.importeCentavos), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun DetalleParaLiberar(contenedor: Contenedor, orden: Orden, alVolver: () -> Unit, alRechazar: () -> Unit, alTerminar: (Orden) -> Unit) {
    val c = Finso.colores
    val alcance = rememberCoroutineScope()
    val firma = remember(orden.id) { FirmaDeOrden(contenedor.api, contenedor.guardado, contenedor.llave, orden, contenedor.reloj) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nombre = NombreLegible.de(orden.destino.titular)
    val importe = Dinero.pesos(orden.importeCentavos)

    fun ejecutar(accion: suspend () -> Orden) {
        cargando = true
        error = null
        alcance.launch {
            try {
                alTerminar(accion())
            } catch (e: Exception) {
                error = textoDeFalla(contenedor, e)
            } finally {
                cargando = false
            }
        }
    }

    MarcoSpei(
        titulo = null,
        alVolver = alVolver,
        abajo = {
            AvisoEnLinea(error)
            Spacer(Modifier.height(8.dp))
            BotonPrincipal(
                texto = "Liberar con tu huella",
                icono = Icons.Rounded.Fingerprint,
                cargando = cargando,
                modifier = Modifier.fillMaxWidth().testTag("liberar"),
                alTocar = { ejecutar { firma.liberar("Liberar SPEI de $importe a $nombre") } },
            )
            TextButton(
                onClick = alRechazar,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("rechazar"),
            ) {
                Text("Rechazar", color = c.peligro, style = MaterialTheme.typography.titleSmall)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Text("${orden.pedidaPor} pidió mandar", style = MaterialTheme.typography.titleSmall, color = c.secundario, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) { Importe(importe, 44) }
        Text("a $nombre", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Tarjeta {
            Renglon("Cuenta", listOfNotNull(orden.destino.banco, "•••• ${orden.destino.clabeFinal}").joinToString(" · "))
            Renglon("Concepto", orden.concepto)
            orden.referenciaNumerica?.let { Renglon("Referencia", it.toString()) }
            Renglon("Pedido", runCatching { Fechas.fechaYHora(Instant.parse(orden.creadaEn)) }.getOrDefault(orden.creadaEn))
        }
        AvisoEnLinea("Al liberarlo sale de la cuenta de tu empresa.", TipoAviso.INFO)
    }
}

/**
 * «¿Rechazar este SPEI?» (§3.5): el motivo (5 a 500, lo ve quien lo pidió) y
 * el botón rojo, que se activa con 5 o más. Va en su propia pantalla para que
 * el teclado no tape nada.
 */
@Composable
private fun RechazarOrden(contenedor: Contenedor, orden: Orden, alVolver: () -> Unit, alTerminar: (Orden) -> Unit) {
    val c = Finso.colores
    val alcance = rememberCoroutineScope()
    val firma = remember(orden.id) { FirmaDeOrden(contenedor.api, contenedor.guardado, contenedor.llave, orden, contenedor.reloj) }
    var motivo by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nombre = NombreLegible.de(orden.destino.titular)
    val importe = Dinero.pesos(orden.importeCentavos)

    MarcoSpei(
        titulo = null,
        alVolver = alVolver,
        abajo = {
            AvisoEnLinea(error)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    cargando = true
                    error = null
                    alcance.launch {
                        try {
                            alTerminar(firma.rechazar(motivo.trim(), "Rechazar SPEI de $importe a $nombre"))
                        } catch (e: Exception) {
                            error = textoDeFalla(contenedor, e)
                        } finally {
                            cargando = false
                        }
                    }
                },
                enabled = FirmaDeOrden.motivoValido(motivo) && !cargando,
                shape = RoundedCornerShape(Radios.grande),
                colors = ButtonDefaults.buttonColors(containerColor = c.peligro, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("confirmar-rechazo"),
            ) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rechazar con tu huella", style = MaterialTheme.typography.titleSmall)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Text("¿Rechazar este SPEI?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("$importe a $nombre, que pidió ${orden.pedidaPor}.", style = MaterialTheme.typography.bodyLarge, color = c.secundario)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = motivo,
            onValueChange = { motivo = it.take(FirmaDeOrden.MOTIVO_MAXIMO) },
            label = { Text("Motivo (lo verá quien lo pidió)") },
            minLines = 3,
            shape = RoundedCornerShape(Radios.medio),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().testTag("motivo"),
        )
        Text(
            "${motivo.trim().length}/${FirmaDeOrden.MOTIVO_MAXIMO} · mínimo ${FirmaDeOrden.MOTIVO_MINIMO}",
            style = MaterialTheme.typography.bodySmall,
            color = c.secundario,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}
