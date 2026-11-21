import XCTest
@testable import FinsoNucleo

final class FirmaTests: XCTestCase {
    func testSha256() {
        XCTAssertEqual(
            Sha256.hex(Data("abc".utf8)), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        XCTAssertEqual(
            Sha256.hex(Data()), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        // Dos bloques (56 bytes).
        XCTAssertEqual(
            Sha256.hex(Data("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".utf8)),
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1")
    }

    func testTextosFirmados() {
        XCTAssertEqual(TextoFirmado.entrar(dispositivo: "dev_1", reto: "r1"), "finso-movil:v1:entrar:dev_1:r1")
        XCTAssertEqual(
            TextoFirmado.spei(dispositivo: "dev_1", reto: "r1", destino: "dst_9", importeCentavos: 125_050,
                              concepto: "Pago factura 12"),
            "finso-movil:v1:spei:dev_1:r1:dst_9:125050:Pago factura 12")
        XCTAssertEqual(
            TextoFirmado.liberar(dispositivo: "d", reto: "r", orden: "spo_1", importeCentavos: 7),
            "finso-movil:v1:liberar:d:r:spo_1:7")
        XCTAssertEqual(TextoFirmado.rechazar(dispositivo: "d", reto: "r", orden: "spo_1"),
                       "finso-movil:v1:rechazar:d:r:spo_1")
    }

    func testHuellaDeLaLlave() {
        let huella = huellaDeLlave(LlaveSimulada.publica)
        XCTAssertEqual(huella, Sha256.hex(Data(base64Encoded: LlaveSimulada.publica)!))
        XCTAssertEqual(huella.count, 64)
        XCTAssertEqual(TextoFirmado.registrar(reto: "rto", huellaDeLlave: huella), "finso-movil:v1:registrar:rto:\(huella)")
    }

    func testMensajesDeError() {
        XCTAssertFalse(ErrorFinso("SALDO_INSUFICIENTE").paraPersona.isEmpty)
        XCTAssertFalse(ErrorFinso("ALGO_NUEVO").paraPersona.isEmpty)
        XCTAssertTrue(ErrorFinso("DISPOSITIVO_REVOCADO").cierraSesion)
        XCTAssertFalse(ErrorFinso("SESION_VENCIDA").cierraSesion)
    }
}
