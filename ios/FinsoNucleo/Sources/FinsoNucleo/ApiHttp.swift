import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Un pedido HTTP ya armado. Las pruebas cambian el transporte por uno falso.
public struct Pedido: Sendable {
    public var metodo: String
    public var url: URL
    public var encabezados: [String: String]
    public var cuerpo: Data?
}

public protocol Transporte: Sendable {
    /// Devuelve el código HTTP y el cuerpo. Lanza `ErrorFinso(.sinRed)` si no
    /// hay conexión.
    func enviar(_ pedido: Pedido) async throws -> (Int, Data)
}

/// Transporte real con URLSession (20 s de espera).
public struct TransporteUrlSession: Transporte {
    public init() {}

    public func enviar(_ pedido: Pedido) async throws -> (Int, Data) {
        var r = URLRequest(url: pedido.url, timeoutInterval: 20)
        r.httpMethod = pedido.metodo
        r.httpBody = pedido.cuerpo
        for (k, v) in pedido.encabezados { r.setValue(v, forHTTPHeaderField: k) }
        return try await withCheckedThrowingContinuation { cont in
            URLSession.shared.dataTask(with: r) { datos, respuesta, error in
                if error != nil {
                    cont.resume(throwing: ErrorFinso(.sinRed))
                } else if let h = respuesta as? HTTPURLResponse {
                    cont.resume(returning: (h.statusCode, datos ?? Data()))
                } else {
                    cont.resume(throwing: ErrorFinso(.respuestaInvalida))
                }
            }.resume()
        }
    }
}

/// La API real de Finso Empresas por HTTPS (contrato §0).
public final class ApiHttp: FinsoApi, @unchecked Sendable {
    public let almacen: AlmacenDeSesion
    let base: URL
    let version: String
    let plataforma: String
    let transporte: Transporte
    let reloj: @Sendable () -> Date

    /// Se llama cuando el servidor revoca la sesión o el teléfono, o cuando
    /// pasó la hora desde el último Face ID (§1.7: hay que pedirlo otra vez).
    public var alCerrarSesion: (@Sendable (ErrorFinso) -> Void)?
    /// Se llama con `409 EMPRESA_REQUERIDA`.
    public var alPedirEmpresa: (@Sendable () -> Void)?

    /// Una sola renovación a la vez, aunque varias pantallas pidan datos juntas.
    private var renovando: Task<Void, Error>?
    private let lock = NSLock()

    public init(
        almacen: AlmacenDeSesion, base: URL, version: String, plataforma: String = "ios",
        transporte: Transporte = TransporteUrlSession(), reloj: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.almacen = almacen
        self.base = base
        self.version = version
        self.plataforma = plataforma
        self.transporte = transporte
        self.reloj = reloj
    }

    func url(_ ruta: String, _ consulta: [String: String]) -> URL {
        var raiz = base.absoluteString
        while raiz.hasSuffix("/") { raiz.removeLast() }
        var c = URLComponents(string: raiz + ruta)!
        if !consulta.isEmpty {
            c.queryItems = consulta.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        return c.url!
    }

    func encabezados(conSesion: Bool, idempotencia: String?) -> [String: String] {
        var e = [
            "Accept": "application/json",
            "Content-Type": "application/json; charset=utf-8",
            "X-Finso-App": "empresarial/\(version)",
            "X-Finso-Plataforma": plataforma,
        ]
        if let d = almacen.dispositivo() { e["X-Finso-Dispositivo"] = d }
        if let emp = almacen.empresa() { e["X-Finso-Empresa"] = emp }
        if let i = idempotencia { e["Idempotency-Key"] = i }
        if conSesion, let s = almacen.sesion() { e["Authorization"] = "Bearer \(s.acceso)" }
        return e
    }

    @discardableResult
    func pedir(
        _ metodo: String, _ ruta: String, cuerpo: Json? = nil, consulta: [String: String] = [:],
        conSesion: Bool = true, idempotencia: String? = nil, reintentado: Bool = false
    ) async throws -> Any? {
        if conSesion && !reintentado { try await renovarSiVence() }
        let datos = try cuerpo.map { try JSONSerialization.data(withJSONObject: $0, options: [.sortedKeys]) }
        let p = Pedido(
            metodo: metodo, url: url(ruta, consulta),
            encabezados: encabezados(conSesion: conSesion, idempotencia: idempotencia), cuerpo: datos)
        let (codigo, respuesta) = try await transporte.enviar(p)
        let json: Any? = respuesta.isEmpty ? nil : try? JSONSerialization.jsonObject(with: respuesta)
        if (200..<300).contains(codigo) {
            if !respuesta.isEmpty && json == nil { throw ErrorFinso(ErrorFinso.Comun.respuestaInvalida.rawValue, http: codigo) }
            return json
        }
        let error = ErrorFinso(http: codigo, json: json)
        if codigo == 401 && error.codigo == "SESION_VENCIDA" && conSesion && !reintentado {
            try await renovar()
            return try await pedir(
                metodo, ruta, cuerpo: cuerpo, consulta: consulta, conSesion: conSesion,
                idempotencia: idempotencia, reintentado: true)
        }
        if error.cierraSesion {
            almacen.borrarSesion()
            alCerrarSesion?(error)
        }
        if error.codigo == "EMPRESA_REQUERIDA" {
            almacen.guardarEmpresa(nil)
            alPedirEmpresa?()
        }
        throw error
    }

    func renovarSiVence() async throws {
        guard let s = almacen.sesion() else { return }
        if s.accesoExpiraEn < reloj().addingTimeInterval(30) { try await renovar() }
    }

    func renovar() async throws {
        let tarea: Task<Void, Error> = lock.con {
            if let t = renovando { return t }
            let t = Task { try await self.renovarAhora() }
            renovando = t
            return t
        }
        defer { lock.con { if renovando == tarea { renovando = nil } } }
        try await tarea.value
    }

    private func renovarAhora() async throws {
        do {
            guard let s = almacen.sesion() else { throw ErrorFinso("SESION_REVOCADA", http: 401) }
            // La sesión dura 1 h desde el último Face ID (§1.7). Pasada la hora
            // ni se intenta: se pide Face ID otra vez.
            guard s.renovacionExpiraEn > reloj() else { throw ErrorFinso("SESION_VENCIDA", http: 401) }
            let j = try await pedir("POST", "/sesion/renovar", cuerpo: ["renovacion": s.renovacion], conSesion: false)
            almacen.guardar(try Sesion(json: j))
        } catch let e as ErrorFinso where e.codigo == "SESION_VENCIDA" {
            almacen.borrarSesion()
            alCerrarSesion?(e)
            throw e
        }
    }

    // MARK: §1 Entrar

    public func iniciar(correo: String, contrasena: String, telefono: DatosDelTelefono) async throws -> RetoDeEntrada {
        try RetoDeEntrada(json: try await pedir(
            "POST", "/sesion/inicio",
            cuerpo: ["correo": correo, "contrasena": contrasena, "dispositivo": telefono.json], conSesion: false))
    }

    public func capturarCelular(reto: RetoDeEntrada, celular: String) async throws -> RetoDeEntrada {
        let l = try Lector(try await pedir(
            "POST", "/sesion/celular", cuerpo: ["reto": reto.reto, "celular": celular], conSesion: false))
        return reto.conPasos(
            reto.pasos.filter { $0 != .capturarCelular },
            celularEnmascarado: l.textoOpcional("celularEnmascarado"),
            reenvioDisponibleEn: l.fechaOpcional("reenvioDisponibleEn"))
    }

    public func confirmarCodigo(reto: RetoDeEntrada, paso: PasoEntrada, codigo: String) async throws -> RetoDeEntrada {
        let l = try Lector(try await pedir(
            "POST", "/sesion/codigo", cuerpo: ["reto": reto.reto, "paso": paso.rawValue, "codigo": codigo],
            conSesion: false))
        let pendientes = try l.lista("pasosPendientes").map { v -> PasoEntrada in
            guard let s = v as? String, let p = PasoEntrada(rawValue: s) else { throw ErrorFinso(.respuestaInvalida) }
            return p
        }
        // Si ya salió el SMS, su espera manda; si no, no se arrastra la del correo.
        return reto.conPasos(
            pendientes, celularEnmascarado: l.textoOpcional("celularEnmascarado"),
            reenvioDisponibleEn: l.fechaOpcional("reenvioDisponibleEn"))
    }

    public func reenviarCodigo(reto: String, paso: PasoEntrada) async throws -> Date {
        try Lector(try await pedir(
            "POST", "/sesion/codigo/reenviar", cuerpo: ["reto": reto, "paso": paso.rawValue], conSesion: false)
        ).fecha("reenvioDisponibleEn")
    }

    public func registrarDispositivo(
        reto: String, llavePublica: String, firma: String, telefono: DatosDelTelefono
    ) async throws -> Ingreso {
        let ingreso = try Ingreso(json: try await pedir(
            "POST", "/dispositivos",
            cuerpo: [
                "reto": reto, "llavePublica": llavePublica, "algoritmo": "ES256", "nombre": telefono.nombre,
                "firma": firma,
            ], conSesion: false))
        almacen.guardar(ingreso.sesion)
        return ingreso
    }

    public func retoParaEntrar(dispositivo: String) async throws -> RetoDeFirma {
        try RetoDeFirma(json: try await pedir(
            "POST", "/sesion/biometria/reto", cuerpo: ["dispositivo": dispositivo], conSesion: false))
    }

    public func entrarConBiometria(dispositivo: String, reto: String, firma: String) async throws -> Ingreso {
        let ingreso = try Ingreso(json: try await pedir(
            "POST", "/sesion/biometria", cuerpo: ["dispositivo": dispositivo, "reto": reto, "firma": firma],
            conSesion: false))
        almacen.guardar(ingreso.sesion)
        return ingreso
    }

    public func salir() async throws {
        defer { almacen.borrarSesion() }
        try await pedir("POST", "/sesion/salir")
    }

    public func yo() async throws -> Usuario { try Usuario(json: try await pedir("GET", "/yo")) }

    public func elegirEmpresa(_ id: String) async throws -> Usuario {
        almacen.guardarEmpresa(id)
        return try await yo()
    }

    // MARK: §2 Cuenta

    public func cuenta() async throws -> LecturaDeCuenta {
        try LecturaDeCuenta(json: try await pedir("GET", "/cuenta"))
    }

    public func movimientos(mes: String, cursor: String?) async throws -> PaginaDeMovimientos {
        var c = ["mes": mes]
        if let cursor { c["cursor"] = cursor }
        return try PaginaDeMovimientos(json: try await pedir("GET", "/movimientos", consulta: c))
    }

    // MARK: §3 Destinos

    public func destinos() async throws -> [Destino] {
        try Lector(try await pedir("GET", "/destinos")).lista("elementos").map { try Destino(Lector($0)) }
    }

    // MARK: §4 SPEI

    public func prepararSpei(_ solicitud: SolicitudSpei) async throws -> PreparacionSpei {
        try PreparacionSpei(json: try await pedir("POST", "/spei/preparar", cuerpo: solicitud.json))
    }

    public func mandarSpei(
        _ solicitud: SolicitudSpei, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden {
        var c = solicitud.json
        c["reto"] = reto
        c["firma"] = firma
        return try Orden(json: try await pedir("POST", "/spei", cuerpo: c, idempotencia: llaveDeIdempotencia))
    }

    static func ruta(_ id: String) -> String {
        id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/")))
            ?? id
    }

    public func orden(_ id: String) async throws -> Orden {
        try Orden(json: try await pedir("GET", "/spei/\(Self.ruta(id))"))
    }

    public func porLiberar() async throws -> [Orden] {
        try Lector(try await pedir("GET", "/spei", consulta: ["estado": "POR_LIBERAR"])).lista("elementos")
            .map { try Orden(json: $0) }
    }

    public func prepararLiberacion(_ orden: String) async throws -> PreparacionDeLiberacion {
        try PreparacionDeLiberacion(json: try await pedir("POST", "/spei/\(Self.ruta(orden))/liberar/preparar"))
    }

    public func liberar(orden: String, reto: String, firma: String, llaveDeIdempotencia: String) async throws -> Orden {
        try Orden(json: try await pedir(
            "POST", "/spei/\(Self.ruta(orden))/liberar", cuerpo: ["reto": reto, "firma": firma],
            idempotencia: llaveDeIdempotencia))
    }

    public func rechazar(
        orden: String, motivo: String, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden {
        try Orden(json: try await pedir(
            "POST", "/spei/\(Self.ruta(orden))/rechazar", cuerpo: ["motivo": motivo, "reto": reto, "firma": firma],
            idempotencia: llaveDeIdempotencia))
    }

    // MARK: §5 Avisos

    public func avisarActividad(orden: String, token: String) async throws {
        try await pedir(
            "POST", "/avisos/actividad", cuerpo: ["orden": orden, "plataforma": plataforma, "token": token])
    }
}
