import Foundation

/// Datos del teléfono que se mandan al entrar por primera vez.
public struct DatosDelTelefono: Sendable {
    public var nombre: String
    /// `ios` o `android`.
    public var plataforma: String
    public var modelo: String?
    public var versionSistema: String?

    public init(nombre: String, plataforma: String = "ios", modelo: String? = nil, versionSistema: String? = nil) {
        self.nombre = nombre
        self.plataforma = plataforma
        self.modelo = modelo
        self.versionSistema = versionSistema
    }

    var json: Json {
        var j: Json = ["nombre": nombre, "plataforma": plataforma]
        if let modelo { j["modelo"] = modelo }
        if let versionSistema { j["versionSistema"] = versionSistema }
        return j
    }
}

/// Todo lo que la app le pide a Finso Empresas. Hay dos implementaciones:
/// `ApiHttp` (la real) y `ApiSimulada` (datos de prueba). Las pantallas solo
/// conocen esta interfaz. Los errores salen como `ErrorFinso`.
public protocol FinsoApi: AnyObject, Sendable {
    // §1 Entrar
    func iniciar(correo: String, contrasena: String, telefono: DatosDelTelefono) async throws -> RetoDeEntrada
    func capturarCelular(reto: RetoDeEntrada, celular: String) async throws -> RetoDeEntrada
    /// El reto con los pasos que faltan (vacío = listo para registrar el
    /// teléfono). Al confirmar el correo puede traer ya el celular del SMS.
    func confirmarCodigo(reto: RetoDeEntrada, paso: PasoEntrada, codigo: String) async throws -> RetoDeEntrada
    func reenviarCodigo(reto: String, paso: PasoEntrada) async throws -> Date
    /// `firma` prueba la llave: firma de `TextoFirmado.registrar` (§1.5).
    func registrarDispositivo(
        reto: String, llavePublica: String, firma: String, telefono: DatosDelTelefono
    ) async throws -> Ingreso
    func retoParaEntrar(dispositivo: String) async throws -> RetoDeFirma
    func entrarConBiometria(dispositivo: String, reto: String, firma: String) async throws -> Ingreso
    func salir() async throws
    func yo() async throws -> Usuario
    /// Elige la empresa (si tiene varias): desde aquí va en `X-Finso-Empresa`.
    func elegirEmpresa(_ id: String) async throws -> Usuario

    // §2 Cuenta
    func cuenta() async throws -> LecturaDeCuenta
    func movimientos(mes: String, cursor: String?) async throws -> PaginaDeMovimientos

    // §3 Destinos
    func destinos() async throws -> [Destino]

    // §4 SPEI
    func prepararSpei(_ solicitud: SolicitudSpei) async throws -> PreparacionSpei
    func mandarSpei(
        _ solicitud: SolicitudSpei, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden
    func orden(_ id: String) async throws -> Orden
    func porLiberar() async throws -> [Orden]
    /// El mismo reto sirve para liberar o para rechazar.
    func prepararLiberacion(_ orden: String) async throws -> PreparacionDeLiberacion
    func liberar(orden: String, reto: String, firma: String, llaveDeIdempotencia: String) async throws -> Orden
    func rechazar(
        orden: String, motivo: String, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden
}

/// Una llave de idempotencia nueva (UUID v4).
public func nuevaLlaveDeIdempotencia() -> String { UUID().uuidString.lowercased() }
