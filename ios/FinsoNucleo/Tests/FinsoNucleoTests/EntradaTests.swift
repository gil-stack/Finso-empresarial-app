import XCTest
@testable import FinsoNucleo

/// Casos 9 a 15 de la especificación §8, sobre la lógica (las pantallas solo
/// la muestran).
@MainActor
final class EntradaTests: XCTestCase {
    final class Reloj: @unchecked Sendable {
        var ahora = Date(timeIntervalSince1970: 1_800_000_000)
    }

    var reloj = Reloj()
    var api: ApiSimulada!
    var almacen: AlmacenEnMemoria!
    var llave: LlaveSimulada!
    var control: ControlDeSesion!

    override func setUp() async throws {
        reloj = Reloj()
        let r = reloj
        api = ApiSimulada(reloj: { r.ahora }, demora: 0)
        almacen = AlmacenEnMemoria()
        llave = LlaveSimulada()
        control = ControlDeSesion(api: api, almacen: almacen, llave: llave, reloj: { r.ahora })
        control.arrancar()
    }

    func flujo() -> FlujoDeEntrada {
        FlujoDeEntrada(control: control, telefono: DatosDelTelefono(nombre: "iPhone"), esperar: { _ in })
    }

    func primerIngreso(_ correo: String = "ana@empresa.mx") async {
        let f = flujo()
        await f.continuar(correo: correo, contrasena: "x")
        while f.paso != .biometria {
            if f.paso == .celular {
                await f.capturar(celular: "55 1234 9876")
            } else {
                await f.confirmar(codigo: "123456")
            }
            XCTAssertNil(f.error)
        }
        await f.activarBiometria()
    }

    func testArrancaFueraSinTelefono() async {
        XCTAssertEqual(control.estado, .fuera(aviso: nil))
    }

    func testPrimerIngresoCompleto() async throws {
        let f = flujo()
        XCTAssertEqual(f.textoDelPaso, "PASO 1 DE 4")
        await f.continuar(correo: "ana@empresa.mx", contrasena: "secreta")
        XCTAssertEqual(f.paso, .codigo(.codigoCorreo))
        XCTAssertEqual(f.textoDelPaso, "PASO 2 DE 4")
        await f.confirmar(codigo: "000000")
        XCTAssertEqual(f.error, "El código no es correcto. Te quedan 4 intentos.")
        XCTAssertEqual(f.codigosEquivocados, 1)
        await f.confirmar(codigo: "123456")
        XCTAssertEqual(f.paso, .codigo(.codigoSms))
        XCTAssertEqual(f.textoDelPaso, "PASO 3 DE 4")
        XCTAssertNotNil(f.textoDeReenvio(ahora: reloj.ahora))
        await f.confirmar(codigo: "123456")
        XCTAssertEqual(f.paso, .biometria)
        XCTAssertEqual(f.textoDelPaso, "PASO 4 DE 4")
        await f.activarBiometria()
        guard case .dentro(let u) = control.estado else { return XCTFail("\(control.estado)") }
        XCTAssertEqual(u.nombreCorto, "Ana")
        XCTAssertNotNil(almacen.dispositivo())
        XCTAssertEqual(almacen.persona(), "Ana")
        XCTAssertTrue(llave.firmados.first?.hasPrefix("finso-movil:v1:registrar:") ?? false)
    }

    func testCancelarBiometriaNoRegistra() async {
        let f = flujo()
        await f.continuar(correo: "ana@empresa.mx", contrasena: "x")
        await f.confirmar(codigo: "123456")
        await f.confirmar(codigo: "123456")
        llave.siguienteFallo = .cancelada
        await f.activarBiometria()
        XCTAssertEqual(f.error, "Cancelaste la verificación.")
        XCTAssertEqual(f.paso, .biometria)
        XCTAssertNil(almacen.dispositivo())
        XCTAssertFalse(llave.existe())
        await f.activarBiometria()
        XCTAssertNotNil(almacen.dispositivo())
    }

    func testSinCelularLoCapturaDespuesDelCorreo() async {
        let f = flujo()
        await f.continuar(correo: "nuevo@empresa.mx", contrasena: "x")
        XCTAssertEqual(f.textoDelPaso, "PASO 2 DE 5")
        await f.confirmar(codigo: "123456")
        XCTAssertEqual(f.paso, .celular)
        XCTAssertEqual(f.textoDelPaso, "PASO 3 DE 5")
        await f.capturar(celular: "55 12")
        XCTAssertEqual(f.error, "Revisa el número: son 10 dígitos.")
        await f.capturar(celular: "55 1234 9876")
        XCTAssertEqual(f.paso, .codigo(.codigoSms))
        XCTAssertEqual(f.celularEnmascarado, "•••• ••• 9876")
        XCTAssertEqual(f.textoDelPaso, "PASO 4 DE 5")
    }

    func testDemasiadosIntentosVuelveAlCorreo() async {
        let f = flujo()
        await f.continuar(correo: "ana@empresa.mx", contrasena: "x")
        for _ in 0..<5 { await f.confirmar(codigo: "000000") }
        XCTAssertEqual(f.paso, .correo)
        XCTAssertEqual(f.error, "Demasiados intentos. Espera unos minutos.")
    }

    func testVariasEmpresasElige() async throws {
        await primerIngreso("varias@empresa.mx")
        guard case .elegirEmpresa(let u) = control.estado else { return XCTFail("\(control.estado)") }
        XCTAssertEqual(u.empresas.count, 2)
        try await control.elegirEmpresa("emp_SIM2")
        XCTAssertEqual(control.estado.usuario?.empresaNombre, "Transportes del Bajío")
        XCTAssertEqual(almacen.empresa(), "emp_SIM2")
        control.pedirEmpresa()
        guard case .elegirEmpresa = control.estado else { return XCTFail() }
        XCTAssertNil(almacen.empresa())
    }

    func testSaleYVuelveConBiometria() async throws {
        await primerIngreso()
        await control.salir()
        XCTAssertEqual(control.estado, .bloqueada(nombre: "Ana"))
        try await control.desbloquear()
        guard case .dentro = control.estado else { return XCTFail() }
        XCTAssertTrue(llave.firmados.last?.hasPrefix("finso-movil:v1:entrar:\(almacen.dispositivo()!):") ?? false)
        // Al reabrir la app, pide Face ID.
        control.arrancar()
        XCTAssertEqual(control.estado, .bloqueada(nombre: "Ana"))
    }

    func testCambiaronLasHuellas() async throws {
        await primerIngreso()
        await control.salir()
        llave.siguienteFallo = .invalidada
        try await control.desbloquear()
        XCTAssertEqual(
            control.estado,
            .fuera(aviso: "Cambiaron las huellas o la cara de este teléfono. Por seguridad, vuelve a entrar con tu correo."))
        XCTAssertNil(almacen.dispositivo())
        XCTAssertFalse(llave.existe())
    }

    func testCancelarAlDesbloquearSigueBloqueada() async {
        await primerIngreso()
        await control.salir()
        llave.siguienteFallo = .cancelada
        do {
            try await control.desbloquear()
            XCTFail()
        } catch let e as ErrorDeLlave {
            XCTAssertEqual(e.fallo, .cancelada)
        } catch {
            XCTFail("\(error)")
        }
        XCTAssertEqual(control.estado, .bloqueada(nombre: "Ana"))
    }

    func testCincoMinutosEnSegundoPlanoBloquea() async {
        await primerIngreso()
        control.fueASegundoPlano()
        reloj.ahora += 4 * 60
        control.volvio()
        guard case .dentro = control.estado else { return XCTFail("4 min no bloquea") }
        control.fueASegundoPlano()
        reloj.ahora += 5 * 60
        control.volvio()
        XCTAssertEqual(control.estado, .bloqueada(nombre: "Ana"))
        XCTAssertNil(almacen.sesion())
    }

    func testPasadaLaHoraPideFaceId() async {
        await primerIngreso()
        reloj.ahora += 61 * 60
        control.fueASegundoPlano()
        control.volvio()
        XCTAssertEqual(control.estado, .bloqueada(nombre: "Ana"))
    }

    func testTelefonoDadoDeBaja() async {
        await primerIngreso()
        control.cerradaPorServidor(ErrorFinso("DISPOSITIVO_REVOCADO", http: 401))
        XCTAssertEqual(control.estado, .fuera(aviso: "Este teléfono se dio de baja. Vuelve a registrarlo."))
        control.avisoVisto()
        XCTAssertEqual(control.estado, .fuera(aviso: nil))
    }

    func testOlvidarTelefono() async {
        await primerIngreso()
        await control.olvidarTelefono()
        XCTAssertEqual(control.estado, .fuera(aviso: nil))
        XCTAssertNil(almacen.dispositivo())
        XCTAssertNil(almacen.persona())
    }
}
