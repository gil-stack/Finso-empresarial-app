package mx.finsocial.empresarial.pantallas.acceso

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.Contenedor
import mx.finsocial.empresarial.nucleo.Textos
import mx.finsocial.empresarial.nucleo.TextoFirmado
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.nucleo.api.Paso
import mx.finsocial.empresarial.nucleo.api.RetoDeEntrada
import mx.finsocial.empresarial.ui.piezas.AvisoEnLinea
import mx.finsocial.empresarial.ui.piezas.BotonPrincipal
import mx.finsocial.empresarial.ui.piezas.CampoFinso
import mx.finsocial.empresarial.ui.piezas.CasillasCodigo
import mx.finsocial.empresarial.ui.piezas.Hapticos
import mx.finsocial.empresarial.ui.piezas.MarcoAcceso
import mx.finsocial.empresarial.ui.tema.Finso
import mx.finsocial.empresarial.ui.tema.LocalReducirMovimiento
import mx.finsocial.empresarial.ui.tema.Menta
import java.time.Duration
import java.time.Instant

private fun etiquetaDePaso(ingreso: Ingreso) =
    if (ingreso.pendientes.isEmpty()) "Último paso" else "Paso ${ingreso.numeroDePaso} de ${ingreso.total}"

/** Texto para una falla de la huella (especificación §6, segunda tabla). */
internal fun textoDeBiometria(e: FallaBiometria): String = when (e.tipo) {
    FallaBiometria.Tipo.CANCELO -> Textos.BIO_CANCELO
    FallaBiometria.Tipo.SIN_BIOMETRIA -> Textos.BIO_SIN
    FallaBiometria.Tipo.BLOQUEADA -> Textos.BIO_BLOQUEADA
    FallaBiometria.Tipo.INVALIDADA -> Textos.BIO_INVALIDADA
    FallaBiometria.Tipo.NO_EXISTE -> Textos.BIO_NO_EXISTE
    FallaBiometria.Tipo.OTRA -> Textos.OTRO
}

// ───── Paso 1: correo y contraseña ─────

@Composable
internal fun PasoCorreo(
    contenedor: Contenedor,
    avisoInicial: String?,
    alVolver: () -> Unit,
    alEmpezar: (RetoDeEntrada) -> Unit,
) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(avisoInicial) }
    val alcance = rememberCoroutineScope()
    val foco = LocalFocusManager.current
    val vista = LocalView.current
    val listo = correo.contains('@') && correo.trim().length >= 5 && contrasena.isNotEmpty()

    fun continuar() {
        if (!listo || cargando) return
        foco.clearFocus()
        cargando = true
        error = null
        alcance.launch {
            try {
                alEmpezar(contenedor.api.iniciar(correo.trim(), contrasena, contenedor.telefono))
            } catch (e: FallaApi) {
                Hapticos.error(vista)
                error = e.texto
            } finally {
                cargando = false
            }
        }
    }

    MarcoAcceso(
        etiqueta = "Registra este teléfono",
        titulo = "Entra con tu cuenta",
        explicacion = "Usa el mismo correo y contraseña de Finso Empresas. Solo la primera vez en este teléfono.",
        alVolver = alVolver,
        abajo = {
            BotonPrincipal("Continuar", ::continuar, habilitado = listo, cargando = cargando, modifier = Modifier.testTag("continuar"))
            Text(
                "¿Olvidaste tu contraseña? Recupérala en empresas.finsocial.app",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        },
    ) {
        CampoFinso(
            etiqueta = "Correo",
            valor = correo,
            alCambiar = { correo = it.trim(); error = null },
            ayuda = "tu@empresa.mx",
            teclado = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            tipoAutollenado = ContentType.EmailAddress + ContentType.Username,
            habilitado = !cargando,
            modifier = Modifier.testTag("correo"),
        )
        Spacer(Modifier.height(18.dp))
        CampoFinso(
            etiqueta = "Contraseña",
            valor = contrasena,
            alCambiar = { contrasena = it; error = null },
            teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            acciones = KeyboardActions(onGo = { continuar() }),
            tipoAutollenado = ContentType.Password,
            contrasena = true,
            habilitado = !cargando,
            modifier = Modifier.testTag("contrasena"),
        )
        AvisoEnLinea(error)
    }
}

// ───── Pasos del servidor: código del correo o del SMS ─────

/** Segundos que faltan para poder pedir otro código. */
@Composable
private fun cuentaRegresiva(hasta: Instant?, reloj: () -> Instant): Long {
    var faltan by remember(hasta) { mutableLongStateOf(hasta?.let { Duration.between(reloj(), it).seconds.coerceAtLeast(0L) } ?: 0L) }
    LaunchedEffect(hasta) {
        while (hasta != null) {
            faltan = Duration.between(reloj(), hasta).seconds.coerceAtLeast(0L)
            if (faltan <= 0) break
            delay(1000)
        }
    }
    return faltan
}

@Composable
internal fun PasoCodigo(
    contenedor: Contenedor,
    ingreso: Ingreso,
    paso: String,
    alAvanzar: () -> Unit,
    alReiniciar: (String) -> Unit,
    alVolver: () -> Unit,
) {
    val esCorreo = paso == Paso.CODIGO_CORREO
    var codigo by remember(paso) { mutableStateOf("") }
    var cargando by remember(paso) { mutableStateOf(false) }
    var error by remember(paso) { mutableStateOf<String?>(null) }
    var info by remember(paso) { mutableStateOf<String?>(null) }
    var sacudir by remember(paso) { mutableIntStateOf(0) }
    val alcance = rememberCoroutineScope()
    val vista = LocalView.current
    val faltan = cuentaRegresiva(ingreso.reenvioEn, contenedor.reloj)
    val destino = if (esCorreo) {
        ingreso.reto.correoEnmascarado ?: "tu correo"
    } else {
        ingreso.celular ?: "tu celular"
    }

    fun confirmar(c: String) {
        if (c.length != 6 || cargando) return
        cargando = true
        error = null
        info = null
        alcance.launch {
            try {
                val r = contenedor.api.confirmarCodigo(ingreso.reto.reto, paso, c)
                if (r.celularEnmascarado != null) ingreso.celular = r.celularEnmascarado
                ingreso.reenvioEn = r.reenvioDisponibleEn?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ingreso.pendientes = r.pasosPendientes
                Hapticos.exito(vista)
                alAvanzar()
            } catch (e: FallaApi) {
                Hapticos.error(vista)
                error = e.texto
                codigo = ""
                sacudir++
                if (e.reiniciaElIngreso) alReiniciar(e.texto)
            } finally {
                cargando = false
            }
        }
    }

    fun reenviar() {
        alcance.launch {
            try {
                val r = contenedor.api.reenviarCodigo(ingreso.reto.reto, paso)
                ingreso.reenvioEn = r.reenvioDisponibleEn?.let { runCatching { Instant.parse(it) }.getOrNull() }
                r.celularEnmascarado?.let { ingreso.celular = it }
                error = null
                info = if (esCorreo) "Te mandamos otro correo." else "Te mandamos otro SMS."
            } catch (e: FallaApi) {
                error = e.texto
                if (e.reiniciaElIngreso) alReiniciar(e.texto)
            }
        }
    }

    MarcoAcceso(
        etiqueta = etiquetaDePaso(ingreso),
        titulo = if (esCorreo) "Revisa tu correo" else "Revisa tus mensajes",
        explicacion = "Escribe el código de 6 dígitos que mandamos a $destino.",
        alVolver = alVolver,
        abajo = {
            BotonPrincipal("Confirmar", { confirmar(codigo) }, habilitado = codigo.length == 6, cargando = cargando)
        },
    ) {
        CasillasCodigo(
            codigo = codigo,
            alCambiar = { codigo = it; if (it.isNotEmpty()) error = null },
            alCompletar = ::confirmar,
            sacudir = sacudir,
            habilitado = !cargando,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = ::reenviar,
            enabled = faltan <= 0 && !cargando,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (faltan > 0) "Pedir otro código en ${faltan / 60}:${(faltan % 60).toString().padStart(2, '0')}" else "Pedir otro código",
                style = MaterialTheme.typography.titleSmall,
                color = if (faltan > 0) Finso.colores.secundario else Finso.colores.acento,
            )
        }
        AvisoEnLinea(error)
        AvisoEnLinea(info, tipo = mx.finsocial.empresarial.ui.piezas.TipoAviso.INFO)
        if (contenedor.simulada) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Modo de prueba: el código es 123456",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ───── Paso del servidor: capturar el celular ─────

/** `5512345678` se ve como `55 1234 5678` mientras se escribe. */
private object FormatoCelular : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val salida = buildString {
            d.forEachIndexed { i, ch ->
                if (i == 2 || i == 6) append(' ')
                append(ch)
            }
        }
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (if (offset > 2) 1 else 0) + (if (offset > 6) 1 else 0)
            override fun transformedToOriginal(offset: Int) = (offset - (if (offset > 3) 1 else 0) - (if (offset > 8) 1 else 0)).coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(salida), mapa)
    }
}

@Composable
internal fun PasoCelular(
    contenedor: Contenedor,
    ingreso: Ingreso,
    alAvanzar: () -> Unit,
    alReiniciar: (String) -> Unit,
    alVolver: () -> Unit,
) {
    var digitos by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val vista = LocalView.current

    fun mandar() {
        if (digitos.length != 10 || cargando) return
        cargando = true
        error = null
        alcance.launch {
            try {
                val r = contenedor.api.capturarCelular(ingreso.reto.reto, "+52$digitos")
                ingreso.celular = r.celularEnmascarado
                ingreso.reenvioEn = r.reenvioDisponibleEn?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ingreso.pendientes = ingreso.pendientes.filterNot { it == Paso.CAPTURAR_CELULAR }
                alAvanzar()
            } catch (e: FallaApi) {
                Hapticos.error(vista)
                error = e.texto
                if (e.reiniciaElIngreso) alReiniciar(e.texto)
            } finally {
                cargando = false
            }
        }
    }

    MarcoAcceso(
        etiqueta = etiquetaDePaso(ingreso),
        titulo = "Tu celular",
        explicacion = "Te mandaremos un código por SMS. Este número también sirve para avisarte de operaciones importantes.",
        alVolver = alVolver,
        abajo = { BotonPrincipal("Mandar código", ::mandar, habilitado = digitos.length == 10, cargando = cargando) },
    ) {
        CampoFinso(
            etiqueta = "Celular a 10 dígitos",
            valor = digitos,
            alCambiar = { nuevo ->
                // Acepta pegar «+52 55 1234 5678»: se queda con los 10 dígitos.
                val d = nuevo.filter { it.isDigit() }
                digitos = (if (d.length > 10 && d.startsWith("52")) d.drop(2) else d).take(10)
                error = null
            },
            ayuda = "55 1234 5678",
            teclado = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
            acciones = KeyboardActions(onSend = { mandar() }),
            tipoAutollenado = ContentType.PhoneNumber,
            transformacion = FormatoCelular,
            habilitado = !cargando,
            modifier = Modifier.testTag("celular"),
        )
        AvisoEnLinea(error)
    }
}

// ───── Último paso: activar la huella ─────

@Composable
internal fun PasoActivar(contenedor: Contenedor, ingreso: Ingreso, alReiniciar: (String) -> Unit) {
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    val vista = LocalView.current

    fun activar() {
        if (cargando) return
        cargando = true
        error = null
        alcance.launch {
            val llave = contenedor.llave
            try {
                val publica = llave.crear()
                val texto = TextoFirmado.registrar(ingreso.reto.reto, TextoFirmado.huellaDeLlave(publica))
                val firma = llave.firmar(texto, "Activar tu huella en Finso Empresarial")
                val r = contenedor.api.registrarDispositivo(ingreso.reto.reto, publica, contenedor.telefono.nombre, firma)
                Hapticos.exito(vista)
                contenedor.sesion.alEntrar(r.usuario)
            } catch (e: FallaBiometria) {
                // No queda nada registrado: la llave creada se borra.
                llave.borrar()
                error = textoDeBiometria(e)
            } catch (e: FallaApi) {
                llave.borrar()
                Hapticos.error(vista)
                error = e.texto
                if (e.reiniciaElIngreso) alReiniciar(e.texto)
            } catch (e: Exception) {
                llave.borrar()
                error = Textos.deError("LLAVE_INVALIDA")
            } finally {
                cargando = false
            }
        }
    }

    MarcoAcceso(
        etiqueta = etiquetaDePaso(ingreso),
        titulo = "Activa tu huella",
        explicacion = "Con tu huella entras a la app y autorizas cada SPEI. Tu huella nunca sale de este teléfono.",
        abajo = {
            BotonPrincipal("Activar tu huella", ::activar, cargando = cargando, icono = Icons.Rounded.Fingerprint, modifier = Modifier.testTag("activar"))
        },
    ) {
        HuellaQueLate(modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Punto(Icons.Rounded.Lock, "Se crea una llave única guardada en el chip seguro del teléfono.")
            Punto(Icons.Rounded.VerifiedUser, "Cada SPEI lleva tu firma: nadie puede mandarlo por ti.")
            Punto(Icons.Rounded.PhonelinkErase, "Puedes dar de baja este teléfono cuando quieras desde el portal.")
        }
        AvisoEnLinea(error)
    }
}

@Composable
private fun Punto(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = null, tint = Finso.colores.acento, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Finso.colores.texto)
    }
}

/** El círculo con la huella que «respira» despacio (quieto si se reduce el movimiento). */
@Composable
internal fun HuellaQueLate(modifier: Modifier = Modifier) {
    val reducir = LocalReducirMovimiento.current
    val t = rememberInfiniteTransition(label = "huella")
    val escala by t.animateFloat(
        initialValue = 1f,
        targetValue = if (reducir) 1f else 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "respira",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .scale(escala)
            .background(Menta.copy(alpha = .14f), CircleShape)
            .border(1.dp, Menta.copy(alpha = .35f), CircleShape)
            .padding(28.dp),
    ) {
        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = Menta, modifier = Modifier.size(56.dp))
    }
}
