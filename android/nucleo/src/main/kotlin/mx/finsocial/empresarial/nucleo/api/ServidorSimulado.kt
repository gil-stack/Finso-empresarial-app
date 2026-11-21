package mx.finsocial.empresarial.nucleo.api

import kotlinx.serialization.KSerializer
import mx.finsocial.empresarial.nucleo.Concepto
import mx.finsocial.empresarial.nucleo.Fechas
import mx.finsocial.empresarial.nucleo.TextoFirmado
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.random.Random

/**
 * Finso Empresas de mentira, para usar la app completa sin servidor
 * (especificación compartida §5; los mismos datos que la app de iPhone).
 * Es un interceptor de OkHttp: contesta con el JSON del contrato, así que la
 * app pasa por el mismo [ApiFinso], los mismos encabezados y los mismos
 * decodificadores que con el servidor real. Verifica las firmas de verdad
 * (ECDSA P-256) con la llave pública registrada.
 *
 * Para probar:
 * - El código del correo y del SMS es **123456**. Siempre va primero el correo.
 * - `ana@…`: administradora y liberadora (ve saldo y libera). Cualquier otro
 *   correo: Luis, solicitante (pide SPEI, no ve saldo ni libera).
 * - `nuevo@…`: captura el celular después del correo.
 * - `varias@…`: trabaja con dos empresas y debe elegir.
 * - Todo SPEI queda por liberar; liberado pasa a «en proceso» y se liquida a
 *   los 2.5 s. Si pasa de $890,000.00, el core lo rechaza al liberar.
 */
class ServidorSimulado(
    private val reloj: () -> Instant = Instant::now,
    private val demoraMs: Long = 650,
) : Interceptor {
    companion object {
        const val CODIGO = "123456"
        const val TOPE_CENTAVOS = 89_000_000L
        private val tipoJson = "application/json; charset=utf-8".toMediaType()
    }

    private class FallaSim(val http: Int, val codigo: String, val intentos: Int? = null, val reintentarEn: String? = null, val mensaje: String? = null) : Exception(codigo)

    private class RetoPendiente(val id: String, val correo: String, val pendientes: MutableList<String>, val envios: MutableMap<String, Int>, var intentos: Int = 0, val expira: Instant)

    private class SesionViva(val dispositivo: String, val correo: String, var acceso: String, var accesoExpira: Instant, var renovacion: String, val renovacionExpira: Instant)

    private class OrdenSim(var orden: Orden, val pidioCorreo: String, var liquidarEn: Instant? = null)

    private val azar = Random(7)
    private var consecutivo = 0
    private fun id(prefijo: String) = "${prefijo}_SIM" + (++consecutivo).toString().padStart(6, '0')
    private fun token(prefijo: String) = prefijo + "_" + (1..24).map { "abcdefghijkmnpqrstuvwxyz23456789"[azar.nextInt(32)] }.joinToString("")
    private val ahora get() = reloj()
    private fun iso(i: Instant) = i.toString()

    private var reto: RetoPendiente? = null
    private val llaves = mutableMapOf<String, String>()        // dev_ → llave pública SPKI base64
    private val correoDe = mutableMapOf<String, String>()      // dev_ → correo
    private val retosDeFirma = mutableMapOf<String, Pair<String, Instant>>() // reto → (dispositivo u operación, vence)
    private val sesiones = mutableListOf<SesionViva>()
    private val renovacionesUsadas = mutableMapOf<String, SesionViva>()
    private val ordenes = linkedMapOf<String, OrdenSim>()
    private val porLlave = mutableMapOf<String, Pair<String, String>>() // Idempotency-Key → (cuerpo, orden)
    private val preparados = mutableMapOf<String, Pair<String, SolicitudSpei>>() // reto → (correo, solicitud)
    private val retosDeLiberar = mutableMapOf<String, String>() // reto → orden
    private var saldo = 124_821_537L
    private val movimientos = mutableListOf<Movimiento>()
    private val destinos = mutableListOf<Destino>()

    init { sembrar() }

    private fun sembrar() {
        val t = ahora
        destinos += Destino("dst_SIM_PAPELERA", "PAPELERA DEL CENTRO SA DE CV", "PROVEEDOR", "BBVA MEXICO", "4821", "ACTIVO", iso(t.minus(Duration.ofDays(40))))
        destinos += Destino("dst_SIM_FLETES", "FLETES RAPIDOS DEL NORTE", "PROVEEDOR", "BANORTE", "0921", "ACTIVO", iso(t.minus(Duration.ofDays(12))))
        destinos += Destino("dst_SIM_LUCIA", "LUCIA HERNANDEZ RUIZ", "COLABORADOR", "SANTANDER", "7710", "ACTIVO", iso(t.minus(Duration.ofDays(3))))
        destinos += Destino("dst_SIM_EMPAQUES", "EMPAQUES Y CARTON MX", "PROVEEDOR", "BANAMEX", "3305", "EN_ESPERA", iso(t.plus(Duration.ofMinutes(18))))
        val contrapartes = listOf(
            listOf("ABARROTES LA ESPERANZA", "BBVA MEXICO", "1187", "1"),
            listOf("PAPELERA DEL CENTRO SA DE CV", "BBVA MEXICO", "4821", "0"),
            listOf("COMERCIALIZADORA ALBA", "BANORTE", "6620", "1"),
            listOf("FLETES RAPIDOS DEL NORTE", "BANORTE", "0921", "0"),
            listOf("TIENDAS MONTERO", "SANTANDER", "0045", "1"),
        )
        var s = saldo
        for (i in 0 until 26) {
            val (nombre, banco, clabe, entra) = contrapartes[i % contrapartes.size]
            val abono = entra == "1"
            val importe = 150_000L + azar.nextLong(4_500_000L)
            val fecha = t.minus(Duration.ofHours(5L + i * 19L)).minus(Duration.ofMinutes(azar.nextLong(50)))
            movimientos += Movimiento(
                id = id("mov"),
                sentido = if (abono) "ABONO" else "CARGO",
                tipo = if (abono) "SPEI_ENTRADA" else "SPEI_SALIDA",
                importeCentavos = importe,
                saldoResultanteCentavos = s,
                fecha = iso(fecha),
                concepto = if (abono) "Pago factura ${1200 - i}" else "Pago a proveedor ${300 + i}",
                claveRastreo = "FINSO${fecha.epochSecond}",
                contraparte = Contraparte(nombre, banco, clabe),
                comprobante = !abono,
            )
            s += if (abono) -importe else importe
        }
        ordenes["spo_SIM_PENDIENTE"] = OrdenSim(
            Orden(
                id = "spo_SIM_PENDIENTE",
                estado = EstadoOrden.POR_LIBERAR,
                destino = breve(destinos[1]),
                importeCentavos = 1_845_000,
                concepto = "Fletes semana 47",
                referenciaNumerica = 47,
                pedidaPor = "Luis Pérez",
                creadaEn = iso(t.minus(Duration.ofMinutes(40))),
                actualizadaEn = iso(t.minus(Duration.ofMinutes(40))),
            ),
            pidioCorreo = "luis@empresa.mx",
        )
    }

    private fun breve(d: Destino) = DestinoBreve(d.id, d.titular, d.banco, d.clabeFinal)

    // ───── Usuarios ─────

    private fun esAna(correo: String) = correo.startsWith("ana")
    private fun nombreDe(correo: String) = if (esAna(correo)) "Ana López Garza" else "Luis Pérez Soto"
    private val empresasSim = listOf(EmpresaBreve("emp_SIM", "Distribuidora del Bajío"), EmpresaBreve("emp_SIM2", "Transportes del Bajío"))

    private fun usuario(correo: String, empresaElegida: String?): Usuario {
        val ana = esAna(correo)
        val varias = "varias" in correo
        val papeles = if (ana) listOf("ADMINISTRADOR", "LIBERADOR") else listOf("SOLICITANTE")
        val suyas = if (varias) empresasSim else empresasSim.take(1)
        val empresa = if (varias) empresasSim.firstOrNull { it.id == empresaElegida } else empresasSim.first()
        return Usuario(
            id = "usr_SIM",
            nombre = nombreDe(correo),
            correo = correo,
            papeles = papeles,
            empresa = empresa,
            empresas = suyas.map { EmpresaDelUsuario(it.id, it.nombre, papeles) },
            permisos = Permisos(verCuenta = ana, verDestinos = true, pedirSpei = true, liberarSpei = ana, verOrdenes = true),
        )
    }

    // ───── Interceptor ─────

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (demoraMs > 0) Thread.sleep(demoraMs)
        val (codigo, cuerpo) = synchronized(this) {
            try {
                atender(req)
            } catch (e: FallaSim) {
                e.http to JsonFinso.encodeToString(
                    CuerpoDeError.serializer(),
                    CuerpoDeError(e.codigo, e.mensaje, intentosRestantes = e.intentos, reintentarEn = e.reintentarEn),
                )
            }
        }
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message(if (codigo < 400) "OK" else "Error")
            .body(cuerpo.toResponseBody(tipoJson))
            .build()
    }

    private fun <T> ok(s: KSerializer<T>, v: T, http: Int = 200) = http to JsonFinso.encodeToString(s, v)

    private fun <T> leer(req: Request, s: KSerializer<T>): T {
        val b = Buffer().also { req.body?.writeTo(it) }.readUtf8()
        return runCatching { JsonFinso.decodeFromString(s, b) }.getOrElse { throw FallaSim(400, "DATOS_INVALIDOS") }
    }

    private fun cuerpoCrudo(req: Request) = Buffer().also { req.body?.writeTo(it) }.readUtf8()

    private fun atender(req: Request): Pair<Int, String> {
        val ruta = req.url.encodedPath.substringAfter("/movil/v1/").trim('/')
        val m = req.method
        val partes = ruta.split('/')
        return when {
            m == "POST" && ruta == "sesion/inicio" -> inicio(leer(req, PeticionInicio.serializer()))
            m == "POST" && ruta == "sesion/celular" -> celular(leer(req, PeticionCelular.serializer()))
            m == "POST" && ruta == "sesion/codigo" -> codigo(leer(req, PeticionCodigo.serializer()))
            m == "POST" && ruta == "sesion/codigo/reenviar" -> reenviar(leer(req, PeticionReenvio.serializer()))
            m == "POST" && ruta == "dispositivos" -> registrar(leer(req, PeticionRegistro.serializer()))
            m == "POST" && ruta == "sesion/biometria/reto" -> retoBiometria(leer(req, PeticionRetoBiometria.serializer()))
            m == "POST" && ruta == "sesion/biometria" -> biometria(leer(req, PeticionBiometria.serializer()))
            m == "POST" && ruta == "sesion/renovar" -> renovar(leer(req, PeticionRenovar.serializer()))
            else -> {
                val s = sesionDe(req)
                val empresa = req.header("X-Finso-Empresa")
                when {
                    m == "POST" && ruta == "sesion/salir" -> { sesiones.remove(s); 204 to "" }
                    m == "GET" && ruta == "yo" -> ok(Usuario.serializer(), usuario(s.correo, empresa))
                    m == "GET" && ruta == "cuenta" -> { exigir(s, empresa) { it.verCuenta }; ok(LecturaDeCuenta.serializer(), LecturaDeCuenta(cuentaActual(), "core")) }
                    m == "GET" && ruta == "movimientos" -> { exigir(s, empresa) { it.verCuenta }; movimientosDe(req.url.queryParameter("mes") ?: Fechas.mesActual(ahora)) }
                    m == "GET" && ruta == "destinos" -> { exigir(s, empresa) { it.verDestinos }; ok(ListaDeDestinos.serializer(), ListaDeDestinos(destinosVivos())) }
                    m == "POST" && ruta == "spei/preparar" -> prepararSpei(s, empresa, leer(req, SolicitudSpei.serializer()))
                    m == "POST" && ruta == "spei" -> pedirSpei(s, empresa, req)
                    m == "GET" && ruta == "spei" -> listaOrdenes(s, empresa, req.url.queryParameter("estado"))
                    m == "GET" && partes.size == 2 && partes[0] == "spei" -> { exigir(s, empresa) { it.verOrdenes }; ok(Orden.serializer(), vista(ordenDe(partes[1]), s.correo)) }
                    m == "POST" && partes.size == 4 && partes[2] == "liberar" && partes[3] == "preparar" -> prepararLiberar(s, empresa, partes[1])
                    m == "POST" && partes.size == 3 && partes[2] == "liberar" -> liberar(s, empresa, partes[1], req)
                    m == "POST" && partes.size == 3 && partes[2] == "rechazar" -> rechazar(s, empresa, partes[1], req)
                    else -> throw FallaSim(404, "NO_ENCONTRADO")
                }
            }
        }
    }

    // ───── §1 Entrar ─────

    private fun inicio(p: PeticionInicio): Pair<Int, String> {
        val c = p.correo.trim().lowercase()
        if (!Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(c) || p.contrasena.isEmpty()) throw FallaSim(401, "CREDENCIALES_INVALIDAS")
        val nuevo = "nuevo" in c
        val pasos = if (nuevo) listOf(Paso.CODIGO_CORREO, Paso.CAPTURAR_CELULAR, Paso.CODIGO_SMS) else listOf(Paso.CODIGO_CORREO, Paso.CODIGO_SMS)
        val r = RetoPendiente(token("rto"), c, pasos.toMutableList(), mutableMapOf(Paso.CODIGO_CORREO to 1), expira = ahora.plus(Duration.ofMinutes(10)))
        reto = r
        return ok(
            RetoDeEntrada.serializer(),
            RetoDeEntrada(
                reto = r.id,
                expiraEn = iso(r.expira),
                pasos = pasos,
                celularEnmascarado = if (nuevo) null else "•••• ••• 4821",
                correoEnmascarado = "${c[0]}••@${c.substringAfter('@')}",
                reenvioDisponibleEn = iso(ahora.plusSeconds(60)),
            ),
        )
    }

    private fun retoVivo(id: String): RetoPendiente {
        val r = reto
        if (r == null || r.id != id || !r.expira.isAfter(ahora)) throw FallaSim(410, "RETO_VENCIDO")
        return r
    }

    private fun celular(p: PeticionCelular): Pair<Int, String> {
        val r = retoVivo(p.reto)
        if (r.pendientes.firstOrNull() != Paso.CAPTURAR_CELULAR) throw FallaSim(409, "PASO_FUERA_DE_ORDEN")
        var d = p.celular.filter { it.isDigit() }
        if (d.length == 12 && d.startsWith("52")) d = d.substring(2)
        if (d.length != 10) throw FallaSim(400, "CELULAR_INVALIDO")
        r.pendientes.remove(Paso.CAPTURAR_CELULAR)
        r.envios[Paso.CODIGO_SMS] = 1
        return ok(RespuestaCelular.serializer(), RespuestaCelular("•••• ••• ${d.takeLast(4)}", iso(ahora.plusSeconds(60))))
    }

    private fun codigo(p: PeticionCodigo): Pair<Int, String> {
        val r = retoVivo(p.reto)
        if (r.pendientes.firstOrNull() != p.paso) throw FallaSim(409, "PASO_FUERA_DE_ORDEN")
        if (p.codigo != CODIGO) {
            r.intentos++
            if (r.intentos >= 5) {
                reto = null
                throw FallaSim(429, "DEMASIADOS_INTENTOS")
            }
            throw FallaSim(400, "CODIGO_INCORRECTO", intentos = 5 - r.intentos)
        }
        r.intentos = 0
        r.pendientes.remove(p.paso)
        val smsSale = p.paso == Paso.CODIGO_CORREO && r.pendientes.firstOrNull() == Paso.CODIGO_SMS
        if (smsSale) r.envios[Paso.CODIGO_SMS] = 1
        return ok(
            RespuestaCodigo.serializer(),
            RespuestaCodigo(
                pasosPendientes = r.pendientes.toList(),
                celularEnmascarado = if (smsSale) "•••• ••• 4821" else null,
                reenvioDisponibleEn = if (smsSale) iso(ahora.plusSeconds(60)) else null,
            ),
        )
    }

    private fun reenviar(p: PeticionReenvio): Pair<Int, String> {
        val r = retoVivo(p.reto)
        val enviados = r.envios[p.paso] ?: 0
        if (enviados >= 3) throw FallaSim(429, "DEMASIADOS_INTENTOS")
        r.envios[p.paso] = enviados + 1
        return ok(
            RespuestaReenvio.serializer(),
            RespuestaReenvio(iso(ahora.plusSeconds(60)), if (p.paso == Paso.CODIGO_SMS) "•••• ••• 4821" else null),
            http = 202,
        )
    }

    private fun verificar(llavePublica: String, texto: String, firma: String): Boolean = runCatching {
        val llave = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(llavePublica)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(llave)
            update(texto.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(firma))
        }
    }.getOrDefault(false)

    private fun nuevaSesion(dispositivo: String, correo: String): Sesion {
        val s = SesionViva(dispositivo, correo, token("acc"), ahora.plus(Duration.ofMinutes(10)), token("ren"), ahora.plus(Duration.ofHours(1)))
        sesiones += s
        return Sesion(s.acceso, iso(s.accesoExpira), s.renovacion, iso(s.renovacionExpira))
    }

    private fun registrar(p: PeticionRegistro): Pair<Int, String> {
        val r = retoVivo(p.reto)
        if (r.pendientes.isNotEmpty()) throw FallaSim(409, "RETO_INCOMPLETO")
        val esEc = runCatching {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(p.llavePublica)))
        }.isSuccess
        if (!esEc) throw FallaSim(400, "LLAVE_INVALIDA")
        val texto = TextoFirmado.registrar(p.reto, TextoFirmado.huellaDeLlave(p.llavePublica))
        if (!verificar(p.llavePublica, texto, p.firma)) throw FallaSim(401, "FIRMA_INVALIDA")
        val dev = "dev_" + (1..32).map { "0123456789abcdef"[azar.nextInt(16)] }.joinToString("")
        llaves[dev] = p.llavePublica
        correoDe[dev] = r.correo
        reto = null
        return ok(Registro.serializer(), Registro(dev, nuevaSesion(dev, r.correo), usuario(r.correo, null)), http = 201)
    }

    private fun retoBiometria(p: PeticionRetoBiometria): Pair<Int, String> {
        if (p.dispositivo !in llaves) throw FallaSim(401, "DISPOSITIVO_REVOCADO")
        val r = Base64.getUrlEncoder().withoutPadding().encodeToString(azar.nextBytes(32))
        val vence = ahora.plusSeconds(60)
        retosDeFirma[r] = p.dispositivo to vence
        return ok(RetoDeFirma.serializer(), RetoDeFirma(r, iso(vence)))
    }

    private fun biometria(p: PeticionBiometria): Pair<Int, String> {
        val llave = llaves[p.dispositivo] ?: throw FallaSim(401, "DISPOSITIVO_REVOCADO")
        val (dev, vence) = retosDeFirma.remove(p.reto) ?: throw FallaSim(410, "RETO_VENCIDO")
        if (dev != p.dispositivo || !vence.isAfter(ahora)) throw FallaSim(410, "RETO_VENCIDO")
        if (!verificar(llave, TextoFirmado.entrar(p.dispositivo, p.reto), p.firma)) throw FallaSim(401, "FIRMA_INVALIDA")
        val correo = correoDe.getValue(p.dispositivo)
        return ok(Ingreso.serializer(), Ingreso(nuevaSesion(p.dispositivo, correo), usuario(correo, null)))
    }

    private fun renovar(p: PeticionRenovar): Pair<Int, String> {
        // Una renovación usada dos veces revoca la sesión entera (§1.7).
        renovacionesUsadas[p.renovacion]?.let { familia ->
            sesiones.remove(familia)
            throw FallaSim(401, "SESION_REVOCADA")
        }
        val s = sesiones.firstOrNull { it.renovacion == p.renovacion } ?: throw FallaSim(401, "SESION_VENCIDA")
        if (!s.renovacionExpira.isAfter(ahora)) {
            sesiones.remove(s)
            throw FallaSim(401, "SESION_VENCIDA")
        }
        renovacionesUsadas[s.renovacion] = s
        s.acceso = token("acc")
        s.accesoExpira = ahora.plus(Duration.ofMinutes(10))
        s.renovacion = token("ren")
        return ok(Sesion.serializer(), Sesion(s.acceso, iso(s.accesoExpira), s.renovacion, iso(s.renovacionExpira)))
    }

    private fun sesionDe(req: Request): SesionViva {
        val acceso = req.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val s = sesiones.firstOrNull { it.acceso == acceso } ?: throw FallaSim(401, "SESION_VENCIDA")
        if (s.dispositivo !in llaves) throw FallaSim(401, "DISPOSITIVO_REVOCADO")
        if (req.header("X-Finso-Dispositivo") != s.dispositivo) throw FallaSim(401, "SESION_VENCIDA")
        if (!s.accesoExpira.isAfter(ahora)) throw FallaSim(401, "SESION_VENCIDA")
        return s
    }

    private fun exigir(s: SesionViva, empresa: String?, permiso: (Permisos) -> Boolean): Usuario {
        val u = usuario(s.correo, empresa)
        if (u.empresa == null) throw FallaSim(409, "EMPRESA_REQUERIDA")
        if (!permiso(u.permisos)) throw FallaSim(403, "SIN_PERMISO")
        return u
    }

    // ───── Pruebas: herramientas para simular casos ─────

    /** Da de baja todos los teléfonos (como «Quitar» en el portal). */
    @Synchronized fun revocarDispositivos() = llaves.clear()

    /** Corta todas las sesiones (como si pasara la hora). */
    @Synchronized fun vencerSesiones() = sesiones.forEach { it.accesoExpira = Instant.EPOCH }

    // ───── §2 Cuenta ─────

    private fun liquidarPendientes() {
        for (o in ordenes.values) {
            val en = o.liquidarEn ?: continue
            if (o.orden.estado == EstadoOrden.EN_PROCESO && !en.isAfter(ahora)) {
                saldo -= o.orden.importeCentavos
                val clave = "FINSO${ahora.epochSecond}"
                o.orden = o.orden.copy(estado = EstadoOrden.LIQUIDADA, claveRastreo = clave, actualizadaEn = iso(ahora))
                o.liquidarEn = null
                movimientos.add(
                    0,
                    Movimiento(
                        id = id("mov"), sentido = "CARGO", tipo = "SPEI_SALIDA", importeCentavos = o.orden.importeCentavos,
                        saldoResultanteCentavos = saldo, fecha = iso(ahora), concepto = o.orden.concepto, claveRastreo = clave,
                        contraparte = Contraparte(o.orden.destino.titular, o.orden.destino.banco, o.orden.destino.clabeFinal),
                        orden = o.orden.id, comprobante = true,
                    ),
                )
            }
        }
    }

    private fun cuentaActual(): Cuenta {
        liquidarPendientes()
        val retenido = ordenes.values.filter { it.orden.estado == EstadoOrden.POR_LIBERAR || it.orden.estado == EstadoOrden.EN_PROCESO }
            .sumOf { it.orden.importeCentavos }
        return Cuenta("646180535000004821", "DISTRIBUIDORA DEL BAJIO SA DE CV", "VIGENTE", saldo, saldo - retenido, iso(ahora))
    }

    private fun movimientosDe(mes: String): Pair<Int, String> {
        liquidarPendientes()
        val delMes = movimientos.filter { Fechas.mesActual(Instant.parse(it.fecha)) == mes }.sortedByDescending { it.fecha }
        return ok(PaginaDeMovimientos.serializer(), PaginaDeMovimientos(mes, delMes))
    }

    // ───── §3 Destinos ─────

    private fun destinosVivos(): List<Destino> {
        for (i in destinos.indices) {
            val d = destinos[i]
            if (d.enEspera && d.activoDesde != null && !Instant.parse(d.activoDesde).isAfter(ahora)) destinos[i] = d.copy(estado = "ACTIVO")
        }
        return destinos.toList()
    }

    // ───── §4 SPEI ─────

    private fun validar(s: SesionViva, empresa: String?, sol: SolicitudSpei): Destino {
        exigir(s, empresa) { it.pedirSpei }
        val d = destinosVivos().firstOrNull { it.id == sol.destino } ?: throw FallaSim(422, "DESTINO_NO_REGISTRADO")
        if (d.esColaborador) throw FallaSim(422, "DESTINO_SOLO_NOMINA")
        if (d.enEspera) throw FallaSim(422, "DESTINO_EN_ESPERA", reintentarEn = d.activoDesde)
        if (sol.importeCentavos <= 0) throw FallaSim(400, "IMPORTE_INVALIDO")
        if (!Concepto.esValido(sol.concepto)) throw FallaSim(400, "CONCEPTO_INVALIDO")
        if (sol.importeCentavos > cuentaActual().disponibleCentavos) throw FallaSim(422, "SALDO_INSUFICIENTE")
        return d
    }

    private fun prepararSpei(s: SesionViva, empresa: String?, sol: SolicitudSpei): Pair<Int, String> {
        val d = validar(s, empresa, sol)
        val r = Base64.getUrlEncoder().withoutPadding().encodeToString(azar.nextBytes(32))
        preparados[r] = s.dispositivo to sol
        retosDeFirma[r] = s.dispositivo to ahora.plusSeconds(120)
        return ok(
            PreparacionSpei.serializer(),
            PreparacionSpei(r, iso(ahora.plusSeconds(120)), ResumenSpei(breve(d).copy(id = null), sol.importeCentavos, 0, sol.concepto)),
        )
    }

    private fun idempotente(req: Request, cuerpo: String): String? {
        val llave = req.header("Idempotency-Key") ?: throw FallaSim(400, "LLAVE_INVALIDA")
        if (!Regex("^[0-9a-fA-F-]{36}$").matches(llave)) throw FallaSim(400, "LLAVE_INVALIDA")
        val previa = porLlave[llave] ?: return null
        if (previa.first != cuerpo) throw FallaSim(409, "LLAVE_EN_CONFLICTO")
        return previa.second
    }

    private fun pedirSpei(s: SesionViva, empresa: String?, req: Request): Pair<Int, String> {
        val crudo = cuerpoCrudo(req)
        idempotente(req, crudo)?.let { return ok(Orden.serializer(), vista(ordenDe(it), s.correo), http = 201) }
        val p = runCatching { JsonFinso.decodeFromString(PeticionSpei.serializer(), crudo) }.getOrElse { throw FallaSim(400, "DATOS_INVALIDOS") }
        val (dev, sol) = preparados.remove(p.reto) ?: throw FallaSim(410, "RETO_VENCIDO")
        val vence = retosDeFirma.remove(p.reto)?.second
        if (dev != s.dispositivo || vence == null || !vence.isAfter(ahora)) throw FallaSim(410, "RETO_VENCIDO")
        if (sol != SolicitudSpei(p.destino, p.importeCentavos, p.concepto)) throw FallaSim(409, "RETO_DE_OTRA_OPERACION")
        val d = validar(s, empresa, sol)
        val texto = TextoFirmado.spei(s.dispositivo, p.reto, p.destino, p.importeCentavos, p.concepto)
        if (!verificar(llaves.getValue(s.dispositivo), texto, p.firma)) throw FallaSim(401, "FIRMA_INVALIDA")
        val idOrden = id("spo")
        val o = Orden(
            id = idOrden, estado = EstadoOrden.POR_LIBERAR, destino = breve(d), importeCentavos = p.importeCentavos,
            concepto = p.concepto, referenciaNumerica = 100L + ordenes.size, pedidaPor = nombreDe(s.correo).split(" ").take(2).joinToString(" "),
            creadaEn = iso(ahora), actualizadaEn = iso(ahora),
        )
        ordenes[idOrden] = OrdenSim(o, s.correo)
        porLlave[req.header("Idempotency-Key")!!] = crudo to idOrden
        return ok(Orden.serializer(), vista(ordenes.getValue(idOrden), s.correo), http = 201)
    }

    private fun ordenDe(id: String): OrdenSim {
        liquidarPendientes()
        return ordenes[id] ?: throw FallaSim(404, "NO_ENCONTRADA")
    }

    private fun vista(o: OrdenSim, correo: String): Orden {
        val puede = o.orden.estado == EstadoOrden.POR_LIBERAR && esAna(correo) && o.pidioCorreo != correo
        return o.orden.copy(puedoLiberar = puede)
    }

    private fun listaOrdenes(s: SesionViva, empresa: String?, estado: String?): Pair<Int, String> {
        val u = exigir(s, empresa) { it.verOrdenes }
        liquidarPendientes()
        val lista = if (estado == EstadoOrden.POR_LIBERAR) {
            if (!u.permisos.liberarSpei) emptyList() else ordenes.values.filter { it.orden.estado == EstadoOrden.POR_LIBERAR && it.pidioCorreo != s.correo }
        } else {
            ordenes.values.toList().reversed()
        }
        return ok(ListaDeOrdenes.serializer(), ListaDeOrdenes(lista.map { vista(it, s.correo) }))
    }

    private fun liberable(s: SesionViva, empresa: String?, id: String): OrdenSim {
        exigir(s, empresa) { it.liberarSpei }
        val o = ordenDe(id)
        when {
            o.orden.estado == EstadoOrden.CANCELADA -> throw FallaSim(409, "CANCELADA")
            o.orden.estado != EstadoOrden.POR_LIBERAR -> throw FallaSim(409, "YA_LIBERADA")
            o.pidioCorreo == s.correo -> throw FallaSim(409, "MISMA_PERSONA")
        }
        return o
    }

    private fun prepararLiberar(s: SesionViva, empresa: String?, id: String): Pair<Int, String> {
        val o = liberable(s, empresa, id)
        val r = Base64.getUrlEncoder().withoutPadding().encodeToString(azar.nextBytes(32))
        retosDeLiberar[r] = id
        retosDeFirma[r] = s.dispositivo to ahora.plusSeconds(120)
        return ok(PreparacionLiberar.serializer(), PreparacionLiberar(r, iso(ahora.plusSeconds(120)), o.orden.importeCentavos, o.orden.version))
    }

    private fun usarReto(s: SesionViva, reto: String, orden: String) {
        val vence = retosDeFirma.remove(reto)
        if (retosDeLiberar.remove(reto) != orden || vence == null || vence.first != s.dispositivo || !vence.second.isAfter(ahora)) {
            throw FallaSim(410, "RETO_VENCIDO")
        }
    }

    private fun liberar(s: SesionViva, empresa: String?, id: String, req: Request): Pair<Int, String> {
        val crudo = cuerpoCrudo(req)
        idempotente(req, crudo)?.let { return ok(Orden.serializer(), vista(ordenDe(it), s.correo)) }
        val p = runCatching { JsonFinso.decodeFromString(PeticionFirma.serializer(), crudo) }.getOrElse { throw FallaSim(400, "DATOS_INVALIDOS") }
        val o = liberable(s, empresa, id)
        usarReto(s, p.reto, id)
        val texto = TextoFirmado.liberar(s.dispositivo, p.reto, id, o.orden.importeCentavos)
        if (!verificar(llaves.getValue(s.dispositivo), texto, p.firma)) throw FallaSim(401, "FIRMA_INVALIDA")
        porLlave[req.header("Idempotency-Key")!!] = crudo to id
        val quien = nombreDe(s.correo)
        o.orden = if (o.orden.importeCentavos > TOPE_CENTAVOS) {
            o.orden.copy(estado = EstadoOrden.RECHAZADA, motivo = "Pasa el tope por SPEI de $890,000.00.", liberadaPor = quien, actualizadaEn = iso(ahora), version = o.orden.version + 1)
        } else {
            o.liquidarEn = ahora.plusMillis(2500)
            o.orden.copy(estado = EstadoOrden.EN_PROCESO, liberadaPor = quien, actualizadaEn = iso(ahora), version = o.orden.version + 1)
        }
        return ok(Orden.serializer(), vista(o, s.correo))
    }

    private fun rechazar(s: SesionViva, empresa: String?, id: String, req: Request): Pair<Int, String> {
        val crudo = cuerpoCrudo(req)
        idempotente(req, crudo)?.let { return ok(Orden.serializer(), vista(ordenDe(it), s.correo)) }
        val p = runCatching { JsonFinso.decodeFromString(PeticionRechazo.serializer(), crudo) }.getOrElse { throw FallaSim(400, "DATOS_INVALIDOS") }
        val motivo = p.motivo.trim()
        if (motivo.length !in 5..500) throw FallaSim(400, "DATOS_INVALIDOS")
        val o = liberable(s, empresa, id)
        usarReto(s, p.reto, id)
        if (!verificar(llaves.getValue(s.dispositivo), TextoFirmado.rechazar(s.dispositivo, p.reto, id), p.firma)) throw FallaSim(401, "FIRMA_INVALIDA")
        porLlave[req.header("Idempotency-Key")!!] = crudo to id
        o.orden = o.orden.copy(estado = EstadoOrden.RECHAZADA, motivo = motivo, actualizadaEn = iso(ahora), version = o.orden.version + 1)
        return ok(Orden.serializer(), vista(o, s.correo))
    }
}
