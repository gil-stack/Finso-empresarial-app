import XCTest
@testable import FinsoNucleo

/// Transporte falso: contesta en orden y guarda lo que se pidió.
final class TransporteFalso: Transporte, @unchecked Sendable {
    var respuestas: [(Int, Any?)]
    var pedidos: [Pedido] = []
    private let lock = NSLock()

    init(_ respuestas: [(Int, Any?)]) { self.respuestas = respuestas }

    func enviar(_ pedido: Pedido) async throws -> (Int, Data) {
        try lock.con {
            pedidos.append(pedido)
            guard !respuestas.isEmpty else { throw ErrorFinso(.sinRed) }
            let (codigo, cuerpo) = respuestas.removeFirst()
            let datos = try cuerpo.map { try JSONSerialization.data(withJSONObject: $0) } ?? Data()
            return (codigo, datos)
        }
    }

    func cuerpo(_ i: Int) -> Json? {
        pedidos[i].cuerpo.flatMap { try? JSONSerialization.jsonObject(with: $0) as? Json }
    }
}

final class Caja: @unchecked Sendable { var codigo: String? }

final class ApiHttpTests: XCTestCase {
    let ahora = Iso.fecha("2026-11-21T03:00:00Z")!
    let base = URL(string: "https://empresas.finsocial.app/movil/v1/")!

    func sesion(accesoEn: TimeInterval = 600, renovacionEn: TimeInterval = 3600) -> Sesion {
        Sesion(acceso: "acc", accesoExpiraEn: ahora + accesoEn, renovacion: "ren", renovacionExpiraEn: ahora + renovacionEn)
    }

    func api(_ t: TransporteFalso, _ almacen: AlmacenEnMemoria = AlmacenEnMemoria()) -> ApiHttp {
        let ahora = self.ahora
        return ApiHttp(almacen: almacen, base: base, version: "1.0.0", transporte: t, reloj: { ahora })
    }

    func testEncabezadosYRuta() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion())
        almacen.guardarDispositivo("dev_1")
        almacen.guardarEmpresa("emp_1")
        let t = TransporteFalso([(200, ["elementos": []] as Json)])
        _ = try await api(t, almacen).destinos()
        let p = t.pedidos[0]
        XCTAssertEqual(p.url.absoluteString, "https://empresas.finsocial.app/movil/v1/destinos")
        XCTAssertEqual(p.encabezados["Authorization"], "Bearer acc")
        XCTAssertEqual(p.encabezados["X-Finso-Dispositivo"], "dev_1")
        XCTAssertEqual(p.encabezados["X-Finso-Empresa"], "emp_1")
        XCTAssertEqual(p.encabezados["X-Finso-Plataforma"], "ios")
        XCTAssertEqual(p.encabezados["X-Finso-App"], "empresarial/1.0.0")
    }

    func testRenuevaAntesDeQueVenza() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion(accesoEn: 10))
        let nueva: Json = [
            "acceso": "acc2", "accesoExpiraEn": Iso.texto(ahora + 600),
            "renovacion": "ren2", "renovacionExpiraEn": Iso.texto(ahora + 3000),
        ]
        let t = TransporteFalso([(200, nueva), (200, ["elementos": []] as Json)])
        _ = try await api(t, almacen).destinos()
        XCTAssertEqual(t.pedidos[0].url.path, "/movil/v1/sesion/renovar")
        XCTAssertEqual(t.cuerpo(0)?["renovacion"] as? String, "ren")
        XCTAssertNil(t.pedidos[0].encabezados["Authorization"])
        XCTAssertEqual(t.pedidos[1].encabezados["Authorization"], "Bearer acc2")
        XCTAssertEqual(almacen.sesion()?.acceso, "acc2")
    }

    func testPasadaLaHoraPideFaceIdOtraVez() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion(accesoEn: -10, renovacionEn: -1))
        let t = TransporteFalso([])
        let a = api(t, almacen)
        let cerro = Caja()
        a.alCerrarSesion = { e in cerro.codigo = e.codigo }
        do {
            _ = try await a.destinos()
            XCTFail()
        } catch let e as ErrorFinso {
            XCTAssertEqual(e.codigo, "SESION_VENCIDA")
        }
        XCTAssertEqual(cerro.codigo, "SESION_VENCIDA")
        XCTAssertTrue(t.pedidos.isEmpty)
        XCTAssertNil(almacen.sesion())
    }

    func testDispositivoRevocadoCierraSesion() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion())
        let t = TransporteFalso([(401, ["codigo": "DISPOSITIVO_REVOCADO"] as Json)])
        do {
            _ = try await api(t, almacen).yo()
            XCTFail()
        } catch let e as ErrorFinso {
            XCTAssertEqual(e.codigo, "DISPOSITIVO_REVOCADO")
            XCTAssertEqual(e.http, 401)
        }
        XCTAssertNil(almacen.sesion())
    }

    func testEmpresaRequerida() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion())
        almacen.guardarEmpresa("emp_vieja")
        let t = TransporteFalso([(409, ["codigo": "EMPRESA_REQUERIDA"] as Json)])
        _ = try? await api(t, almacen).cuenta()
        XCTAssertNil(almacen.empresa())
    }

    func testMandarSpeiSinReferenciaYConLlave() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(sesion())
        almacen.guardarDispositivo("dev_1")
        let orden: Json = [
            "id": "spo_1", "estado": "POR_LIBERAR",
            "destino": ["id": "dst_1", "titular": "PAPELERA", "clabeFinal": "4821"] as Json,
            "importeCentavos": 125_050, "concepto": "Pago", "referenciaNumerica": 77, "pedidaPor": "Luis",
            "creadaEn": "2026-11-21T03:00:00.000Z", "actualizadaEn": "2026-11-21T03:00:00.000Z", "version": 1,
        ]
        let t = TransporteFalso([(201, orden)])
        let o = try await api(t, almacen).mandarSpei(
            SolicitudSpei(destino: "dst_1", importeCentavos: 125_050, concepto: "Pago"), reto: "r", firma: "f",
            llaveDeIdempotencia: "llave-1")
        XCTAssertEqual(o.referenciaNumerica, 77)
        XCTAssertEqual(t.pedidos[0].encabezados["Idempotency-Key"], "llave-1")
        let c = t.cuerpo(0)!
        XCTAssertNil(c["referenciaNumerica"])
        XCTAssertEqual(c["firma"] as? String, "f")
        XCTAssertEqual(c["reto"] as? String, "r")
    }

    func testSinRed() async throws {
        let t = TransporteFalso([])
        do {
            _ = try await api(t).iniciar(correo: "a@b.mx", contrasena: "x", telefono: DatosDelTelefono(nombre: "x"))
            XCTFail()
        } catch let e as ErrorFinso {
            XCTAssertTrue(e.esSinRed)
        }
    }
}
