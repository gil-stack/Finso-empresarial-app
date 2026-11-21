import XCTest
@testable import FinsoNucleo

/// §8 de la especificación: mismos casos que en Android.
final class FormatosTests: XCTestCase {
    func testPesos() {
        XCTAssertEqual(Dinero.pesos(125_050), "$1,250.50")
        XCTAssertEqual(Dinero.pesos(5), "$0.05")
        XCTAssertEqual(Dinero.pesos(0), "$0.00")
        XCTAssertEqual(Dinero.pesos(124_821_537), "$1,248,215.37")
        XCTAssertEqual(Dinero.pesos(150_000, conSigno: true), "+$1,500.00")
        XCTAssertEqual(Dinero.pesos(-150_000, conSigno: true), "−$1,500.00")
        XCTAssertEqual(Dinero.pesos(125_050, sinCentavos: true), "$1,250")
    }

    func testCentavosDeLoTecleado() {
        XCTAssertEqual(Dinero.centavos(de: "1,250.5"), 125_050)
        XCTAssertEqual(Dinero.centavos(de: "$80"), 8_000)
        XCTAssertEqual(Dinero.centavos(de: "0.07"), 7)
        XCTAssertEqual(Dinero.centavos(de: "18450.00"), 1_845_000)
        XCTAssertNil(Dinero.centavos(de: ""))
        XCTAssertNil(Dinero.centavos(de: "1.234"))
        XCTAssertNil(Dinero.centavos(de: "1.2.3"))
        XCTAssertNil(Dinero.centavos(de: "abc"))
        XCTAssertNil(Dinero.centavos(de: "-5"))
    }

    func testAlTeclear() {
        XCTAssertEqual(Dinero.alTeclear(anterior: "", nuevo: "1"), "1")
        XCTAssertEqual(Dinero.alTeclear(anterior: "125", nuevo: "1250"), "1,250")
        XCTAssertEqual(Dinero.alTeclear(anterior: "1,250", nuevo: "1,250."), "1,250.")
        XCTAssertEqual(Dinero.alTeclear(anterior: "1,250.5", nuevo: "1,250.50"), "1,250.50")
        // No deja un tercer decimal.
        XCTAssertEqual(Dinero.alTeclear(anterior: "1,250.50", nuevo: "1,250.501"), "1,250.50")
        // Coma decimal de algunos teclados.
        XCTAssertEqual(Dinero.alTeclear(anterior: "80", nuevo: "80,"), "80.")
        XCTAssertEqual(Dinero.alTeclear(anterior: "", nuevo: "007"), "7")
        XCTAssertEqual(Dinero.alTeclear(anterior: "1", nuevo: ""), "")
        XCTAssertEqual(Dinero.alTeclear(anterior: "1", nuevo: "1a"), "1")
    }

    func testConcepto() {
        XCTAssertEqual(Concepto.limpiar("  Pago   de flete"), "Pago de flete")
        XCTAssertEqual(Concepto.limpiar("Facturación ñandú"), "Facturacion nandu")
        XCTAssertEqual(Concepto.limpiar(String(repeating: "a", count: 50)).count, 35)
        XCTAssertTrue(SolicitudSpei.conceptoValido("Fletes semana 47"))
        XCTAssertFalse(SolicitudSpei.conceptoValido(""))
        XCTAssertFalse(SolicitudSpei.conceptoValido(String(repeating: "a", count: 36)))
    }

    func testNombreLegible() {
        XCTAssertEqual(Nombre.legible("PAPELERA DEL CENTRO SA DE CV"), "Papelera del Centro SA de CV")
        XCTAssertEqual(Nombre.legible("LA ESPERANZA"), "La Esperanza")
    }

    func testFechasEnHoraDeMexico() {
        // 2026-11-21 02:30Z = 20 nov 20:30 en México (UTC−6).
        let d = Iso.fecha("2026-11-21T02:30:00Z")!
        XCTAssertEqual(Fechas.fechaCorta(d), "20 nov 2026")
        XCTAssertEqual(Fechas.mes(d), "2026-11")
        XCTAssertEqual(Fechas.mesAnterior("2026-01"), "2025-12")
        XCTAssertEqual(Fechas.dia(d, ahora: d), "Hoy")
        XCTAssertEqual(Fechas.dia(d, ahora: d + 86_400), "Ayer")
        XCTAssertNotNil(Iso.fecha("2026-11-21T02:30:00.123Z"))
    }

    func testClabeAgrupada() throws {
        let c = try LecturaDeCuenta(json: [
            "cuenta": ["clabe": "646180535000004821", "titular": "X", "estado": "VIGENTE", "saldoCentavos": 1,
                       "disponibleCentavos": 1, "horaDelSaldo": "2026-11-21T02:30:00Z"] as Json,
            "fuente": "core", "sinConexion": false,
        ] as Json)
        XCTAssertEqual(c.cuenta?.clabeAgrupada, "6461 8053 5000 0048 21")
    }
}
