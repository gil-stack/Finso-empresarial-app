package mx.finsocial.empresarial.nucleo

import kotlinx.coroutines.test.runTest
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono
import mx.finsocial.empresarial.nucleo.api.Destino
import mx.finsocial.empresarial.nucleo.api.EstadoOrden
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDeSoftware
import mx.finsocial.empresarial.nucleo.api.LlaveroEnMemoria
import mx.finsocial.empresarial.nucleo.api.Movimiento
import mx.finsocial.empresarial.nucleo.api.Paso
import mx.finsocial.empresarial.nucleo.api.ServidorSimulado
import mx.finsocial.empresarial.nucleo.api.SinRed
import mx.finsocial.empresarial.nucleo.api.SolicitudSpei
import mx.finsocial.empresarial.nucleo.spei.FirmaDeOrden
import mx.finsocial.empresarial.nucleo.spei.Listas
import mx.finsocial.empresarial.nucleo.spei.PedidoSpei
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Reglas de reintento al pedir, liberar y rechazar (§3.4 y §3.5) y listas de Inicio. */
class FirmasTest {
    private var ahora = Instant.parse("2026-11-21T18:00:00Z")
    private val servidor = ServidorSimulado(reloj = { ahora }, demoraMs = 0)

    /** Corta la respuesta de la siguiente llamada a [ruta] después de que el servidor ya la procesó. */
    private var cortarDespuesDe: String? = null

    private inner class App(correo: String) {
        val guardado = Guardado(LlaveroEnMemoria())
        val llave = LlaveDeSoftware()
        val api = ApiFinso(
            "https://empresas.finsocial.app/movil/v1", "empresarial/1.0.0+1", guardado,
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val r = chain.proceed(chain.request())
                    val ruta = cortarDespuesDe
                    if (ruta != null && chain.request().url.encodedPath.endsWith(ruta)) {
                        cortarDespuesDe = null
                        r.close()
                        throw IOException("se cortó")
                    }
                    r
                })
                .addInterceptor(servidor)
                .build(),
            reloj = { ahora },
        )

        suspend fun entrar(correo: String) {
            val r = api.iniciar(correo, "secreta", DatosDelTelefono("Pixel", modelo = "Pixel 9", versionSistema = "16"))
            for (p in r.pasos) if (p == Paso.CAPTURAR_CELULAR) api.capturarCelular(r.reto, "5512345678") else api.confirmarCodigo(r.reto, p, ServidorSimulado.CODIGO)
            val publica = llave.crear()
            api.registrarDispositivo(r.reto, publica, null, llave.firmar(TextoFirmado.registrar(r.reto, TextoFirmado.huellaDeLlave(publica)), "Activar"))
            llave.firmados.clear()
        }
    }

    private suspend fun dentro(correo: String) = App(correo).also { it.entrar(correo) }

    private val solicitud = SolicitudSpei("dst_SIM_PAPELERA", 150_000, "Pago diseno ano")

    @Test fun `pedir firma una vez sin referencia y queda por liberar`() = runTest {
        val luis = dentro("luis@empresa.mx")
        val pedido = PedidoSpei(luis.api, luis.guardado, luis.llave, solicitud, reloj = { ahora })
        pedido.preparar()
        val o = pedido.pedir("Pedir SPEI")
        assertEquals(EstadoOrden.POR_LIBERAR, o.estado)
        assertEquals(1, luis.llave.firmados.size)
        val texto = luis.llave.firmados.single()
        assertTrue(texto.startsWith("finso-movil:v1:spei:${luis.guardado.dispositivo}:"))
        assertTrue(texto.endsWith(":dst_SIM_PAPELERA:150000:Pago diseno ano"))
    }

    @Test fun `si se corta la red el reintento manda lo mismo sin pedir huella y no duplica`() = runTest {
        val luis = dentro("luis@empresa.mx")
        val pedido = PedidoSpei(luis.api, luis.guardado, luis.llave, solicitud, reloj = { ahora })
        pedido.preparar()
        cortarDespuesDe = "/spei"
        try { pedido.pedir("Pedir SPEI"); fail() } catch (e: SinRed) { /* esperado */ }
        assertTrue(pedido.tieneReintento)
        val o = pedido.pedir("Pedir SPEI")
        assertEquals(1, luis.llave.firmados.size)
        val porLiberar = dentro("ana@empresa.mx").api.ordenes(EstadoOrden.POR_LIBERAR)
        assertEquals(1, porLiberar.count { it.id == o.id })
        assertEquals(2, porLiberar.size) // la de Luis de los datos simulados y esta
    }

    @Test fun `otro error gasta el reto y el siguiente intento prepara otro`() = runTest {
        val luis = dentro("luis@empresa.mx")
        val pedido = PedidoSpei(luis.api, luis.guardado, luis.llave, solicitud, reloj = { ahora })
        val primero = pedido.preparar().reto
        luis.llave.respuesta = null
        // Reto vencido en pantalla: se prepara otro antes de firmar.
        ahora = ahora.plusSeconds(200)
        val o = pedido.pedir("Pedir SPEI")
        assertFalse(luis.llave.firmados.single().contains(":$primero:"))
        assertEquals(EstadoOrden.POR_LIBERAR, o.estado)
    }

    @Test fun `liberar comprueba la version, firma y reintenta sin duplicar`() = runTest {
        val ana = dentro("ana@empresa.mx")
        val orden = ana.api.orden("spo_SIM_PENDIENTE")
        val cambiada = FirmaDeOrden(ana.api, ana.guardado, ana.llave, orden.copy(importeCentavos = 1), reloj = { ahora })
        try { cambiada.liberar("Liberar"); fail() } catch (e: FallaApi) { assertEquals("ORDEN_CAMBIO", e.codigo) }
        assertTrue(ana.llave.firmados.isEmpty())

        val firma = FirmaDeOrden(ana.api, ana.guardado, ana.llave, orden, reloj = { ahora })
        cortarDespuesDe = "/liberar"
        try { firma.liberar("Liberar"); fail() } catch (e: SinRed) { /* esperado */ }
        val liberada = firma.liberar("Liberar")
        assertEquals(EstadoOrden.EN_PROCESO, liberada.estado)
        assertEquals(1, ana.llave.firmados.size)
        assertTrue(ana.llave.firmados.single().startsWith("finso-movil:v1:liberar:"))
    }

    @Test fun `rechazar firma rechazar y valida el motivo`() = runTest {
        val ana = dentro("ana@empresa.mx")
        assertFalse(FirmaDeOrden.motivoValido("no"))
        assertTrue(FirmaDeOrden.motivoValido("Factura duplicada"))
        val firma = FirmaDeOrden(ana.api, ana.guardado, ana.llave, ana.api.orden("spo_SIM_PENDIENTE"), reloj = { ahora })
        val r = firma.rechazar("Factura duplicada", "Rechazar")
        assertEquals(EstadoOrden.RECHAZADA, r.estado)
        assertEquals("Factura duplicada", r.motivo)
        assertTrue(ana.llave.firmados.single().startsWith("finso-movil:v1:rechazar:"))
    }

    @Test fun `movimientos por dia y destinos ordenados`() {
        fun m(id: String, fecha: String) = Movimiento(id, "ABONO", "SPEI", 100, fecha = fecha)
        val dias = Listas.porDia(
            listOf(m("a", "2026-11-19T20:00:00Z"), m("b", "2026-11-21T17:00:00Z"), m("c", "2026-11-21T05:00:00Z"), m("d", "2026-11-20T23:00:00Z")),
            ahora,
        )
        // 05:00Z del 21 es 23:00 del 20 en México.
        assertEquals(listOf("Hoy", "Ayer", "19 nov 2026"), dias.map { it.titulo })
        assertEquals(listOf("b"), dias[0].movimientos.map { it.id })
        assertEquals(listOf("c", "d"), dias[1].movimientos.map { it.id })

        val destinos = listOf(
            Destino("1", "ZAPATOS SA", "PROVEEDOR", "BBVA", "0001", "EN_ESPERA", "2026-11-21T18:18:00Z"),
            Destino("2", "LUCIA HERNANDEZ", "COLABORADOR", null, "0002", "ACTIVO"),
            Destino("3", "PAPELERA DEL CENTRO", "PROVEEDOR", "BBVA MEXICO", "4821", "ACTIVO"),
            Destino("4", "AGUA PURA", "PROVEEDOR", "BANORTE", "0921", "ACTIVO"),
        )
        assertEquals(listOf("4", "3", "2", "1"), Listas.destinosOrdenados(destinos).map { it.id })
        assertEquals("Nueva · se activa en 18 min", Listas.lineaDeDestino(destinos[0], ahora))
        assertEquals("Colaborador · se paga desde Nómina en el portal", Listas.lineaDeDestino(destinos[1], ahora))
        assertEquals("Proveedor · BANORTE · •••• 0921", Listas.lineaDeDestino(destinos[3], ahora))
        assertEquals(Instant.parse("2026-11-21T18:18:00Z"), Listas.proximaActivacion(destinos, ahora))
    }
}
