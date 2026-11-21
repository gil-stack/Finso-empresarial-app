package mx.finsocial.empresarial.nucleo

import kotlinx.coroutines.test.runTest
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDeSoftware
import mx.finsocial.empresarial.nucleo.api.LlaveroEnMemoria
import mx.finsocial.empresarial.nucleo.api.Paso
import mx.finsocial.empresarial.nucleo.api.PeticionSpei
import mx.finsocial.empresarial.nucleo.api.ServidorSimulado
import mx.finsocial.empresarial.nucleo.api.SolicitudSpei
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Caso 7 de la especificación compartida: la API simulada, por el mismo cliente HTTP. */
class ServidorSimuladoTest {
    private var ahora = Instant.parse("2026-11-21T18:00:00Z")
    private val servidor = ServidorSimulado(reloj = { ahora }, demoraMs = 0)
    private val telefono = DatosDelTelefono("Pixel de Ana", modelo = "Pixel 9", versionSistema = "16")

    private fun nuevaApp(): Triple<ApiFinso, Guardado, LlaveDeSoftware> {
        val g = Guardado(LlaveroEnMemoria())
        val cliente = OkHttpClient.Builder().addInterceptor(servidor).build()
        return Triple(ApiFinso("https://empresas.finsocial.app/movil/v1", "empresarial/1.0.0+1", g, cliente, reloj = { ahora }), g, LlaveDeSoftware())
    }

    private inline fun falla(codigo: String, bloque: () -> Unit) {
        try { bloque(); fail("Esperaba $codigo") } catch (e: FallaApi) { assertEquals(codigo, e.codigo) }
    }

    /** Hace el primer ingreso completo y regresa la app adentro. */
    private suspend fun entrar(correo: String): Triple<ApiFinso, Guardado, LlaveDeSoftware> {
        val (api, g, llave) = nuevaApp()
        val r = api.iniciar(correo, "secreta", telefono)
        for (paso in r.pasos) {
            when (paso) {
                Paso.CAPTURAR_CELULAR -> api.capturarCelular(r.reto, "55 1234 5678")
                else -> api.confirmarCodigo(r.reto, paso, ServidorSimulado.CODIGO)
            }
        }
        val publica = llave.crear()
        val firma = llave.firmar(TextoFirmado.registrar(r.reto, TextoFirmado.huellaDeLlave(publica)), "Activar")
        api.registrarDispositivo(r.reto, publica, "Pixel", firma)
        return Triple(api, g, llave)
    }

    @Test fun `primer ingreso - correo primero, el SMS sale solo y registro con firma`() = runTest {
        val (api, g, llave) = nuevaApp()
        val r = api.iniciar("ana@empresa.mx", "secreta", telefono)
        assertEquals(listOf(Paso.CODIGO_CORREO, Paso.CODIGO_SMS), r.pasos)
        falla("PASO_FUERA_DE_ORDEN") { api.confirmarCodigo(r.reto, Paso.CODIGO_SMS, "123456") }
        val c = api.confirmarCodigo(r.reto, Paso.CODIGO_CORREO, "123456")
        assertEquals(listOf(Paso.CODIGO_SMS), c.pasosPendientes)
        assertNotNull(c.reenvioDisponibleEn)
        assertEquals("•••• ••• 4821", c.celularEnmascarado)
        api.confirmarCodigo(r.reto, Paso.CODIGO_SMS, "123456")
        val publica = llave.crear()
        falla("FIRMA_INVALIDA") { api.registrarDispositivo(r.reto, publica, null, llave.firmar("otro texto", "x")) }
        val firma = llave.firmar(TextoFirmado.registrar(r.reto, TextoFirmado.huellaDeLlave(publica)), "Activar")
        val reg = api.registrarDispositivo(r.reto, publica, null, firma)
        assertTrue(reg.dispositivo.startsWith("dev_"))
        assertEquals(reg.dispositivo, g.dispositivo)
        assertEquals("Ana", g.nombreCorto)
        assertEquals("Ana López Garza", api.yo().nombre)
    }

    @Test fun `codigo equivocado dice cuantos intentos quedan y al quinto vuelve a empezar`() = runTest {
        val (api) = nuevaApp()
        val r = api.iniciar("ana@empresa.mx", "x", telefono)
        for (quedan in 4 downTo 1) {
            try { api.confirmarCodigo(r.reto, Paso.CODIGO_CORREO, "000000"); fail() } catch (e: FallaApi) {
                assertEquals(quedan, e.intentosRestantes)
            }
        }
        try { api.confirmarCodigo(r.reto, Paso.CODIGO_CORREO, "000000"); fail() } catch (e: FallaApi) {
            assertEquals("DEMASIADOS_INTENTOS", e.codigo)
            assertTrue(e.reiniciaElIngreso)
        }
    }

    @Test fun `reenviar admite maximo 3 envios`() = runTest {
        val (api) = nuevaApp()
        val r = api.iniciar("ana@empresa.mx", "x", telefono)
        api.reenviarCodigo(r.reto, Paso.CODIGO_CORREO)
        api.reenviarCodigo(r.reto, Paso.CODIGO_CORREO)
        falla("DEMASIADOS_INTENTOS") { api.reenviarCodigo(r.reto, Paso.CODIGO_CORREO) }
    }

    @Test fun `sin celular se captura despues del correo`() = runTest {
        val (api) = nuevaApp()
        val r = api.iniciar("nuevo@empresa.mx", "x", telefono)
        assertEquals(listOf(Paso.CODIGO_CORREO, Paso.CAPTURAR_CELULAR, Paso.CODIGO_SMS), r.pasos)
        falla("PASO_FUERA_DE_ORDEN") { api.capturarCelular(r.reto, "5512345678") }
        api.confirmarCodigo(r.reto, Paso.CODIGO_CORREO, "123456")
        falla("CELULAR_INVALIDO") { api.capturarCelular(r.reto, "123") }
        assertEquals("•••• ••• 5678", api.capturarCelular(r.reto, "+52 55 1234 5678").celularEnmascarado)
    }

    @Test fun `credenciales invalidas`() = runTest {
        val (api) = nuevaApp()
        falla("CREDENCIALES_INVALIDAS") { api.iniciar("no-es-correo", "x", telefono) }
    }

    @Test fun `sin sesion no hay cuenta`() = runTest {
        val (api) = nuevaApp()
        falla("SESION_VENCIDA") { api.cuenta() }
    }

    @Test fun `cuenta, movimientos y destinos`() = runTest {
        val (api) = entrar("ana@empresa.mx")
        val c = api.cuenta().cuenta!!
        assertEquals("646180535000004821", c.clabe)
        assertEquals(124_821_537L, c.saldoCentavos)
        assertEquals(124_821_537L - 1_845_000L, c.disponibleCentavos)
        val movs = api.movimientos(Fechas.mesActual(ahora)).elementos
        assertTrue(movs.isNotEmpty())
        assertEquals(movs.sortedByDescending { it.fecha }, movs)
        val d = api.destinos()
        assertEquals(4, d.size)
        assertTrue(d.first { it.id == "dst_SIM_EMPAQUES" }.enEspera)
    }

    @Test fun `el solicitante no ve saldo ni libera`() = runTest {
        val (api) = entrar("luis@empresa.mx")
        val u = api.yo()
        assertFalse(u.permisos.verCuenta)
        assertFalse(u.permisos.liberarSpei)
        falla("SIN_PERMISO") { api.cuenta() }
        assertTrue(api.ordenes(EstadoOrden.POR_LIBERAR).isEmpty())
        falla("SIN_PERMISO") { api.prepararLiberacion("spo_SIM_PENDIENTE") }
    }

    @Test fun `varias empresas - hay que elegir`() = runTest {
        val (api, g) = entrar("varias@empresa.mx")
        val u = api.yo()
        assertNull(u.empresa)
        assertTrue(u.debeElegirEmpresa)
        falla("EMPRESA_REQUERIDA") { api.destinos() }
        assertEquals("Transportes del Bajío", api.elegirEmpresa("emp_SIM2").empresa!!.nombre)
        assertEquals("emp_SIM2", g.empresa)
        assertEquals(4, api.destinos().size)
    }

    @Test fun `destino en espera se activa a los 30 minutos y colaborador es solo nomina`() = runTest {
        val (api) = entrar("ana@empresa.mx")
        falla("DESTINO_EN_ESPERA") { api.prepararSpei(SolicitudSpei("dst_SIM_EMPAQUES", 1000, "Cajas")) }
        falla("DESTINO_SOLO_NOMINA") { api.prepararSpei(SolicitudSpei("dst_SIM_LUCIA", 1000, "Sueldo")) }
        ahora = ahora.plus(Duration.ofMinutes(19))
        servidor.vencerSesiones()
        // La sesión venció: el cliente renueva solo y sigue.
        assertEquals("ACTIVO", api.destinos().first { it.id == "dst_SIM_EMPAQUES" }.estado)
    }

    @Test fun `concepto invalido y saldo insuficiente`() = runTest {
        val (api) = entrar("ana@empresa.mx")
        falla("CONCEPTO_INVALIDO") { api.prepararSpei(SolicitudSpei("dst_SIM_PAPELERA", 1000, "Pago diseño")) }
        falla("SALDO_INSUFICIENTE") { api.prepararSpei(SolicitudSpei("dst_SIM_PAPELERA", 999_999_999, "Pago")) }
    }

    private suspend fun pedir(api: ApiFinso, g: Guardado, llave: LlaveDeSoftware, importe: Long, llaveIdem: String = ApiFinso.nuevaLlave()) =
        api.prepararSpei(SolicitudSpei("dst_SIM_PAPELERA", importe, "Pago factura 118")).let { p ->
            val firma = llave.firmar(TextoFirmado.spei(g.dispositivo!!, p.reto, "dst_SIM_PAPELERA", importe, "Pago factura 118"), "Mandar")
            PeticionSpei("dst_SIM_PAPELERA", importe, "Pago factura 118", p.reto, firma) to llaveIdem
        }

    @Test fun `todo SPEI queda por liberar y la llave evita duplicados`() = runTest {
        val (api, g, llave) = entrar("luis@empresa.mx")
        val (peticion, idem) = pedir(api, g, llave, 150_000)
        val o = api.pedirSpei(peticion, idem)
        assertEquals(EstadoOrden.POR_LIBERAR, o.estado)
        assertNotNull(o.referenciaNumerica)
        assertFalse(o.puedoLiberar)
        // Reintento por red cortada: misma llave y mismo cuerpo → la misma orden, sin firmar otra vez.
        assertEquals(o.id, api.pedirSpei(peticion, idem).id)
        falla("LLAVE_EN_CONFLICTO") { api.pedirSpei(peticion.copy(importeCentavos = 1), idem) }
        // El reto ya se gastó: con otra llave no se puede reutilizar.
        falla("RETO_VENCIDO") { api.pedirSpei(peticion, ApiFinso.nuevaLlave()) }
    }

    @Test fun `liberar y rechazar firmados y el tope del core se ve al liberar`() = runTest {
        val (luis, gl, ll) = entrar("luis@empresa.mx")
        val (p1, k1) = pedir(luis, gl, ll, 150_000)
        val chica = luis.pedirSpei(p1, k1)
        val (ana, ga, la) = entrar("ana@empresa.mx")
        val porLiberar = ana.ordenes(EstadoOrden.POR_LIBERAR)
        assertTrue(porLiberar.any { it.id == chica.id && it.puedoLiberar })

        // Quien pidió no libera.
        falla("SIN_PERMISO") { luis.prepararLiberacion(chica.id) }

        val prep = ana.prepararLiberacion(chica.id)
        val firma = la.firmar(TextoFirmado.liberar(ga.dispositivo!!, prep.reto, chica.id, prep.importeCentavos), "Liberar")
        val liberada = ana.liberar(chica.id, prep.reto, firma, ApiFinso.nuevaLlave())
        assertEquals(EstadoOrden.EN_PROCESO, liberada.estado)
        assertEquals("Ana López Garza", liberada.liberadaPor)
        ahora = ahora.plusSeconds(3)
        assertEquals(EstadoOrden.LIQUIDADA, ana.orden(chica.id).estado)

        // Rechazar: motivo corto da error; con motivo queda RECHAZADA.
        val prep2 = ana.prepararLiberacion("spo_SIM_PENDIENTE")
        val f2 = la.firmar(TextoFirmado.rechazar(ga.dispositivo!!, prep2.reto, "spo_SIM_PENDIENTE"), "Rechazar")
        falla("DATOS_INVALIDOS") { ana.rechazar("spo_SIM_PENDIENTE", "no", prep2.reto, f2, ApiFinso.nuevaLlave()) }
        val r = ana.rechazar("spo_SIM_PENDIENTE", "Factura duplicada", prep2.reto, f2, ApiFinso.nuevaLlave())
        assertEquals(EstadoOrden.RECHAZADA, r.estado)
        assertEquals("Factura duplicada", r.motivo)

        // Tope del core: se pide, pero al liberar queda RECHAZADA con motivo.
        val (p3, k3) = pedir(luis, gl, ll, 95_000_000)
        val grande = luis.pedirSpei(p3, k3)
        val prep3 = ana.prepararLiberacion(grande.id)
        val f3 = la.firmar(TextoFirmado.liberar(ga.dispositivo!!, prep3.reto, grande.id, prep3.importeCentavos), "Liberar")
        val tope = ana.liberar(grande.id, prep3.reto, f3, ApiFinso.nuevaLlave())
        assertEquals(EstadoOrden.RECHAZADA, tope.estado)
        assertEquals("Pasa el tope por SPEI de $890,000.00.", tope.motivo)
    }

    @Test fun `entrar con huella firma el reto y un telefono dado de baja olvida todo`() = runTest {
        val (api, g, llave) = entrar("ana@empresa.mx")
        api.salir()
        assertNull(g.sesion)
        val reto = api.retoParaEntrar()
        val firma = llave.firmar(TextoFirmado.entrar(g.dispositivo!!, reto.reto), "Entrar")
        api.entrarConBiometria(reto.reto, firma)
        assertNotNull(g.sesion)
        servidor.revocarDispositivos()
        falla("DISPOSITIVO_REVOCADO") { api.yo() }
        assertNull(g.dispositivo)
    }
}
