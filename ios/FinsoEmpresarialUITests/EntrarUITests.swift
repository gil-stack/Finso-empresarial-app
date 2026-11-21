import XCTest

/// Casos de pantalla 8 a 13 de la especificación §8, con la API simulada.
/// Cada paso deja una captura (se guardan como artefacto del CI).
final class EntrarUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // Sin lienzo seguro: si no, las capturas salen negras.
        app.launchArguments += ["-finsoCapturas", "-AppleLanguages", "(es-MX)", "-AppleLocale", "es_MX"]
        app.launchEnvironment["FINSO_API"] = "simulada"
    }

    func captura(_ nombre: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = nombre
        a.lifetime = .keepAlways
        add(a)
    }

    func esperar(_ e: XCUIElement, _ segundos: TimeInterval = 8, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(e.waitForExistence(timeout: segundos), "No apareció \(e)", file: file, line: line)
    }

    func texto(_ t: String) -> XCUIElement {
        app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", t)).firstMatch
    }

    func escribirCodigo(_ c: String) {
        let campo = app.textFields["codigo"]
        esperar(campo)
        // El campo es casi invisible (va bajo las casillas): se toca por coordenada.
        campo.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        campo.typeText(c)
    }

    func entrarConCorreo(_ correo: String) {
        let entrar = app.buttons["Entrar"]
        esperar(entrar)
        entrar.tap()
        let campoCorreo = app.textFields["correo"]
        esperar(campoCorreo)
        campoCorreo.tap()
        campoCorreo.typeText(correo)
        let contrasena = app.secureTextFields["contrasena"]
        contrasena.tap()
        contrasena.typeText("secreta")
        app.buttons["continuar"].tap()
    }

    func testBienvenidaYPrimerIngresoCompleto() {
        app.launch()
        esperar(app.buttons["Entrar"])
        captura("01-bienvenida")
        entrarConCorreo("ana@empresa.mx")
        esperar(texto("PASO 2 DE 4"))
        escribirCodigo("000000")
        esperar(texto("Te quedan 4 intentos"))
        captura("02-codigo-equivocado")
        escribirCodigo("123456")
        esperar(texto("PASO 3 DE 4"))
        esperar(texto("Revisa tus mensajes"))
        captura("03-codigo-sms")
        escribirCodigo("123456")
        esperar(app.buttons["activarBiometria"])
        esperar(texto("PASO 4 DE 4"))
        captura("04-activar-face-id")
        app.buttons["activarBiometria"].tap()
        esperar(app.staticTexts["saludoInicio"])
        XCTAssertEqual(app.staticTexts["saludoInicio"].label, "Hola, Ana")
        captura("05-dentro")

        // Sale y vuelve con Face ID (en el simulador, llave de prueba).
        app.buttons["ajustes"].tap()
        esperar(app.buttons["salir"])
        captura("06-ajustes")
        app.buttons["salir"].tap()
        esperar(app.staticTexts["saludo"])
        XCTAssertEqual(app.staticTexts["saludo"].label, "Hola, Ana")
        captura("07-desbloqueo")
        app.buttons["entrarBiometria"].tap()
        esperar(app.staticTexts["saludoInicio"])
    }

    func testSinCelularLoCaptura() {
        app.launch()
        entrarConCorreo("nuevo@empresa.mx")
        escribirCodigo("123456")
        esperar(texto("PASO 3 DE 5"))
        let celular = app.textFields["celular"]
        esperar(celular)
        celular.tap()
        celular.typeText("5512349876")
        XCTAssertEqual(celular.value as? String, "55 1234 9876")
        captura("08-celular")
        app.buttons["mandarCodigo"].tap()
        esperar(texto("PASO 4 DE 5"))
        esperar(texto("9876"))
    }

    func testVariasEmpresas() {
        app.launch()
        entrarConCorreo("varias@empresa.mx")
        escribirCodigo("123456")
        esperar(texto("Revisa tus mensajes"))
        escribirCodigo("123456")
        esperar(app.buttons["activarBiometria"])
        app.buttons["activarBiometria"].tap()
        let segunda = app.buttons["empresa-emp_SIM2"]
        esperar(segunda)
        captura("09-elegir-empresa")
        segunda.tap()
        esperar(texto("Transportes del Bajío"))
        captura("10-empresa-elegida")
    }
}
