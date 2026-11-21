package mx.finsocial.empresarial.pantallas.spei

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Textos
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.pantallas.acceso.textoDeBiometria
import mx.finsocial.empresarial.ui.piezas.Encabezado
import mx.finsocial.empresarial.ui.tema.Espacios

/**
 * Lo que se le dice a la persona cuando algo falla al firmar o mandar.
 * Cancelar la huella no es un error (null). Si la llave quedó inválida se
 * olvida el teléfono y se vuelve al correo.
 */
internal fun textoDeFalla(contenedor: Contenedor, e: Throwable): String? = when (e) {
    is FallaBiometria -> when (e.tipo) {
        FallaBiometria.Tipo.CANCELO -> null
        FallaBiometria.Tipo.INVALIDADA -> { contenedor.sesion.olvidarTelefono(Textos.BIO_INVALIDADA); null }
        FallaBiometria.Tipo.NO_EXISTE -> { contenedor.sesion.olvidarTelefono(Textos.BIO_NO_EXISTE); null }
        else -> textoDeBiometria(e)
    }
    is FallaApi -> e.texto
    else -> Textos.OTRO
}

/** Marco de las pantallas de SPEI: encabezado con volver, contenido con scroll y la acción abajo. */
@Composable
internal fun MarcoSpei(
    titulo: String?,
    alVolver: () -> Unit,
    abajo: @Composable ColumnScope.() -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Encabezado(titulo, alVolver, Modifier.padding(horizontal = 4.dp))
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = Espacios.columnaMaxima)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacios.lateral),
            content = contenido,
        )
        Column(
            Modifier.widthIn(max = Espacios.columnaMaxima).fillMaxWidth().padding(horizontal = Espacios.lateral).padding(top = 8.dp, bottom = 16.dp),
            content = abajo,
        )
    }
}
