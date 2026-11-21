package mx.finsocial.empresarial.pantallas.dentro

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.nucleo.Dinero
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.Movimiento
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.piezas.Importe
import mx.finsocial.empresarial.ui.piezas.Inicial
import mx.finsocial.empresarial.ui.piezas.Renglon
import mx.finsocial.empresarial.ui.tema.Finso
import java.time.Instant

private fun nombreDe(m: Movimiento): String =
    m.contraparte?.nombre?.let(NombreLegible::de) ?: when (m.tipo) {
        "COMISION" -> "Comisión"
        "INTERES" -> "Intereses"
        else -> if (m.esAbono) "Depósito" else "Cargo"
    }

private fun importeDe(m: Movimiento, ocultar: Boolean): String = when {
    ocultar -> Dinero.OCULTO
    m.esAbono -> Dinero.pesos(m.importeCentavos, conSigno = true)
    else -> Dinero.pesos(-m.importeCentavos, conSigno = true)
}

/** Un renglón suelto sobre el fondo, sin tarjeta ni línea (§10, regla de Gil). */
@Composable
fun RenglonDeMovimiento(m: Movimiento, ocultar: Boolean, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    val c = Finso.colores
    val nombre = nombreDe(m)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = alTocar)
            .padding(vertical = 10.dp)
            .testTag("movimiento-${m.id}"),
    ) {
        Inicial(nombre)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val linea = m.concepto?.takeIf { it.isNotBlank() } ?: m.contraparte?.banco
            if (linea != null) {
                Text(linea, style = MaterialTheme.typography.bodySmall, color = c.secundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            importeDe(m, ocultar),
            style = MaterialTheme.typography.titleSmall,
            color = if (m.esAbono && !ocultar) c.positivo else c.texto,
        )
    }
}

/** El detalle en una hoja desde abajo; la clave de rastreo se copia manteniéndola. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetalleDeMovimiento(m: Movimiento, ocultar: Boolean, alCerrar: () -> Unit) {
    val c = Finso.colores
    val contexto = LocalContext.current
    val vista = LocalView.current
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var claveCopiada by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = hoja, containerColor = if (c.esOscuro) c.elevado else c.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp).testTag("detalle")) {
            Text(if (m.esAbono) "Recibiste" else "Enviaste", style = MaterialTheme.typography.titleSmall, color = c.secundario)
            Importe(importeDe(m, ocultar), 36, color = if (m.esAbono && !ocultar) c.positivo else c.texto)
            Text(nombreDe(m), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val fecha = runCatching { Fechas.fechaYHora(Instant.parse(m.fecha)) }.getOrDefault(m.fecha)
            Renglon("Fecha", fecha)
            m.contraparte?.banco?.let { Renglon("Banco", it) }
            m.contraparte?.clabeFinal?.let { Renglon("Cuenta", "•••• $it") }
            m.concepto?.takeIf { it.isNotBlank() }?.let { Renglon("Concepto", it) }
            m.claveRastreo?.let { clave ->
                Renglon(
                    if (claveCopiada) "Clave de rastreo · copiada" else "Clave de rastreo",
                    clave,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            contexto.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Clave de rastreo", clave))
                            Hapticos.ligero(vista)
                            claveCopiada = true
                        },
                    ),
                )
            }
            m.saldoResultanteCentavos?.let { Renglon("Saldo después", if (ocultar) Dinero.OCULTO else Dinero.pesos(it)) }
        }
    }
}
