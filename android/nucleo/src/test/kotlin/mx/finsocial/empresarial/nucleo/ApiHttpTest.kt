package mx.finsocial.empresarial.nucleo

import kotlinx.coroutines.test.runTest
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveroEnMemoria
import mx.finsocial.empresarial.nucleo.api.PeticionSpei
import mx.finsocial.empresarial.nucleo.api.Sesion
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** Caso 6 de la especificación compartida: el cliente HTTP contra un servidor de mentira. */
class ApiHttpTest {
    private val servidor = MockWebServer()
    private val ahora = Instant.parse("2026-11-21T18:00:00Z")
    private lateinit var guardado: Guardado
    private lateinit var api: ApiFinso
    private val fallas = mutableListOf<String>()

    @Before fun armar() {
        servidor.start()
        guardado = Guardado(LlaveroEnMemoria())
        guardado.dispositivo = "dev_1"
        guardado.sesion = Sesion("acc_1", "2026-11-21T18:10:00Z", "ren_1", "2026-11-21T18:50:00Z")
        api = ApiFinso(servidor.url("/movil/v1/").toString(), "empresarial/1.0.0+7", guardado, reloj = { ahora })
        api.alFallarSesion = { fallas += it.codigo }
    }

    @After fun cerrar() = servidor.close()

    private fun responder(codigo: Int, cuerpo: String) =
        servidor.enqueue(MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo).build())

    private val yo = """{"id":"usr_1","nombre":"Ana López","correo":"ana@e.mx","papeles":["ADMINISTRADOR"],"empresa":{"id":"emp_1","nombre":"X"},"empresas":[],"permisos":{"verCuenta":true},"operacionDelDueno":false}"""

    @Test fun `manda los encabezados del contrato y el token`() = runTest {
        responder(200, yo)
        val u = api.yo()
        assertEquals("Ana López", u.nombre)
        val r = servidor.takeRequest()
        assertEquals("/movil/v1/yo", r.url.encodedPath)
        assertEquals("empresarial/1.0.0+7", r.headers["X-Finso-App"])
        assertEquals("android", r.headers["X-Finso-Plataforma"])
        assertEquals("dev_1", r.headers["X-Finso-Dispositivo"])
        assertEquals("Bearer acc_1", r.headers["Authorization"])
        assertNull(r.headers["X-Finso-Empresa"])
    }

    @Test fun `con SESION_VENCIDA renueva una vez y repite`() = runTest {
        responder(401, """{"codigo":"SESION_VENCIDA"}""")
        responder(200, """{"acceso":"acc_2","accesoExpiraEn":"2026-11-21T18:10:00Z","renovacion":"ren_2","renovacionExpiraEn":"2026-11-21T18:50:00Z"}""")
        responder(200, yo)
        api.yo()
        servidor.takeRequest()
        val renovar = servidor.takeRequest()
        assertEquals("/movil/v1/sesion/renovar", renovar.url.encodedPath)
        assertTrue(renovar.body!!.utf8().contains("ren_1"))
        assertEquals("Bearer acc_2", servidor.takeRequest().headers["Authorization"])
        assertEquals("ren_2", guardado.sesion!!.renovacion)
        assertTrue(fallas.isEmpty())
    }

    @Test fun `SESION_REVOCADA borra la sesion y avisa`() = runTest {
        responder(401, """{"codigo":"SESION_REVOCADA"}""")
        try { api.yo(); fail() } catch (e: FallaApi) { assertEquals("SESION_REVOCADA", e.codigo) }
        assertNull(guardado.sesion)
        assertEquals(listOf("SESION_REVOCADA"), fallas)
        assertEquals("dev_1", guardado.dispositivo)
    }

    @Test fun `pasada la hora desde la huella no renueva y pide huella`() = runTest {
        guardado.sesion = Sesion("acc_1", "2026-11-21T17:10:00Z", "ren_1", "2026-11-21T17:59:00Z")
        try { api.yo(); fail() } catch (e: FallaApi) { assertTrue(e.pideBiometria) }
        assertEquals(0, servidor.requestCount)
        assertNull(guardado.sesion)
    }

    @Test fun `el acceso vencido se renueva antes de llamar`() = runTest {
        guardado.sesion = Sesion("acc_1", "2026-11-21T17:59:00Z", "ren_1", "2026-11-21T18:50:00Z")
        responder(200, """{"acceso":"acc_2","accesoExpiraEn":"2026-11-21T18:10:00Z","renovacion":"ren_2","renovacionExpiraEn":"2026-11-21T18:50:00Z"}""")
        responder(200, yo)
        api.yo()
        assertEquals("/movil/v1/sesion/renovar", servidor.takeRequest().url.encodedPath)
    }

    @Test fun `los errores traen el mensaje del servidor o uno propio`() = runTest {
        responder(422, """{"codigo":"SALDO_INSUFICIENTE","mensaje":"Te faltan $10.00"}""")
        try { api.yo(); fail() } catch (e: FallaApi) { assertEquals("Te faltan $10.00", e.texto) }
        responder(400, """{"codigo":"CODIGO_INCORRECTO","intentosRestantes":2}""")
        try { api.confirmarCodigo("rto", "CODIGO_SMS", "1"); fail() } catch (e: FallaApi) {
            assertEquals("El código no es correcto. Te quedan 2 intentos.", e.texto)
            assertEquals(2, e.intentosRestantes)
        }
        responder(500, "<html>")
        try { api.yo(); fail() } catch (e: FallaApi) { assertEquals(Textos.OTRO, e.texto) }
    }

    @Test fun `SPEI lleva Idempotency-Key y no lleva referenciaNumerica`() = runTest {
        responder(201, """{"id":"spo_1","estado":"POR_LIBERAR","destino":{"id":"dst_1","titular":"P","banco":"B","clabeFinal":"0921"},"importeCentavos":150000,"concepto":"Pago","referenciaNumerica":118,"pedidaPor":"Ana","creadaEn":"2026-11-21T18:00:00Z","actualizadaEn":"2026-11-21T18:00:00Z","version":1}""")
        val o = api.pedirSpei(PeticionSpei("dst_1", 150000, "Pago", "R", "F"), "6f1c1d1e-9b0a-4c55-8d55-2f5a1b2c3d4e")
        assertEquals(118L, o.referenciaNumerica)
        val r = servidor.takeRequest()
        assertEquals("6f1c1d1e-9b0a-4c55-8d55-2f5a1b2c3d4e", r.headers["Idempotency-Key"])
        val cuerpo = r.body!!.utf8()
        assertFalse(cuerpo.contains("referencia"))
        assertTrue(cuerpo.contains("\"importeCentavos\":150000"))
    }

    @Test fun `X-Finso-Empresa cuando se eligio y EMPRESA_REQUERIDA borra la eleccion`() = runTest {
        responder(200, yo)
        api.elegirEmpresa("emp_2")
        assertEquals("emp_2", servidor.takeRequest().headers["X-Finso-Empresa"])
        responder(409, """{"codigo":"EMPRESA_REQUERIDA"}""")
        try { api.cuenta(); fail() } catch (e: FallaApi) { assertEquals("EMPRESA_REQUERIDA", e.codigo) }
        assertNull(guardado.empresa)
        assertEquals(listOf("EMPRESA_REQUERIDA"), fallas)
    }

    @Test fun `DISPOSITIVO_REVOCADO olvida el telefono y deja el aviso`() = runTest {
        responder(401, """{"codigo":"DISPOSITIVO_REVOCADO"}""")
        try { api.yo(); fail() } catch (e: FallaApi) { assertTrue(e.olvidaElTelefono) }
        assertNull(guardado.dispositivo)
        assertNull(guardado.sesion)
        assertEquals("Este teléfono se dio de baja. Vuelve a registrarlo.", guardado.avisoDeSalida)
    }

    @Test fun `sin red da SinRed`() = runTest {
        servidor.close()
        try { api.yo(); fail() } catch (e: FallaApi) { assertEquals("SIN_RED", e.codigo); assertEquals(Textos.SIN_RED, e.texto) }
    }
}
