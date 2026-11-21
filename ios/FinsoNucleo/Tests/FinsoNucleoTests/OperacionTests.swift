import XCTest
@testable import FinsoNucleo

/// Casos 16 a 21 de la especificación §8, sobre la lógica.
@MainActor
final class OperacionTests: XCTestCase {
    var api: ApiSimulada!
    var almacen: AlmacenEnMemoria!
    var llave: LlaveSimulada!
    var control: ControlDeSesion!

    override func setUp() async throws {
        api = ApiSimulada(demora: 0, liquidaEn: 0.05)
        almacen = AlmacenEnMemoria()
        llave = LlaveSimulada()
        control = ControlDeSesion(api: api, almacen: almacen, llave: llave)
        control.arrancar()
    }

    func entrar(_ correo: String) async -> Usuario {
        let f = FlujoDeEntrada(control: control, telefono: DatosDelTelefono(nombre: "iPhone"), esperar: { _ in })
        await f.continuar(correo: correo, contrasena: "x")
        while f.paso != .biometria { await f.confirmar(codigo: "123456") }
        await f.activarBiometria()
        return control.estado.usuario!
    }

    func envio(disponible: Int?) -> EnvioSpei {
        EnvioSpei(api: api, almacen: almacen, llave: llave, disponible: disponible, esperar: { _ in })
    }

    func testInicioDeAna() async {
        let u = await entrar("ana@empresa.mx")
        let d = DatosDeInicio(api: api, usuario: u)
        await d.refrescar()
        XCTAssertNil(d.error)
        XCTAssertEqual(d.cuenta?.saldoCentavos, 124_821_537)
        XCTAssertEqual(d.cuenta?.clabeAgrupada, "6461 8053 5000 0048 21")
        XCTAssertFalse(d.dias.isEmpty)
        XCTAssertEqual(d.dias.first?.titulo, "Hoy")
        XCTAssertEqual(d.porLiberar.map(\.id), ["spo_SIM_PENDIENTE"])
        XCTAssertTrue(d.puedeMandar)
        XCTAssertNotNil(d.mesAnterior)
        let antes = d.movimientos.count
        await d.cargarMas()
        XCTAssertEqual(d.meses.count, 2)
        XCTAssertGreaterThanOrEqual(d.movimientos.count, antes)
    }

    func testSolicitanteNoVeSaldo() async {
        let u = await entrar("luis@empresa.mx")
        let d = DatosDeInicio(api: api, usuario: u)
        await d.refrescar()
        XCTAssertNil(d.error)
        XCTAssertNil(d.cuenta)
        XCTAssertTrue(d.movimientos.isEmpty)
        XCTAssertTrue(d.porLiberar.isEmpty)
        XCTAssertTrue(d.puedeMandar)
        XCTAssertNil(d.mesAnterior)
    }

    func testDestinos() async {
        _ = await entrar("luis@empresa.mx")
        let e = envio(disponible: nil)
        await e.cargarDestinos()
        let orden = e.destinosOrdenados
        XCTAssertEqual(orden.prefix(2).map(\.id).sorted(), ["dst_SIM_FLETES", "dst_SIM_PAPELERA"])
        let lucia = orden.first { $0.id == "dst_SIM_LUCIA" }!
        let empaques = orden.first { $0.id == "dst_SIM_EMPAQUES" }!
        XCTAssertEqual(e.estado(de: lucia), .soloNomina)
        guard case .enEspera = e.estado(de: empaques) else { return XCTFail() }
        XCTAssertTrue(e.estado(de: empaques).texto(empaques).hasPrefix("Nueva · se activa en "))
        e.elegir(lucia)
        XCTAssertEqual(e.paso, .destino)
        e.elegir(orden[0])
        XCTAssertEqual(e.paso, .importe)
    }

    func testPedirSpeiQuedaPorLiberar() async {
        _ = await entrar("luis@empresa.mx")
        let e = envio(disponible: nil)
        await e.cargarDestinos()
        e.elegir(e.destinos.first { $0.id == "dst_SIM_PAPELERA" }!)
        e.teclearImporte("1500")
        XCTAssertEqual(e.importeTecleado, "1,500")
        e.teclearConcepto("Pago diseño año ")
        XCTAssertEqual(e.concepto, "Pago diseno ano ")
        XCTAssertEqual(e.conceptoFinal, "Pago diseno ano")
        XCTAssertTrue(e.puedeRevisar)
        await e.revisar()
        XCTAssertEqual(e.paso, .revisar)
        XCTAssertEqual(e.preparacion?.importeCentavos, 150_000)
        XCTAssertEqual(e.motivoDeFirma, "Mandar $1,500.00 a Papelera del Centro SA de CV")
        await e.pedir()
        XCTAssertNil(e.error)
        XCTAssertEqual(e.paso, .resultado)
        XCTAssertEqual(e.orden?.estado, .porLiberar)
        XCTAssertEqual(e.orden?.estado.tituloDeResultado, "Listo, falta liberarlo")
        XCTAssertNotNil(e.orden?.referenciaNumerica)
        let firmado = llave.firmados.last!
        XCTAssertTrue(firmado.hasSuffix(":dst_SIM_PAPELERA:150000:Pago diseno ano"))
    }

    func testNoPasaDelDisponible() async {
        _ = await entrar("ana@empresa.mx")
        let e = envio(disponible: 10_000)
        await e.cargarDestinos()
        e.elegir(e.destinosOrdenados[0])
        e.teclearImporte("100.01")
        e.teclearConcepto("Pago")
        XCTAssertTrue(e.pasaDelDisponible)
        XCTAssertFalse(e.puedeRevisar)
        e.teclearImporte("100.0")
        XCTAssertTrue(e.puedeRevisar)
    }

    func testCancelarFaceIdNoManda() async {
        _ = await entrar("luis@empresa.mx")
        let e = envio(disponible: nil)
        await e.cargarDestinos()
        e.elegir(e.destinosOrdenados[0])
        e.teclearImporte("10")
        e.teclearConcepto("Prueba")
        await e.revisar()
        llave.siguienteFallo = .cancelada
        await e.pedir()
        XCTAssertNil(e.error)
        XCTAssertEqual(e.paso, .revisar)
        XCTAssertNil(e.orden)
    }

    func testLiberarYRechazar() async throws {
        _ = await entrar("luis@empresa.mx")
        let e = envio(disponible: nil)
        await e.cargarDestinos()
        e.elegir(e.destinos.first { $0.id == "dst_SIM_FLETES" }!)
        e.teclearImporte("2000")
        e.teclearConcepto("Flete")
        await e.revisar()
        await e.pedir()
        let pedida = e.orden!

        _ = await entrar("ana@empresa.mx")
        let lista = try await api.porLiberar()
        let o = lista.first { $0.id == pedida.id }!
        XCTAssertTrue(o.puedoLiberar)
        let l = Liberacion(orden: o, api: api, almacen: almacen, llave: llave, esperar: { _ in
            try? await Task.sleep(nanoseconds: 100_000_000)
        })
        await l.liberar()
        XCTAssertNil(l.error)
        XCTAssertTrue(l.terminada)
        XCTAssertEqual(l.orden.estado, .enProceso)
        XCTAssertTrue(llave.firmados.last!.hasPrefix("finso-movil:v1:liberar:"))
        await l.seguir()
        XCTAssertEqual(l.orden.estado, .liquidada)
        XCTAssertEqual(l.orden.liberadaPor, "Ana López Garza")

        let pendiente = try await api.orden("spo_SIM_PENDIENTE")
        let r = Liberacion(orden: pendiente, api: api, almacen: almacen, llave: llave, esperar: { _ in })
        XCTAssertFalse(Liberacion.motivoValido("no"))
        await r.rechazar(motivo: "No corresponde a la semana")
        XCTAssertEqual(r.orden.estado, .rechazada)
        XCTAssertEqual(r.orden.motivo, "No corresponde a la semana")
        XCTAssertTrue(llave.firmados.last!.hasPrefix("finso-movil:v1:rechazar:"))
    }

    /// Si falla la red, el reintento usa la misma llave y la misma firma, sin
    /// volver a pedir Face ID.
    func testReintentoPorRedReusaLlaveYFirma() async {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(Sesion(
            acceso: "a", accesoExpiraEn: Date() + 600, renovacion: "r", renovacionExpiraEn: Date() + 3600))
        almacen.guardarDispositivo("dev_1")
        let prep: Json = [
            "reto": "s1", "expiraEn": Iso.texto(Date() + 120),
            "resumen": [
                "destino": ["id": "dst_1", "titular": "PAPELERA", "clabeFinal": "4821"] as Json,
                "importeCentavos": 1_000, "concepto": "Pago",
            ] as Json,
        ]
        let orden: Json = [
            "id": "spo_1", "estado": "POR_LIBERAR",
            "destino": ["id": "dst_1", "titular": "PAPELERA", "clabeFinal": "4821"] as Json,
            "importeCentavos": 1_000, "concepto": "Pago", "referenciaNumerica": 7, "pedidaPor": "Luis",
            "creadaEn": Iso.texto(Date()), "actualizadaEn": Iso.texto(Date()), "version": 1,
        ]
        let destinos: Json = ["elementos": [
            ["id": "dst_1", "titular": "PAPELERA", "tipo": "PROVEEDOR", "clabeFinal": "4821", "estado": "ACTIVO",
             "activoDesde": Iso.texto(Date() - 86_400)] as Json,
        ]]
        // destinos, preparar, (sin red), (sin red al reintentar no hay: contesta 201)
        let t = TransporteFalso([(200, destinos), (200, prep)])
        let api = ApiHttp(almacen: almacen, base: URL(string: "https://x.mx/movil/v1")!, version: "1", transporte: t)
        let llave = LlaveSimulada(existe: true)
        let e = EnvioSpei(api: api, almacen: almacen, llave: llave, disponible: nil, esperar: { _ in })
        await e.cargarDestinos()
        e.elegir(e.destinos[0])
        e.teclearImporte("10")
        e.teclearConcepto("Pago")
        await e.revisar()
        await e.pedir()  // sin respuestas: falla la red
        XCTAssertEqual(e.error, ErrorFinso(.sinRed).paraPersona)
        XCTAssertEqual(llave.firmados.count, 1)
        t.respuestas = [(201, orden)]
        await e.pedir()
        XCTAssertEqual(e.paso, .resultado)
        XCTAssertEqual(llave.firmados.count, 1, "No vuelve a pedir Face ID")
        let mandados = t.pedidos.filter { $0.url.path.hasSuffix("/spei") }
        XCTAssertEqual(mandados.count, 2)
        XCTAssertEqual(mandados[0].encabezados["Idempotency-Key"], mandados[1].encabezados["Idempotency-Key"])
        XCTAssertEqual(mandados[0].cuerpo, mandados[1].cuerpo)
    }
}
