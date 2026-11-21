import XCTest

/// Casos de pantalla 16 a 21 de la especificación §8, con la API simulada.
final class SpeiUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
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
        if !e.waitForExistence(timeout: segundos) {
            // Deja la pantalla y el árbol de accesibilidad para ver qué pasó.
            captura("fallo-linea-\(line)")
            let arbol = XCTAttachment(string: app.debugDescription)
            arbol.name = "fallo-linea-\(line)-arbol"
            arbol.lifetime = .keepAlways
            add(arbol)
            print("=== ARBOL linea \(line)\n\(app.debugDescription)\n=== FIN ARBOL")
            XCTFail("No apareció \(e)", file: file, line: line)
        }
    }

    func texto(_ t: String) -> XCUIElement {
        app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", t)).firstMatch
    }

    /// Primer ingreso completo con la API simulada.
    func entrar(_ correo: String) {
        app.launch()
        let entrar = app.buttons["Entrar"]
        esperar(entrar)
        entrar.tap()
        let c = app.textFields["correo"]
        esperar(c)
        c.tap()
        c.typeText(correo)
        let p = app.secureTextFields["contrasena"]
        p.tap()
        p.typeText("secreta")
        app.buttons["continuar"].tap()
        for _ in 0..<2 {
            let campo = app.textFields["codigo"]
            esperar(campo)
            campo.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            campo.typeText("123456")
            _ = texto("Revisa tus mensajes").waitForExistence(timeout: 4)
        }
        esperar(app.buttons["activarBiometria"])
        app.buttons["activarBiometria"].tap()
        esperar(app.staticTexts["saludoInicio"])
    }

    func testInicioYOjo() {
        entrar("ana@empresa.mx")
        esperar(app.staticTexts["clabe"])
        XCTAssertEqual(app.staticTexts["clabe"].label, "6461 8053 5000 0048 21")
        esperar(app.buttons["mandarSpei"])
        esperar(app.buttons["porLiberar"])
        sleep(1)
        captura("11-inicio")
        app.buttons["ojo"].tap()
        esperar(texto("•••••"))
        captura("12-inicio-oculto")
    }

    func testSolicitantePideSpei() {
        entrar("luis@empresa.mx")
        esperar(texto("Tu usuario no ve el saldo"))
        XCTAssertFalse(app.staticTexts["clabe"].exists)
        XCTAssertFalse(app.buttons["porLiberar"].exists)
        app.buttons["mandarSpei"].tap()
        esperar(app.buttons["destino-dst_SIM_PAPELERA"])
        XCTAssertFalse(app.buttons["destino-dst_SIM_LUCIA"].isEnabled)
        XCTAssertFalse(app.buttons["destino-dst_SIM_EMPAQUES"].isEnabled)
        captura("13-destinos")
        app.buttons["destino-dst_SIM_PAPELERA"].tap()
        let importe = app.textFields["importe"]
        esperar(importe)
        importe.tap()
        importe.typeText("1500")
        let concepto = app.textFields["concepto"]
        concepto.tap()
        concepto.typeText("Pago diseño año")
        XCTAssertEqual(concepto.value as? String, "Pago diseno ano")
        captura("14-importe")
        app.buttons["revisar"].tap()
        esperar(app.buttons["pedir"])
        esperar(texto("Sin comisión"))
        captura("15-revisar")
        app.buttons["pedir"].tap()
        esperar(app.staticTexts["tituloResultado"], 10)
        XCTAssertEqual(app.staticTexts["tituloResultado"].label, "Listo, falta liberarlo")
        esperar(texto("Referencia"))
        captura("16-resultado")
        app.buttons["listo"].tap()
        esperar(app.staticTexts["saludoInicio"])
    }

    func testAnaLiberaYRechaza() {
        entrar("ana@empresa.mx")
        esperar(app.buttons["porLiberar"])
        app.buttons["porLiberar"].tap()
        let orden = app.buttons["orden-spo_SIM_PENDIENTE"]
        esperar(orden)
        captura("17-por-liberar")
        orden.tap()
        esperar(app.buttons["rechazar"])
        captura("18-detalle-por-liberar")
        app.buttons["rechazar"].tap()
        let motivo = app.textViews["motivo"].exists ? app.textViews["motivo"] : app.textFields["motivo"]
        esperar(motivo)
        XCTAssertFalse(app.buttons["confirmarRechazo"].isEnabled)
        motivo.typeText("No corresponde a esta semana")
        XCTAssertTrue(app.buttons["confirmarRechazo"].isEnabled)
        captura("19-rechazo")
        app.buttons["confirmarRechazo"].tap()
        esperar(app.staticTexts["tituloResultado"], 10)
        XCTAssertEqual(app.staticTexts["tituloResultado"].label, "SPEI rechazado")
        esperar(texto("No corresponde a esta semana"))
        captura("20-rechazado")
    }

    func testAnaLiberaElQuePidioLuis() {
        // Luis pide; en la simulada el estado vive mientras la app esté abierta.
        entrar("luis@empresa.mx")
        app.buttons["mandarSpei"].tap()
        esperar(app.buttons["destino-dst_SIM_FLETES"])
        app.buttons["destino-dst_SIM_FLETES"].tap()
        let importe = app.textFields["importe"]
        esperar(importe)
        importe.tap()
        importe.typeText("2500")
        app.textFields["concepto"].tap()
        app.textFields["concepto"].typeText("Flete 48")
        app.buttons["revisar"].tap()
        esperar(app.buttons["pedir"])
        app.buttons["pedir"].tap()
        esperar(app.buttons["listo"], 10)
        app.buttons["listo"].tap()
        // Olvida el teléfono y entra Ana en la misma app.
        esperar(app.buttons["ajustes"])
        app.buttons["ajustes"].tap()
        esperar(app.buttons["olvidar"])
        app.buttons["olvidar"].tap()
        let confirmar = app.buttons["Olvidar este teléfono"].firstMatch
        esperar(confirmar)
        confirmar.tap()
        let entrar = app.buttons["Entrar"]
        esperar(entrar)
        entrar.tap()
        let c = app.textFields["correo"]
        esperar(c)
        c.tap()
        c.typeText("ana@empresa.mx")
        app.secureTextFields["contrasena"].tap()
        app.secureTextFields["contrasena"].typeText("secreta")
        app.buttons["continuar"].tap()
        for _ in 0..<2 {
            let campo = app.textFields["codigo"]
            esperar(campo)
            campo.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            campo.typeText("123456")
            _ = texto("Revisa tus mensajes").waitForExistence(timeout: 4)
        }
        esperar(app.buttons["activarBiometria"])
        app.buttons["activarBiometria"].tap()
        esperar(app.buttons["porLiberar"])
        app.buttons["porLiberar"].tap()
        let fila = app.buttons.containing(NSPredicate(format: "label CONTAINS %@", "$2,500.00")).firstMatch
        esperar(fila)
        fila.tap()
        esperar(app.buttons["liberar"])
        app.buttons["liberar"].tap()
        esperar(app.staticTexts["tituloResultado"], 10)
        captura("21-liberado")
        let enviado = app.staticTexts.containing(NSPredicate(format: "label == %@", "SPEI enviado")).firstMatch
        esperar(enviado, 15)
        esperar(texto("Liberada por Ana López Garza"))
        captura("22-enviado")
    }
}
