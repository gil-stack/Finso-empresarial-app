import FinsoNucleo
import UIKit
import XCTest
@testable import FinsoEmpresarial

final class AppTests: XCTestCase {
    func testLetrasRegistradas() {
        for nombre in [
            "Gully-Medium", "Gully-SemiBold", "Gully-Bold", "WantedSansStd-Regular", "WantedSansStd-Medium",
            "WantedSansStd-SemiBold", "WantedSansStd-Bold",
        ] {
            XCTAssertNotNil(UIFont(name: nombre, size: 14), "Falta la letra \(nombre)")
        }
    }

    func testDebugUsaDatosDePrueba() {
        XCTAssertTrue(Configuracion.simulada)
        XCTAssertEqual(Configuracion.urlDeLaApi.absoluteString, "https://empresas.finsocial.app/movil/v1")
        XCTAssertTrue(Configuracion.piezas().api is ApiSimulada)
    }

    func testIdentidad() {
        XCTAssertEqual(Bundle.main.bundleIdentifier, "mx.finsocial.empresarial")
        XCTAssertEqual(Bundle.main.infoDictionary?["CFBundleDisplayName"] as? String, "Finso Empresarial")
    }

    func testMarcaYPatron() {
        XCTAssertNotNil(UIImage(named: "FinsoMarca"))
        XCTAssertNotNil(UIImage(named: "PatronFinso"))
    }

    func testLlaveroGuardaYOlvida() throws {
        let a = AlmacenLlavero(servicio: "mx.finsocial.empresarial.pruebas")
        let s = Sesion(
            acceso: "a", accesoExpiraEn: Date(timeIntervalSince1970: 1_800_000_000), renovacion: "r",
            renovacionExpiraEn: Date(timeIntervalSince1970: 1_800_003_600))
        a.guardar(s)
        if a.sesion() == nil {
            throw XCTSkip("El llavero del simulador no está disponible sin firma.")
        }
        a.guardarDispositivo("dev_1")
        a.guardarEmpresa("emp_1")
        XCTAssertEqual(a.sesion(), s)
        XCTAssertEqual(a.dispositivo(), "dev_1")
        a.guardarEmpresa(nil)
        XCTAssertNil(a.empresa())
        a.olvidarTodo()
        XCTAssertNil(a.sesion())
        XCTAssertNil(a.dispositivo())
    }

    func testCabeceraSpki() {
        let hex = LlaveSecureEnclave.cabeceraSpki.map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(hex, "3059301306072a8648ce3d020106082a8648ce3d030107034200")
    }
}
