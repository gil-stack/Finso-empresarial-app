package mx.finsocial.empresarial.nucleo.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Cliente de la API móvil de Finso Empresas (`/movil/v1`, contrato v1.1).
 *
 * - Manda siempre los encabezados del contrato (§0).
 * - Si el acceso venció, renueva una sola vez y repite; la renovación rota
 *   y se hace de a una (usar dos veces la misma revoca la sesión).
 * - Pasada la hora desde la última huella no intenta renovar: pide huella.
 * - `SESION_REVOCADA` borra la sesión; `DISPOSITIVO_REVOCADO` olvida el
 *   teléfono; `EMPRESA_REQUERIDA` borra la empresa elegida. En los tres casos
 *   avisa por [alFallarSesion] para que la app cambie de pantalla.
 *
 * La API simulada es un [okhttp3.Interceptor] ([ServidorSimulado]) que
 * contesta con el mismo JSON, así que pasa por este mismo código.
 */
class ApiFinso(
    baseUrl: String,
    private val versionApp: String,
    private val guardado: Guardado,
    private val cliente: OkHttpClient = clienteBase(),
    private val reloj: () -> Instant = Instant::now,
    private val plataforma: String = "android",
) {
    private val base: HttpUrl = baseUrl.trimEnd('/').plus("/").toHttpUrl()
    private val tipoJson = "application/json; charset=utf-8".toMediaType()
    private val candadoRenovar = Mutex()

    /** Se llama cuando la sesión, el teléfono o la empresa dejan de servir. */
    var alFallarSesion: (FallaApi) -> Unit = {}

    // ───── §1 Entrar ─────

    suspend fun iniciar(correo: String, contrasena: String, telefono: DatosDelTelefono): RetoDeEntrada =
        publico("sesion/inicio", PeticionInicio(correo.trim(), contrasena, telefono), PeticionInicio.serializer(), RetoDeEntrada.serializer())

    suspend fun capturarCelular(reto: String, celular: String): RespuestaCelular =
        publico("sesion/celular", PeticionCelular(reto, celular), PeticionCelular.serializer(), RespuestaCelular.serializer())

    suspend fun confirmarCodigo(reto: String, paso: String, codigo: String): RespuestaCodigo =
        publico("sesion/codigo", PeticionCodigo(reto, paso, codigo), PeticionCodigo.serializer(), RespuestaCodigo.serializer())

    suspend fun reenviarCodigo(reto: String, paso: String): RespuestaReenvio =
        publico("sesion/codigo/reenviar", PeticionReenvio(reto, paso), PeticionReenvio.serializer(), RespuestaReenvio.serializer())

    /** §1.5: registra la llave del teléfono. Guarda `dispositivo` y la sesión. */
    suspend fun registrarDispositivo(reto: String, llavePublica: String, nombre: String?, firma: String): Registro {
        val r = publico(
            "dispositivos",
            PeticionRegistro(reto = reto, llavePublica = llavePublica, nombre = nombre, firma = firma),
            PeticionRegistro.serializer(),
            Registro.serializer(),
        )
        guardado.dispositivo = r.dispositivo
        recordarIngreso(r.sesion, r.usuario)
        return r
    }

    /** §1.6 paso 1. */
    suspend fun retoParaEntrar(): RetoDeFirma {
        val dispositivo = guardado.dispositivo ?: throw FallaApi("DISPOSITIVO_REVOCADO", 401)
        return publico("sesion/biometria/reto", PeticionRetoBiometria(dispositivo), PeticionRetoBiometria.serializer(), RetoDeFirma.serializer())
    }

    /** §1.6 paso 3: entra con la firma de la huella. */
    suspend fun entrarConBiometria(reto: String, firma: String): Ingreso {
        val dispositivo = guardado.dispositivo ?: throw FallaApi("DISPOSITIVO_REVOCADO", 401)
        val i = publico("sesion/biometria", PeticionBiometria(dispositivo, reto, firma), PeticionBiometria.serializer(), Ingreso.serializer())
        recordarIngreso(i.sesion, i.usuario)
        return i
    }

    private fun recordarIngreso(sesion: Sesion, usuario: Usuario) {
        guardado.sesion = sesion
        guardado.ultimaBiometria = reloj()
        guardado.nombreCorto = usuario.nombre.trim().split(Regex("\\s+")).firstOrNull()
    }

    /** §1.7: revoca la sesión (no el teléfono). Aunque falle la red, aquí se olvida. */
    suspend fun salir() {
        try {
            autenticado("sesion/salir", "POST", cuerpo = "{}".toRequestBody(tipoJson), salida = null)
        } catch (_: FallaApi) {
        } finally {
            guardado.olvidarSesion()
        }
    }

    /**
     * Revoca una sesión que ya no está guardada (al olvidar el teléfono): se
     * manda con su propio token y su teléfono, y se ignora si falla.
     */
    suspend fun revocar(sesion: Sesion, dispositivo: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val r = armar("sesion/salir", "POST", "{}".toRequestBody(tipoJson))
                    .header("X-Finso-Dispositivo", dispositivo)
                    .header("Authorization", "Bearer ${sesion.acceso}")
                    .build()
                cliente.newCall(r).execute().close()
            }
        }
    }

    suspend fun yo(): Usuario = autenticado("yo", "GET", salida = Usuario.serializer())

    /** §1.8 v1.1: guarda la empresa (va en `X-Finso-Empresa` desde ahora) y pide `/yo`. */
    suspend fun elegirEmpresa(empresa: String): Usuario {
        guardado.empresa = empresa
        return yo()
    }

    // ───── §2 Cuenta ─────

    suspend fun cuenta(): LecturaDeCuenta = autenticado("cuenta", "GET", salida = LecturaDeCuenta.serializer())

    suspend fun movimientos(mes: String): PaginaDeMovimientos =
        autenticado("movimientos", "GET", consulta = mapOf("mes" to mes), salida = PaginaDeMovimientos.serializer())

    // ───── §3 Destinos ─────

    suspend fun destinos(): List<Destino> = autenticado("destinos", "GET", salida = ListaDeDestinos.serializer()).elementos

    // ───── §4 SPEI ─────

    suspend fun prepararSpei(s: SolicitudSpei): PreparacionSpei =
        autenticado("spei/preparar", "POST", cuerpo = json(s, SolicitudSpei.serializer()), salida = PreparacionSpei.serializer())

    /**
     * §4.2. [llave] es la `Idempotency-Key`: un reintento por red cortada va
     * con la misma llave y el mismo cuerpo y regresa la misma orden.
     */
    suspend fun pedirSpei(peticion: PeticionSpei, llave: String): Orden =
        autenticado("spei", "POST", cuerpo = json(peticion, PeticionSpei.serializer()), llave = llave, salida = Orden.serializer())

    suspend fun orden(id: String): Orden = autenticado("spei/$id", "GET", salida = Orden.serializer())

    /** Sin [estado]: las últimas de la empresa. Con `POR_LIBERAR`: lo que esta persona puede liberar. */
    suspend fun ordenes(estado: String? = null): List<Orden> =
        autenticado("spei", "GET", consulta = estado?.let { mapOf("estado" to it) } ?: emptyMap(), salida = ListaDeOrdenes.serializer()).elementos

    suspend fun prepararLiberacion(orden: String): PreparacionLiberar =
        autenticado("spei/$orden/liberar/preparar", "POST", cuerpo = "{}".toRequestBody(tipoJson), salida = PreparacionLiberar.serializer())

    suspend fun liberar(orden: String, reto: String, firma: String, llave: String): Orden =
        autenticado("spei/$orden/liberar", "POST", cuerpo = json(PeticionFirma(reto, firma), PeticionFirma.serializer()), llave = llave, salida = Orden.serializer())

    suspend fun rechazar(orden: String, motivo: String, reto: String, firma: String, llave: String): Orden =
        autenticado(
            "spei/$orden/rechazar", "POST",
            cuerpo = json(PeticionRechazo(motivo.trim(), reto, firma), PeticionRechazo.serializer()),
            llave = llave, salida = Orden.serializer(),
        )

    // ───── §5.1 Avisos en vivo ─────

    /**
     * Pide al servidor que avise por push (FCM) cada cambio de esta orden.
     * 204 aunque el servidor no lo guarde; la app no distingue.
     */
    suspend fun registrarAviso(orden: String, token: String) {
        autenticado<Unit?>(
            "avisos/actividad", "POST",
            cuerpo = json(PeticionAviso(orden, plataforma, token), PeticionAviso.serializer()),
            salida = null,
        )
    }

    // ───── Transporte ─────

    private fun <T> json(valor: T, s: KSerializer<T>): RequestBody = JsonFinso.encodeToString(s, valor).toRequestBody(tipoJson)

    private fun armar(ruta: String, metodo: String, cuerpo: RequestBody?, consulta: Map<String, String> = emptyMap()): Request.Builder {
        val url = base.newBuilder().addPathSegments(ruta).apply { consulta.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return Request.Builder()
            .url(url)
            .method(metodo, cuerpo)
            .header("Accept", "application/json")
            .header("X-Finso-App", versionApp)
            .header("X-Finso-Plataforma", plataforma)
            .apply { guardado.dispositivo?.let { header("X-Finso-Dispositivo", it) } }
    }

    private suspend fun <E, S> publico(ruta: String, entrada: E, sEntrada: KSerializer<E>, salida: KSerializer<S>): S =
        withContext(Dispatchers.IO) {
            val r = ejecutar(armar(ruta, "POST", json(entrada, sEntrada)).build())
            leer(r, salida)!!
        }

    private suspend fun <S> autenticado(
        ruta: String,
        metodo: String,
        cuerpo: RequestBody? = null,
        consulta: Map<String, String> = emptyMap(),
        llave: String? = null,
        salida: KSerializer<S>?,
    ): S = withContext(Dispatchers.IO) {
        fun peticion(s: Sesion): Request = armar(ruta, metodo, cuerpo, consulta)
            .header("Authorization", "Bearer ${s.acceso}")
            .apply {
                guardado.empresa?.let { header("X-Finso-Empresa", it) }
                if (llave != null) header("Idempotency-Key", llave)
            }
            .build()

        var sesion = sesionVigente()
        var r = ejecutar(peticion(sesion))
        if (r.code == 401 && codigoDe(r) == "SESION_VENCIDA") {
            r.close()
            sesion = renovar(sesion)
            r = ejecutar(peticion(sesion))
        }
        @Suppress("UNCHECKED_CAST")
        leer(r, salida) as S
    }

    /** La sesión guardada, renovada antes si el acceso ya venció. */
    private suspend fun sesionVigente(): Sesion {
        val s = guardado.sesion ?: throw falla(FallaApi("SESION_VENCIDA", 401))
        if (!Instant.parse(s.renovacionExpiraEn).isAfter(reloj())) {
            guardado.olvidarSesion()
            throw falla(FallaApi("SESION_VENCIDA", 401))
        }
        return if (Instant.parse(s.accesoExpiraEn).isAfter(reloj().plusSeconds(5))) s else renovar(s)
    }

    /** Renueva de a una: si otra llamada ya renovó mientras esperaba, usa esa. */
    private suspend fun renovar(vieja: Sesion): Sesion = candadoRenovar.withLock {
        val actual = guardado.sesion ?: throw falla(FallaApi("SESION_VENCIDA", 401))
        if (actual.renovacion != vieja.renovacion) return@withLock actual
        if (!Instant.parse(actual.renovacionExpiraEn).isAfter(reloj())) {
            guardado.olvidarSesion()
            throw falla(FallaApi("SESION_VENCIDA", 401))
        }
        val r = ejecutar(armar("sesion/renovar", "POST", json(PeticionRenovar(actual.renovacion), PeticionRenovar.serializer())).build())
        try {
            val nueva = leer(r, Sesion.serializer())!!
            guardado.sesion = nueva
            nueva
        } catch (e: FallaApi) {
            if (e.estadoHttp == 401) guardado.olvidarSesion()
            throw e
        }
    }

    private fun ejecutar(r: Request): Response = try {
        cliente.newCall(r).execute()
    } catch (e: IOException) {
        throw SinRed(e)
    }

    private fun codigoDe(r: Response): String? = runCatching {
        JsonFinso.decodeFromString(CuerpoDeError.serializer(), r.peekBody(64 * 1024).string()).codigo
    }.getOrNull()

    private fun <S> leer(r: Response, salida: KSerializer<S>?): S? = r.use {
        val texto = it.body.string()
        if (it.isSuccessful) {
            if (salida == null || texto.isBlank()) return null
            return JsonFinso.decodeFromString(salida, texto)
        }
        val e = runCatching { JsonFinso.decodeFromString(CuerpoDeError.serializer(), texto) }.getOrNull()
        val codigo = e?.codigo ?: when (it.code) {
            401 -> "SESION_VENCIDA"
            403 -> "SIN_PERMISO"
            426 -> "VERSION_NO_SOPORTADA"
            else -> "ERROR_${it.code}"
        }
        throw falla(FallaApi(codigo, it.code, e?.mensaje, e?.intentosRestantes, e?.reintentarEn, e?.campos.orEmpty()))
    }

    /** Aplica lo que cada error le hace a lo guardado y avisa a la app. */
    private fun falla(f: FallaApi): FallaApi {
        when (f.codigo) {
            "SESION_REVOCADA", "SESION_VENCIDA" -> guardado.olvidarSesion()
            "DISPOSITIVO_REVOCADO" -> guardado.olvidarTodo(aviso = f.texto)
            "EMPRESA_REQUERIDA" -> guardado.empresa = null
        }
        if (f.codigo in setOf("SESION_VENCIDA", "SESION_REVOCADA", "DISPOSITIVO_REVOCADO", "EMPRESA_REQUERIDA", "VERSION_NO_SOPORTADA")) {
            alFallarSesion(f)
        }
        return f
    }

    companion object {
        fun clienteBase(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        /** UUID v4 para `Idempotency-Key`. */
        fun nuevaLlave(): String = UUID.randomUUID().toString()
    }
}
