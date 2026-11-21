import XCTest
@testable import FinsoNucleo

final class ApiSimuladaTests: XCTestCase {
    let tel = DatosDelTelefono(nombre: "iPhone de prueba", modelo: "iPhone", versionSistema: "17.0")

    func entrar(_ api: ApiSimulada, _ correo: String = "ana@empresa.mx") async throws -> Ingreso {
        var r = try await api.iniciar(correo: correo, contrasena: "x", telefono: tel)
        while let paso = r.pasos.first {
            r = paso == .capturarCelular
                ? try await api.capturarCelular(reto: r, celular: "+52 55 1234 9876")
                : try await api.confirmarCodigo(reto: r, paso: paso, codigo: ApiSimulada.codigoCorrecto)
        }
        return try await api.registrarDispositivo(reto: r.reto, llavePublica: LlaveSimulada.publica, firma: "f", telefono: tel)
    }

    func esperarError(_ codigo: String, _ hacer: () async throws -> Void, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            try await hacer()
            XCTFail("Esperaba \(codigo)", file: file, line: line)
        } catch let e as ErrorFinso {
            XCTAssertEqual(e.codigo, codigo, file: file, line: line)
        } catch {
            XCTFail("\(error)", file: file, line: line)
        }
    }

    func testPrimeroCorreoLuegoSms() async throws {
        let api = ApiSimulada(demora: 0)
        let r = try await api.iniciar(correo: "ana@empresa.mx", contrasena: "x", telefono: tel)
        XCTAssertEqual(r.pasos, [.codigoCorreo, .codigoSms])
        await esperarError("PASO_FUERA_DE_ORDEN") { _ = try await api.confirmarCodigo(reto: r, paso: .codigoSms, codigo: "123456") }
        let r2 = try await api.confirmarCodigo(reto: r, paso: .codigoCorreo, codigo: "123456")
        XCTAssertEqual(r2.pasos, [.codigoSms])
        XCTAssertNotNil(r2.reenvioDisponibleEn)
    }

    func testCelularNuevo() async throws {
        let api = ApiSimulada(demora: 0)
        let ingreso = try await entrar(api, "nuevo@empresa.mx")
        XCTAssertFalse(ingreso.usuario.puedeVerCuenta)
        XCTAssertTrue(ingreso.usuario.puedePedirSpei)
    }

    func testCincoCodigosMalosBloquean() async throws {
        let api = ApiSimulada(demora: 0)
        let r = try await api.iniciar(correo: "ana@empresa.mx", contrasena: "x", telefono: tel)
        for restantes in [4, 3, 2, 1] {
            do {
                _ = try await api.confirmarCodigo(reto: r, paso: .codigoCorreo, codigo: "000000")
            } catch let e as ErrorFinso {
                XCTAssertEqual(e.intentosRestantes, restantes)
            }
        }
        await esperarError("DEMASIADOS_INTENTOS") { _ = try await api.confirmarCodigo(reto: r, paso: .codigoCorreo, codigo: "000000") }
    }

    func testVariasEmpresas() async throws {
        let api = ApiSimulada(demora: 0)
        let ingreso = try await entrar(api, "varias@empresa.mx")
        XCTAssertTrue(ingreso.usuario.debeElegirEmpresa)
        await esperarError("EMPRESA_REQUERIDA") { _ = try await api.destinos() }
        let u = try await api.elegirEmpresa("emp_SIM2")
        XCTAssertEqual(u.empresaNombre, "Transportes del Bajío")
        _ = try await api.destinos()
    }

    func testCuentaYMovimientos() async throws {
        let ahora = Date()
        let api = ApiSimulada(reloj: { ahora }, demora: 0)
        _ = try await entrar(api)
        let c = try await api.cuenta()
        XCTAssertEqual(c.cuenta?.saldoCentavos, 124_821_537)
        XCTAssertEqual(c.cuenta?.disponibleCentavos, 124_821_537 - 1_845_000)
        let pagina = try await api.movimientos(mes: Fechas.mes(ahora), cursor: nil)
        XCTAssertFalse(pagina.elementos.isEmpty)
    }

    func testSolicitanteNoVeSaldo() async throws {
        let api = ApiSimulada(demora: 0)
        _ = try await entrar(api, "luis@empresa.mx")
        await esperarError("SIN_PERMISO") { _ = try await api.cuenta() }
        let lista = try await api.porLiberar()
        XCTAssertTrue(lista.isEmpty)
    }

    func testReglasDeDestinos() async throws {
        let api = ApiSimulada(demora: 0)
        _ = try await entrar(api)
        let destinos = try await api.destinos()
        XCTAssertEqual(destinos.count, 4)
        XCTAssertTrue(destinos.first { $0.id == "dst_SIM_LUCIA" }!.soloNomina)
        XCTAssertFalse(destinos.first { $0.id == "dst_SIM_EMPAQUES" }!.listoEn(Date()))
        await esperarError("DESTINO_SOLO_NOMINA") {
            _ = try await api.prepararSpei(SolicitudSpei(destino: "dst_SIM_LUCIA", importeCentavos: 100, concepto: "Pago"))
        }
        await esperarError("DESTINO_EN_ESPERA") {
            _ = try await api.prepararSpei(SolicitudSpei(destino: "dst_SIM_EMPAQUES", importeCentavos: 100, concepto: "Pago"))
        }
        await esperarError("DESTINO_NO_REGISTRADO") {
            _ = try await api.prepararSpei(SolicitudSpei(destino: "dst_X", importeCentavos: 100, concepto: "Pago"))
        }
    }

    func testSpeiPorLiberarLiberarYLiquidar() async throws {
        let api = ApiSimulada(demora: 0, liquidaEn: 0.05)
        _ = try await entrar(api, "luis@empresa.mx")
        let s = SolicitudSpei(destino: "dst_SIM_PAPELERA", importeCentavos: 125_050, concepto: "Pago factura 12")
        let prep = try await api.prepararSpei(s)
        XCTAssertTrue(prep.requiereLiberador)
        let llave = nuevaLlaveDeIdempotencia()
        let o = try await api.mandarSpei(s, reto: prep.reto, firma: "f", llaveDeIdempotencia: llave)
        XCTAssertEqual(o.estado, .porLiberar)
        XCTAssertFalse(o.puedoLiberar)
        // Reintento con la misma llave: misma orden.
        let otra = try await api.mandarSpei(s, reto: prep.reto, firma: "f", llaveDeIdempotencia: llave)
        XCTAssertEqual(otra.id, o.id)

        // Ana libera.
        _ = try await entrar(api)
        let lista = try await api.porLiberar()
        XCTAssertTrue(lista.contains { $0.id == o.id })
        let pl = try await api.prepararLiberacion(o.id)
        XCTAssertEqual(pl.importeCentavos, 125_050)
        let liberada = try await api.liberar(orden: o.id, reto: pl.reto, firma: "f", llaveDeIdempotencia: nuevaLlaveDeIdempotencia())
        XCTAssertEqual(liberada.estado, .enProceso)
        try await Task.sleep(nanoseconds: 300_000_000)
        let final = try await api.orden(o.id)
        XCTAssertEqual(final.estado, .liquidada)
        XCTAssertEqual(final.liberadaPor, "Ana López Garza")
    }

    func testNoSeLiberaLoQueUnoPidio() async throws {
        let api = ApiSimulada(demora: 0)
        _ = try await entrar(api)
        let s = SolicitudSpei(destino: "dst_SIM_FLETES", importeCentavos: 1_000, concepto: "Prueba")
        let prep = try await api.prepararSpei(s)
        let o = try await api.mandarSpei(s, reto: prep.reto, firma: "f", llaveDeIdempotencia: "k1")
        XCTAssertFalse(o.puedoLiberar)
        await esperarError("MISMA_PERSONA") { _ = try await api.prepararLiberacion(o.id) }
    }

    func testTopeYRechazo() async throws {
        let api = ApiSimulada(demora: 0)
        _ = try await entrar(api, "luis@empresa.mx")
        let s = SolicitudSpei(destino: "dst_SIM_PAPELERA", importeCentavos: 90_000_000, concepto: "Maquinaria")
        let prep = try await api.prepararSpei(s)
        let grande = try await api.mandarSpei(s, reto: prep.reto, firma: "f", llaveDeIdempotencia: "k2")
        _ = try await entrar(api)
        let pl = try await api.prepararLiberacion(grande.id)
        let r = try await api.liberar(orden: grande.id, reto: pl.reto, firma: "f", llaveDeIdempotencia: "k3")
        XCTAssertEqual(r.estado, .rechazada)

        let pl2 = try await api.prepararLiberacion("spo_SIM_PENDIENTE")
        await esperarError("DATOS_INVALIDOS") {
            _ = try await api.rechazar(orden: "spo_SIM_PENDIENTE", motivo: "no", reto: pl2.reto, firma: "f", llaveDeIdempotencia: "k4")
        }
        let rech = try await api.rechazar(
            orden: "spo_SIM_PENDIENTE", motivo: "No corresponde", reto: pl2.reto, firma: "f", llaveDeIdempotencia: "k5")
        XCTAssertEqual(rech.estado, .rechazada)
        XCTAssertEqual(rech.motivo, "No corresponde")
    }
}
