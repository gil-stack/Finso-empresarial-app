package mx.finsocial.empresarial.pantallas.dentro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.Movimiento
import mx.finsocial.empresarial.nucleo.spei.Listas
import mx.finsocial.empresarial.pantallas.Ajustes
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.Esqueleto
import mx.finsocial.empresarial.ui.piezas.TipoAviso
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Radios

/**
 * Inicio (§3.3): saludo, tarjeta de saldo, «Mandar SPEI», «SPEI por liberar»
 * y los movimientos del mes agrupados por día. Deslizar hacia abajo refresca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inicio(contenedor: Contenedor, datos: DatosDeInicio, alMandar: () -> Unit, alPorLiberar: () -> Unit) {
    val usuario = datos.usuario
    val p = usuario.permisos
    val c = Finso.colores
    val alcance = rememberCoroutineScope()
    val ocultar by contenedor.ocultarImportes.collectAsState()
    var ajustes by remember { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<Movimiento?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    val ahora = contenedor.reloj()
    val dias = remember(datos.meses.toList(), ahora.epochSecond / 60) {
        datos.meses.map { it.mes to Listas.porDia(it.elementos, ahora) }
    }
    val cuenta = datos.lectura?.cuenta
    val margen = WindowInsets.safeDrawing.asPaddingValues()

    PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = {
            refrescando = true
            alcance.launch { datos.refrescar(); refrescando = false }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = Espacios.lateral,
                end = Espacios.lateral,
                top = margen.calculateTopPadding() + 16.dp,
                bottom = margen.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize().testTag("inicio"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "encabezado") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Aparicion {
                            Text(
                                "Hola, ${NombreLegible.corto(usuario.nombre)}",
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.semantics { heading() }.testTag("saludo"),
                            )
                        }
                        usuario.empresa?.let {
                            Aparicion(retraso = 60) { Text(it.nombre, style = MaterialTheme.typography.bodyLarge, color = c.secundario) }
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { ajustes = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.elevado, contentColor = c.texto),
                        modifier = Modifier.testTag("ajustes"),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Ajustes")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            item(key = "saldo") {
                Column(Modifier.widthIn(max = Espacios.columnaMaxima)) {
                    when {
                        !p.verCuenta -> AvisoEnLinea(
                            "Tu usuario no ve el saldo ni los movimientos. Si los necesitas, pídeselo a tu administrador.",
                            TipoAviso.INFO,
                        )
                        datos.cargando && datos.lectura == null -> EsqueletoDeSaldo()
                        datos.lectura != null && cuenta == null -> AvisoEnLinea("Tu cuenta se está abriendo…", TipoAviso.ESPERA)
                        cuenta != null -> Aparicion(retraso = 90) {
                            TarjetaDeSaldo(
                                cuenta = cuenta,
                                sinConexion = datos.lectura?.sinConexion == true,
                                ocultar = ocultar,
                                alCambiarOjo = { contenedor.cambiarOcultarImportes(!ocultar) },
                                animarDesdeCero = !datos.saldoYaAnimado,
                                alAnimar = { datos.saldoYaAnimado = true },
                            )
                        }
                    }
                    AvisoEnLinea(datos.error)
                }
            }

            item(key = "acciones") {
                Column(Modifier.widthIn(max = Espacios.columnaMaxima).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (p.pedirSpei && p.verDestinos) {
                        val bloqueada = cuenta?.bloqueada == true
                        BotonPrincipal(
                            texto = "Mandar SPEI",
                            alTocar = alMandar,
                            habilitado = !bloqueada,
                            icono = Icons.AutoMirrored.Rounded.ArrowForward,
                            modifier = Modifier.fillMaxWidth().testTag("mandar"),
                        )
                    }
                    if (p.liberarSpei && datos.porLiberar.isNotEmpty()) {
                        FilaPorLiberar(datos.porLiberar.size, alPorLiberar)
                    }
                }
            }

            if (p.verCuenta) {
                item(key = "titulo-movimientos") {
                    Text(
                        "Movimientos",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth().padding(top = 28.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                if (datos.cargando && datos.meses.isEmpty()) {
                    items(5, key = { "esqueleto-$it" }) { EsqueletoDeMovimiento() }
                }
                dias.forEach { (mes, lista) ->
                    if (lista.isEmpty()) {
                        item(key = "vacio-$mes") {
                            Text(
                                "Sin movimientos en ${Fechas.soloMes(mes)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.secundario,
                                modifier = Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                    }
                    lista.forEach { dia ->
                        item(key = "dia-$mes-${dia.titulo}") {
                            Text(
                                dia.titulo,
                                style = MaterialTheme.typography.titleSmall,
                                color = c.secundario,
                                modifier = Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                            )
                        }
                        items(dia.movimientos, key = { it.id }) { m ->
                            RenglonDeMovimiento(m, ocultar, Modifier.widthIn(max = Espacios.columnaMaxima).animateItem()) { detalle = m }
                        }
                    }
                }
                if (datos.puedeVerMas) {
                    item(key = "ver-mas") {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                            if (datos.cargandoMes) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = c.acento)
                            } else {
                                TextButton(onClick = { alcance.launch { datos.cargarMesAnterior() } }, modifier = Modifier.testTag("ver-mas")) {
                                    Text("Ver ${datos.siguienteMes?.let(Fechas::soloMes).orEmpty()}", color = c.acento, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (ajustes) Ajustes(contenedor, usuario, alCerrar = { ajustes = false })
    detalle?.let { DetalleDeMovimiento(it, ocultar, alCerrar = { detalle = null }) }
}

@Composable
private fun FilaPorLiberar(cantidad: Int, alTocar: () -> Unit) {
    val c = Finso.colores
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.tarjeta, RoundedCornerShape(Radios.grande))
            .clickable(onClick = alTocar)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("por-liberar"),
    ) {
        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = c.aviso)
        Spacer(Modifier.width(12.dp))
        Text("SPEI por liberar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(c.aviso, CircleShape),
        ) {
            Text("$cantidad", style = MaterialTheme.typography.labelLarge, color = c.sobreAccion)
        }
    }
}

@Composable
private fun EsqueletoDeSaldo() {
    val c = Finso.colores
    Column(
        Modifier.fillMaxWidth().background(c.tarjeta, RoundedCornerShape(Radios.tarjeta)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Esqueleto(120.dp, 14.dp)
        Esqueleto(220.dp, 40.dp)
        Esqueleto(180.dp, 14.dp)
    }
}

@Composable
private fun EsqueletoDeMovimiento() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Esqueleto(44.dp, 44.dp, redondo = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Esqueleto(160.dp, 14.dp)
            Esqueleto(100.dp, 12.dp)
        }
        Esqueleto(70.dp, 16.dp)
    }
}
