package mx.finsocial.empresarial.pantallas.spei

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Concepto
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.Destino
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Orden
import mx.finsocial.empresarial.nucleo.api.PreparacionSpei
import mx.finsocial.empresarial.nucleo.api.SolicitudSpei
import mx.finsocial.empresarial.nucleo.spei.Listas
import mx.finsocial.empresarial.nucleo.spei.PedidoSpei
import mx.finsocial.empresarial.pantallas.dentro.DatosDeInicio
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.CampoFinso
import mx.finsocial.empresarial.ui.piezas.Esqueleto
import mx.finsocial.empresarial.ui.piezas.Importe
import mx.finsocial.empresarial.ui.piezas.Inicial
import mx.finsocial.empresarial.ui.piezas.Renglon
import mx.finsocial.empresarial.ui.piezas.Tarjeta
import mx.finsocial.empresarial.ui.piezas.TipoAviso
import mx.finsocial.empresarial.ui.tema.Estilos
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Movimiento
import java.time.Instant

private sealed interface PasoSpei {
    val indice: Int
    data object Destinos : PasoSpei { override val indice = 0 }
    data class Importe(val destino: Destino) : PasoSpei { override val indice = 1 }
    data class Revisar(val destino: Destino, val pedido: PedidoSpei, val preparacion: PreparacionSpei) : PasoSpei { override val indice = 2 }
    data class Resultado(val orden: Orden) : PasoSpei { override val indice = 3 }
}

/** Mandar SPEI (§3.4): a quién, importe y concepto, revisar y resultado. */
@Composable
fun MandarSpei(contenedor: Contenedor, datos: DatosDeInicio, alTerminar: () -> Unit) {
    var paso by remember { mutableStateOf<PasoSpei>(PasoSpei.Destinos) }
    var importe by remember { mutableStateOf(TextFieldValue("")) }
    var concepto by remember { mutableStateOf("") }
    val reducir = LocalReducirMovimiento.current
    val disponible = datos.lectura?.cuenta?.disponibleCentavos?.takeIf { datos.usuario.permisos.verCuenta }

    val atras: () -> Unit = {
        when (val p = paso) {
            PasoSpei.Destinos, is PasoSpei.Resultado -> alTerminar()
            is PasoSpei.Importe -> paso = PasoSpei.Destinos
            is PasoSpei.Revisar -> paso = PasoSpei.Importe(p.destino)
        }
    }
    BackHandler(onBack = atras)

    AnimatedContent(
        targetState = paso,
        contentKey = { it.indice },
        transitionSpec = {
            val adelante = targetState.indice > initialState.indice
            val d = if (reducir) 0 else 1
            (fadeIn(tween(Movimiento.NORMAL)) + slideInHorizontally(tween(Movimiento.NORMAL, easing = Movimiento.curva)) { (if (adelante) it / 6 else -it / 6) * d })
                .togetherWith(fadeOut(tween(Movimiento.RAPIDO)) + slideOutHorizontally(tween(Movimiento.NORMAL)) { (if (adelante) -it / 10 else it / 10) * d })
        },
        label = "pasos del SPEI",
    ) { p ->
        when (p) {
            PasoSpei.Destinos -> ElegirDestino(contenedor, alVolver = atras, alElegir = { paso = PasoSpei.Importe(it) })
            is PasoSpei.Importe -> ImporteYConcepto(
                contenedor = contenedor,
                destino = p.destino,
                disponible = disponible,
                importe = importe,
                alCambiarImporte = { importe = it },
                concepto = concepto,
                alCambiarConcepto = { concepto = it },
                alVolver = atras,
                alRevisar = { pedido, prep -> paso = PasoSpei.Revisar(p.destino, pedido, prep) },
            )
            is PasoSpei.Revisar -> Revisar(contenedor, p.destino, p.pedido, p.preparacion, alVolver = atras, alPedir = { paso = PasoSpei.Resultado(it) })
            is PasoSpei.Resultado -> ResultadoDeOrden(contenedor, p.orden, alListo = alTerminar)
        }
    }
}

// ───── 1. ¿A quién le mandas? ─────

@Composable
private fun ElegirDestino(contenedor: Contenedor, alVolver: () -> Unit, alElegir: (Destino) -> Unit) {
    val c = Finso.colores
    var destinos by remember { mutableStateOf<List<Destino>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ahora by remember { mutableStateOf(contenedor.reloj()) }
    var vuelta by remember { mutableStateOf(0) }

    LaunchedEffect(vuelta) {
        try {
            destinos = Listas.destinosOrdenados(contenedor.api.destinos())
            error = null
        } catch (e: FallaApi) {
            error = e.texto
        }
    }
    // Cada 15 s se actualiza la cuenta regresiva; al cumplirse, se vuelve a pedir la lista.
    LaunchedEffect(destinos) {
        val lista = destinos ?: return@LaunchedEffect
        while (true) {
            delay(15_000)
            ahora = contenedor.reloj()
            val proxima = Listas.proximaActivacion(lista, Instant.EPOCH)
            if (proxima != null && !proxima.isAfter(ahora)) { vuelta++; return@LaunchedEffect }
        }
    }

    MarcoSpei(titulo = "Mandar SPEI", alVolver = alVolver) {
        Text("¿A quién le mandas?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Solo a cuentas registradas en el portal. Las nuevas se activan 30 minutos después de registrarlas.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.secundario,
        )
        AvisoEnLinea(error)
        Spacer(Modifier.height(12.dp))
        val lista = destinos
        when {
            lista == null && error == null -> repeat(4) {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Esqueleto(44.dp, 44.dp, redondo = true)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Esqueleto(180.dp, 14.dp); Esqueleto(120.dp, 12.dp) }
                }
            }
            lista != null && lista.isEmpty() -> AvisoEnLinea(
                "Todavía no tienes cuentas destino. Regístralas en el portal: Cuenta > Cuentas destino.",
                TipoAviso.INFO,
            )
            lista != null -> lista.forEachIndexed { i, d ->
                Aparicion(retraso = i * 30) { RenglonDeDestino(d, ahora) { alElegir(d) } }
            }
        }
    }
}

@Composable
private fun RenglonDeDestino(d: Destino, ahora: Instant, alTocar: () -> Unit) {
    val c = Finso.colores
    val nombre = NombreLegible.de(d.titular)
    val puede = d.sePuedePagar
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (puede) Modifier.clickable(onClick = alTocar) else Modifier.semantics { disabled() })
            .padding(vertical = 12.dp)
            .testTag("destino-${d.id}"),
    ) {
        Inicial(nombre, atenuado = !puede)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (puede) 1f else .55f)) {
            Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Listas.lineaDeDestino(d, ahora),
                style = MaterialTheme.typography.bodySmall,
                color = if (d.enEspera) c.aviso else c.secundario,
                maxLines = 2,
            )
        }
        when {
            d.enEspera -> Icon(Icons.Rounded.Schedule, contentDescription = null, tint = c.aviso)
            puede -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.secundario)
        }
    }
}

// ───── 2. Importe y concepto ─────

private fun tamanoDeImporte(texto: String) = when {
    texto.length <= 9 -> 48
    texto.length <= 12 -> 40
    else -> 32
}

@Composable
private fun ImporteYConcepto(
    contenedor: Contenedor,
    destino: Destino,
    disponible: Long?,
    importe: TextFieldValue,
    alCambiarImporte: (TextFieldValue) -> Unit,
    concepto: String,
    alCambiarConcepto: (String) -> Unit,
    alVolver: () -> Unit,
    alRevisar: (PedidoSpei, PreparacionSpei) -> Unit,
) {
    val c = Finso.colores
    val alcance = rememberCoroutineScope()
    val enfoque = remember { FocusRequester() }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val centavos = Dinero.centavosDe(importe.text) ?: 0L
    val paraMandar = Concepto.paraMandar(concepto)
    val sePasa = disponible != null && centavos > disponible
    val listo = centavos > 0 && Concepto.esValido(paraMandar) && !sePasa

    LaunchedEffect(Unit) { runCatching { enfoque.requestFocus() } }

    MarcoSpei(
        titulo = NombreLegible.de(destino.titular),
        alVolver = alVolver,
        abajo = {
            AvisoEnLinea(error)
            Spacer(Modifier.height(8.dp))
            BotonPrincipal(
                texto = "Revisar",
                habilitado = listo,
                cargando = cargando,
                modifier = Modifier.fillMaxWidth().testTag("revisar"),
                alTocar = {
                    cargando = true
                    error = null
                    alcance.launch {
                        try {
                            val pedido = PedidoSpei(contenedor.api, contenedor.guardado, contenedor.llave, SolicitudSpei(destino.id, centavos, paraMandar), contenedor.reloj)
                            alRevisar(pedido, pedido.preparar())
                        } catch (e: FallaApi) {
                            error = e.texto
                        } finally {
                            cargando = false
                        }
                    }
                },
            )
        },
    ) {
        Text(
            listOfNotNull(destino.banco, "•••• ${destino.clabeFinal}").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = c.secundario,
        )
        Spacer(Modifier.height(32.dp))
        val texto = importe.text
        BasicTextField(
            value = importe,
            onValueChange = { nuevo ->
                val f = Dinero.formatearAlTeclear(importe.text, nuevo.text)
                alCambiarImporte(TextFieldValue(f, TextRange(f.length)))
            },
            singleLine = true,
            textStyle = Estilos.importe(tamanoDeImporte(texto)).copy(color = c.texto, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(c.acento),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(enfoque).testTag("importe").semantics { contentDescription = "Importe" },
            decorationBox = { campo ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Importe("$", tamanoDeImporte(texto), color = if (texto.isEmpty()) c.secundario else c.texto)
                    Box {
                        if (texto.isEmpty()) Importe("0.00", tamanoDeImporte(texto), color = c.secundario.copy(alpha = .5f))
                        campo()
                    }
                }
            },
        )
        if (disponible != null) {
            Text(
                if (sePasa) "Pasa tu disponible de ${Dinero.pesos(disponible)}" else "Disponible ${Dinero.pesos(disponible)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sePasa) c.peligro else c.secundario,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("disponible-spei"),
            )
        }
        Spacer(Modifier.height(36.dp))
        CampoFinso(
            etiqueta = "Concepto",
            valor = concepto,
            alCambiar = { alCambiarConcepto(Concepto.limpiar(it).take(Concepto.MAXIMO)) },
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.testTag("concepto"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp)) {
            Text("Sin acentos ni ñ (así lo pide SPEI).", style = MaterialTheme.typography.bodySmall, color = c.secundario, modifier = Modifier.weight(1f))
            Text("${concepto.length}/${Concepto.MAXIMO}", style = MaterialTheme.typography.bodySmall, color = c.secundario)
        }
    }
}

// ───── 3. Revisa tu SPEI ─────

@Composable
private fun Revisar(
    contenedor: Contenedor,
    destino: Destino,
    pedido: PedidoSpei,
    preparacion: PreparacionSpei,
    alVolver: () -> Unit,
    alPedir: (Orden) -> Unit,
) {
    val c = Finso.colores
    val alcance = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reintento by remember { mutableStateOf(false) }
    val r = preparacion.resumen
    val nombre = NombreLegible.de(r.destino.titular)

    MarcoSpei(
        titulo = "Revisa tu SPEI",
        alVolver = alVolver,
        abajo = {
            AvisoEnLinea(error)
            Spacer(Modifier.height(8.dp))
            BotonPrincipal(
                texto = if (reintento) "Intentar otra vez" else "Pedir con tu huella",
                icono = if (reintento) null else Icons.Rounded.Fingerprint,
                cargando = cargando,
                modifier = Modifier.fillMaxWidth().testTag("pedir"),
                alTocar = {
                    cargando = true
                    error = null
                    alcance.launch {
                        try {
                            alPedir(pedido.pedir("Pedir SPEI de ${Dinero.pesos(r.importeCentavos)} a $nombre"))
                        } catch (e: Exception) {
                            error = textoDeFalla(contenedor, e)
                        } finally {
                            reintento = pedido.tieneReintento
                            cargando = false
                        }
                    }
                },
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Vas a mandar", style = MaterialTheme.typography.titleSmall, color = c.secundario, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) { Importe(Dinero.pesos(r.importeCentavos), 44) }
        Text("a $nombre", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Tarjeta {
            Renglon("Cuenta", listOfNotNull(r.destino.banco ?: destino.banco, "•••• ${r.destino.clabeFinal}").joinToString(" · "))
            Renglon("Concepto", r.concepto)
            Renglon("Comisión", if (r.comisionCentavos == 0L) "Sin comisión" else Dinero.pesos(r.comisionCentavos))
            Renglon("Total", Dinero.pesos(r.importeCentavos + r.comisionCentavos), fuerte = true)
        }
        if (preparacion.requiereLiberador) {
            AvisoEnLinea("Después de confirmarlo, otra persona de tu empresa tiene que liberarlo para que salga.", TipoAviso.ESPERA)
        }
    }
}

