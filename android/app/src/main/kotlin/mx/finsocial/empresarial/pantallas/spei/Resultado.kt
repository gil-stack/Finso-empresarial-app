package mx.finsocial.empresarial.pantallas.spei

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.piezas.Insignia
import mx.finsocial.empresarial.ui.piezas.Palomita
import mx.finsocial.empresarial.ui.piezas.Renglon
import mx.finsocial.empresarial.ui.piezas.Tarjeta
import mx.finsocial.empresarial.ui.tema.Finso

/** Título según el estado (§3.4 paso 4). */
fun tituloDeResultado(estado: String): String = when (estado) {
    EstadoOrden.POR_LIBERAR -> "Listo, falta liberarlo"
    EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> "Enviando…"
    EstadoOrden.LIQUIDADA -> "SPEI enviado"
    EstadoOrden.RECHAZADA -> "SPEI rechazado"
    else -> "No se pudo mandar"
}

/**
 * El resultado de pedir, liberar o rechazar. Mientras está en proceso pregunta
 * cada 3 s, hasta 20 veces. «Listo» (o atrás) vuelve a Inicio.
 */
@Composable
fun ResultadoDeOrden(contenedor: Contenedor, inicial: Orden, alListo: () -> Unit) {
    val c = Finso.colores
    val vista = LocalView.current
    var orden by remember(inicial.id) { mutableStateOf(inicial) }

    LaunchedEffect(inicial.id) {
        var vueltas = 0
        while (orden.estado == EstadoOrden.EN_PROCESO && vueltas < 20) {
            delay(3_000)
            vueltas++
            try { orden = contenedor.api.orden(orden.id) } catch (_: FallaApi) { /* se vuelve a intentar */ }
        }
    }
    LaunchedEffect(orden.estado) {
        when (orden.estado) {
            EstadoOrden.LIQUIDADA, EstadoOrden.POR_LIBERAR -> Hapticos.exito(vista)
            EstadoOrden.RECHAZADA, EstadoOrden.DEVUELTA, EstadoOrden.CANCELADA -> Hapticos.error(vista)
        }
    }

    MarcoSpei(
        titulo = null,
        alVolver = alListo,
        abajo = { BotonPrincipal("Listo", alListo, modifier = Modifier.fillMaxWidth().testTag("listo")) },
    ) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { IconoDeEstado(orden.estado) }
        Spacer(Modifier.height(20.dp))
        Text(
            tituloDeResultado(orden.estado),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite }.testTag("titulo-resultado"),
        )
        orden.motivo?.takeIf { orden.estado != EstadoOrden.LIQUIDADA }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = c.secundario, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding8())
        }
        orden.liberadaPor?.let {
            Text("Liberada por $it", style = MaterialTheme.typography.bodyMedium, color = c.secundario, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding8())
        }
        Spacer(Modifier.height(24.dp))
        Aparicion(retraso = 120) { TarjetaDeOrden(orden) }
    }
}

private fun Modifier.padding8() = this.padding(top = 8.dp)

@Composable
private fun IconoDeEstado(estado: String) {
    val c = Finso.colores
    when (estado) {
        EstadoOrden.LIQUIDADA -> Palomita(descripcion = "SPEI enviado")
        EstadoOrden.POR_LIBERAR -> Circulo(c.avisoFondo) { Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = c.aviso, modifier = Modifier.size(40.dp)) }
        EstadoOrden.EN_PROCESO, EstadoOrden.ENVIADA -> Circulo(c.avisoFondo) { CircularProgressIndicator(color = c.aviso, strokeWidth = 3.dp, modifier = Modifier.size(40.dp)) }
        else -> Circulo(c.peligroFondo) { Icon(Icons.Rounded.Close, contentDescription = null, tint = c.peligro, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
private fun Circulo(fondo: Color, contenido: @Composable () -> Unit) {
    Box(Modifier.size(88.dp).background(fondo, CircleShape), contentAlignment = Alignment.Center) { contenido() }
}

/** Importe, cuenta, concepto, referencia, clave de rastreo y estado. */
@Composable
fun TarjetaDeOrden(orden: Orden) {
    Tarjeta {
        Renglon("Importe", Dinero.pesos(orden.importeCentavos), fuerte = true)
        Renglon("Para", NombreLegible.de(orden.destino.titular))
        Renglon("Cuenta", listOfNotNull(orden.destino.banco, "•••• ${orden.destino.clabeFinal}").joinToString(" · "))
        Renglon("Concepto", orden.concepto)
        orden.referenciaNumerica?.let { Renglon("Referencia", it.toString(), modifier = Modifier.testTag("referencia")) }
        orden.claveRastreo?.let { Renglon("Clave de rastreo", it) }
        Renglon("Estado", "", extra = { Insignia(orden.estado) })
    }
}
