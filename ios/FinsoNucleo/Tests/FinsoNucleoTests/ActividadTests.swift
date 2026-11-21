import XCTest
@testable import FinsoNucleo

final class ActividadTests: XCTestCase {
    func testPresentacionPorEstado() {
        XCTAssertEqual(PresentacionDeActividad(estado: "POR_LIBERAR", liberadaPor: nil).titulo, "Falta que la liberen")
        XCTAssertEqual(PresentacionDeActividad(estado: "EN_PROCESO", liberadaPor: "Ana").titulo, "Liberado por Ana")
        XCTAssertEqual(PresentacionDeActividad(estado: "EN_PROCESO", liberadaPor: nil).titulo, "Enviando…")
        let l = PresentacionDeActividad(estado: "LIQUIDADA", liberadaPor: "Ana")
        XCTAssertEqual(l.titulo, "Enviado")
        XCTAssertTrue(l.final)
        XCTAssertEqual(PresentacionDeActividad(estado: "RECHAZADA", liberadaPor: nil).titulo, "Rechazado")
        XCTAssertEqual(PresentacionDeActividad(estado: "DEVUELTA", liberadaPor: nil).titulo, "Devuelto")
        XCTAssertEqual(PresentacionDeActividad(estado: "OTRO", liberadaPor: nil), .vieja)
    }

    func testImporteYFormatos() {
        XCTAssertEqual(PresentacionDeActividad.importe(1_845_000), "$18,450")
        XCTAssertEqual(PresentacionDeActividad.importe(125_050), "$1,250.50")
        XCTAssertEqual(PresentacionDeActividad.fecha(Iso.fecha("2026-11-21T03:00:00.123Z")!), "2026-11-21T03:00:00Z")
        XCTAssertEqual(PresentacionDeActividad.hex(Data([0x0a, 0xff, 0x01])), "0aff01")
        XCTAssertEqual(PresentacionDeActividad.primerNombre("Ana López Garza"), "Ana")
        XCTAssertNil(PresentacionDeActividad.primerNombre(nil))
    }

    func testAvisarActividadMandaElToken() async throws {
        let almacen = AlmacenEnMemoria()
        almacen.guardar(Sesion(
            acceso: "a", accesoExpiraEn: Date() + 600, renovacion: "r", renovacionExpiraEn: Date() + 3600))
        almacen.guardarDispositivo("dev_1")
        let t = TransporteFalso([(204, nil)])
        let api = ApiHttp(almacen: almacen, base: URL(string: "https://x.mx/movil/v1")!, version: "1", transporte: t)
        try await api.avisarActividad(orden: "spo_1", token: "0aff01")
        XCTAssertEqual(t.pedidos[0].url.path, "/movil/v1/avisos/actividad")
        XCTAssertEqual(t.cuerpo(0)?["orden"] as? String, "spo_1")
        XCTAssertEqual(t.cuerpo(0)?["plataforma"] as? String, "ios")
        XCTAssertEqual(t.cuerpo(0)?["token"] as? String, "0aff01")
        XCTAssertEqual(t.pedidos[0].encabezados["X-Finso-Dispositivo"], "dev_1")
    }
}
