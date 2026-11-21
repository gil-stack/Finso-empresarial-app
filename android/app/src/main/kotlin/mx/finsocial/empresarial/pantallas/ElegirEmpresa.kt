package mx.finsocial.empresarial.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Usuario
import mx.finsocial.empresarial.ui.piezas.Aparicion
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.tema.Espacios
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.Movimiento
import mx.finsocial.empresarial.ui.tema.Radios

/** Para quien trabaja con varias empresas (especificación §3.2). */
@Composable
fun ElegirEmpresa(contenedor: Contenedor, usuario: Usuario) {
    var eligiendo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val vista = LocalView.current
    val c = Finso.colores

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Espacios.lateral, vertical = 24.dp),
    ) {
        item {
            Text("Hola, ${NombreLegible.corto(usuario.nombre)}", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Trabajas con varias empresas. ¿Con cuál quieres entrar?", style = MaterialTheme.typography.bodyLarge, color = c.secundario)
            Spacer(Modifier.height(12.dp))
            AvisoEnLinea(error)
        }
        itemsIndexed(usuario.empresas, key = { _, e -> e.id }) { i, e ->
            Aparicion(retraso = i * Movimiento.ESCALON) {
                Surface(
                    onClick = onClick@{
                        if (eligiendo != null) return@onClick
                        Hapticos.ligero(vista)
                        eligiendo = e.id
                        error = null
                        alcance.launch {
                            try {
                                contenedor.sesion.elegirEmpresa(e.id)
                            } catch (f: FallaApi) {
                                error = f.texto
                            } finally {
                                eligiendo = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(Radios.grande),
                    color = c.tarjeta,
                    modifier = Modifier.fillMaxWidth().widthIn(max = Espacios.columnaMaxima),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(18.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).padding(0.dp)) {
                            Surface(shape = CircleShape, color = c.elevado, modifier = Modifier.size(44.dp)) {}
                            Icon(Icons.Rounded.Business, contentDescription = null, tint = c.acento)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(e.nombre, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (eligiendo == e.id) CircularProgressIndicator(strokeWidth = 2.dp, color = c.acento, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
