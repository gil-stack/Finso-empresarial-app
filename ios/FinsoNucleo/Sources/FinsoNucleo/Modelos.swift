import Foundation

// Modelos de la API móvil de Finso Empresas (`/movil/v1`, contrato v1.1).
// Contrato: /mnt/project-files/app-empresarial/contrato-api-movil.md
// La API simulada también pasa por estos `init(json:)`, así que si el
// contrato cambia, las pruebas lo notan.

// MARK: - Entrar

public enum PasoEntrada: String, Sendable, Equatable {
    case capturarCelular = "CAPTURAR_CELULAR"
    case codigoSms = "CODIGO_SMS"
    case codigoCorreo = "CODIGO_CORREO"
}

/// Lo que falta para registrar el teléfono (§1.1 a §1.4).
public struct RetoDeEntrada: Sendable, Equatable {
    public var reto: String
    public var expiraEn: Date
    public var pasos: [PasoEntrada]
    /// Cuántos pasos pidió el servidor al principio (para «Paso 2 de 4»).
    public var totalDePasos: Int
    public var celularEnmascarado: String?
    public var correoEnmascarado: String?
    public var reenvioDisponibleEn: Date?

    public init(
        reto: String, expiraEn: Date, pasos: [PasoEntrada], totalDePasos: Int? = nil,
        celularEnmascarado: String? = nil, correoEnmascarado: String? = nil,
        reenvioDisponibleEn: Date? = nil
    ) {
        self.reto = reto
        self.expiraEn = expiraEn
        self.pasos = pasos
        self.totalDePasos = totalDePasos ?? pasos.count
        self.celularEnmascarado = celularEnmascarado
        self.correoEnmascarado = correoEnmascarado
        self.reenvioDisponibleEn = reenvioDisponibleEn
    }

    init(json: Any?) throws {
        let l = try Lector(json)
        let pasos = try l.lista("pasos").map { v -> PasoEntrada in
            guard let s = v as? String, let p = PasoEntrada(rawValue: s) else {
                throw ErrorFinso(.respuestaInvalida)
            }
            return p
        }
        self.init(
            reto: try l.texto("reto"), expiraEn: try l.fecha("expiraEn"), pasos: pasos,
            celularEnmascarado: l.textoOpcional("celularEnmascarado"),
            correoEnmascarado: l.textoOpcional("correoEnmascarado"),
            reenvioDisponibleEn: l.fechaOpcional("reenvioDisponibleEn"))
    }

    /// El mismo reto con los pasos que faltan. `reenvio` se reemplaza tal cual
    /// (nil = todavía no hay código en camino para el siguiente paso).
    public func conPasos(
        _ pendientes: [PasoEntrada], celularEnmascarado: String? = nil,
        reenvioDisponibleEn: Date?
    ) -> RetoDeEntrada {
        var r = self
        r.pasos = pendientes
        if let c = celularEnmascarado { r.celularEnmascarado = c }
        r.reenvioDisponibleEn = reenvioDisponibleEn
        return r
    }
}

public struct Sesion: Sendable, Equatable {
    public var acceso: String
    public var accesoExpiraEn: Date
    public var renovacion: String
    public var renovacionExpiraEn: Date

    public init(acceso: String, accesoExpiraEn: Date, renovacion: String, renovacionExpiraEn: Date) {
        self.acceso = acceso
        self.accesoExpiraEn = accesoExpiraEn
        self.renovacion = renovacion
        self.renovacionExpiraEn = renovacionExpiraEn
    }

    public init(json: Any?) throws {
        let l = try Lector(json)
        self.init(
            acceso: try l.texto("acceso"), accesoExpiraEn: try l.fecha("accesoExpiraEn"),
            renovacion: try l.texto("renovacion"),
            renovacionExpiraEn: try l.fecha("renovacionExpiraEn"))
    }

    public var json: Json {
        [
            "acceso": acceso, "accesoExpiraEn": Iso.texto(accesoExpiraEn),
            "renovacion": renovacion, "renovacionExpiraEn": Iso.texto(renovacionExpiraEn),
        ]
    }
}

/// Qué pantallas ve la persona (§1.8 v1.1). Manda sobre `papeles`.
public struct Permisos: Sendable, Equatable {
    public var verCuenta = false
    public var verDestinos = false
    public var pedirSpei = false
    public var liberarSpei = false
    public var verOrdenes = false

    public init(
        verCuenta: Bool = false, verDestinos: Bool = false, pedirSpei: Bool = false,
        liberarSpei: Bool = false, verOrdenes: Bool = false
    ) {
        self.verCuenta = verCuenta
        self.verDestinos = verDestinos
        self.pedirSpei = pedirSpei
        self.liberarSpei = liberarSpei
        self.verOrdenes = verOrdenes
    }

    init(_ l: Lector?) {
        guard let l else { return }
        verCuenta = l.bool("verCuenta")
        verDestinos = l.bool("verDestinos")
        pedirSpei = l.bool("pedirSpei")
        liberarSpei = l.bool("liberarSpei")
        verOrdenes = l.bool("verOrdenes")
    }
}

public struct Empresa: Sendable, Equatable, Identifiable {
    public var id: String
    public var nombre: String

    public init(id: String, nombre: String) {
        self.id = id
        self.nombre = nombre
    }

    init(_ l: Lector) throws {
        self.init(id: try l.texto("id"), nombre: try l.texto("nombre"))
    }
}

public struct Usuario: Sendable, Equatable {
    public var id: String
    public var nombre: String
    public var correo: String
    public var papeles: [String]
    /// nil: tiene varias y todavía no elige (se manda `X-Finso-Empresa`).
    public var empresa: Empresa?
    public var empresas: [Empresa]
    public var permisos: Permisos

    public var nombreCorto: String { String(nombre.split(separator: " ").first ?? "") }
    public var empresaNombre: String { empresa?.nombre ?? "" }
    public var debeElegirEmpresa: Bool { empresa == nil && empresas.count > 1 }
    public var puedeVerCuenta: Bool { permisos.verCuenta }
    public var puedePedirSpei: Bool { permisos.pedirSpei && permisos.verDestinos }
    public var puedeLiberar: Bool { permisos.liberarSpei }

    public init(json: Any?) throws {
        let l = try Lector(json)
        id = try l.texto("id")
        nombre = try l.texto("nombre")
        correo = try l.texto("correo")
        papeles = l.lista("papeles").compactMap { $0 as? String }
        empresa = try l.objetoOpcional("empresa").map(Empresa.init)
        empresas = try l.lista("empresas").map { try Empresa(Lector($0)) }
        permisos = Permisos(l.objetoOpcional("permisos"))
    }

    /// El mismo usuario sin empresa elegida (tras `409 EMPRESA_REQUERIDA`).
    public func sinEmpresa() -> Usuario {
        var u = self
        u.empresa = nil
        return u
    }
}

/// Respuesta de `POST /dispositivos` y de entrar con biometría.
public struct Ingreso: Sendable {
    public var sesion: Sesion
    public var usuario: Usuario
    /// Solo al registrar el teléfono.
    public var dispositivo: String?

    init(json: Any?) throws {
        let l = try Lector(json)
        sesion = try Sesion(json: l.j["sesion"])
        usuario = try Usuario(json: l.j["usuario"])
        dispositivo = l.textoOpcional("dispositivo")
    }
}

public struct RetoDeFirma: Sendable {
    public var reto: String
    public var expiraEn: Date

    init(json: Any?) throws {
        let l = try Lector(json)
        reto = try l.texto("reto")
        expiraEn = try l.fecha("expiraEn")
    }
}

// MARK: - Cuenta

public struct Cuenta: Sendable, Equatable {
    public var clabe: String
    public var titular: String
    public var bloqueada: Bool
    public var saldoCentavos: Int
    public var disponibleCentavos: Int
    public var horaDelSaldo: Date

    /// `6461 8053 5000 0012 34`: la CLABE en grupos de 4 para leerla y dictarla.
    public var clabeAgrupada: String {
        var grupos: [String] = []
        var resto = Substring(clabe)
        while !resto.isEmpty {
            grupos.append(String(resto.prefix(4)))
            resto = resto.dropFirst(4)
        }
        return grupos.joined(separator: " ")
    }

    init(_ l: Lector) throws {
        clabe = try l.texto("clabe")
        titular = try l.texto("titular")
        bloqueada = l.textoOpcional("estado") == "BLOQUEADA"
        saldoCentavos = try l.entero("saldoCentavos")
        disponibleCentavos = try l.entero("disponibleCentavos")
        horaDelSaldo = try l.fecha("horaDelSaldo")
    }
}

public struct LecturaDeCuenta: Sendable {
    /// nil: la cuenta todavía se está abriendo.
    public var cuenta: Cuenta?
    /// El core no contestó: es la última copia guardada.
    public var sinConexion: Bool

    init(json: Any?) throws {
        let l = try Lector(json)
        cuenta = try l.objetoOpcional("cuenta").map(Cuenta.init)
        sinConexion = l.bool("sinConexion")
    }
}

public enum TipoMovimiento: String, Sendable {
    case speiEntrada = "SPEI_ENTRADA"
    case speiSalida = "SPEI_SALIDA"
    case comision = "COMISION"
    case devolucion = "DEVOLUCION"
    case ajuste = "AJUSTE"
}

public struct Contraparte: Sendable, Equatable {
    public var nombre: String?
    public var banco: String?
    public var clabeFinal: String?
}

public struct Movimiento: Sendable, Identifiable, Equatable {
    public var id: String
    /// true = entra dinero (ABONO); false = sale (CARGO).
    public var abono: Bool
    public var tipo: TipoMovimiento
    public var importeCentavos: Int
    public var saldoResultanteCentavos: Int
    public var fecha: Date
    public var concepto: String?
    public var claveRastreo: String?
    public var contraparte: Contraparte?
    public var orden: String?
    public var comprobante: Bool

    /// El importe con su signo: positivo si entra, negativo si sale.
    public var importeConSigno: Int { abono ? importeCentavos : -importeCentavos }

    public var titulo: String {
        if let n = contraparte?.nombre { return n }
        switch tipo {
        case .comision: return "Comisión"
        case .devolucion: return "Devolución"
        case .ajuste: return "Ajuste"
        case .speiEntrada: return "SPEI recibido"
        case .speiSalida: return "SPEI enviado"
        }
    }

    init(_ l: Lector) throws {
        id = try l.texto("id")
        abono = l.textoOpcional("sentido") == "ABONO"
        tipo = TipoMovimiento(rawValue: l.textoOpcional("tipo") ?? "") ?? .ajuste
        importeCentavos = try l.entero("importeCentavos")
        saldoResultanteCentavos = try l.entero("saldoResultanteCentavos")
        fecha = try l.fecha("fecha")
        concepto = l.textoOpcional("concepto")
        claveRastreo = l.textoOpcional("claveRastreo")
        contraparte = l.objetoOpcional("contraparte").map {
            Contraparte(
                nombre: $0.textoOpcional("nombre"), banco: $0.textoOpcional("banco"),
                clabeFinal: $0.textoOpcional("clabeFinal"))
        }
        orden = l.textoOpcional("orden")
        comprobante = l.bool("comprobante")
    }
}

public struct PaginaDeMovimientos: Sendable {
    public var mes: String
    public var elementos: [Movimiento]
    public var siguienteCursor: String?
    public var incompleto: Bool
    public var sinConexion: Bool

    init(json: Any?) throws {
        let l = try Lector(json)
        mes = try l.texto("mes")
        elementos = try l.lista("elementos").map { try Movimiento(Lector($0)) }
        siguienteCursor = l.textoOpcional("siguienteCursor")
        incompleto = l.bool("incompleto")
        sinConexion = l.bool("sinConexion")
    }
}

// MARK: - Destinos

public struct Destino: Sendable, Identifiable, Equatable {
    public var id: String
    public var titular: String
    /// true = PROVEEDOR; false = COLABORADOR.
    public var proveedor: Bool
    public var banco: String?
    public var clabeFinal: String
    /// false = EN_ESPERA (registrado hace menos de 30 min).
    public var activo: Bool
    /// nil: el alta todavía no llega al core.
    public var activoDesde: Date?

    /// Colaborador: se le paga desde Nómina en el portal, no desde la app.
    public var soloNomina: Bool { !proveedor }

    /// Ya se le puede pagar: proveedor ACTIVO o que ya cumplió su espera.
    public func listoEn(_ ahora: Date) -> Bool {
        if soloNomina { return false }
        if activo { return true }
        guard let desde = activoDesde else { return false }
        return ahora >= desde
    }

    public var detalle: String { [banco, "•••• \(clabeFinal)"].compactMap { $0 }.joined(separator: " · ") }

    init(_ l: Lector) throws {
        id = try l.texto("id")
        titular = try l.texto("titular")
        proveedor = l.textoOpcional("tipo") == "PROVEEDOR"
        banco = l.textoOpcional("banco")
        clabeFinal = try l.texto("clabeFinal")
        activo = l.textoOpcional("estado") == "ACTIVO"
        activoDesde = l.fechaOpcional("activoDesde")
    }
}

// MARK: - SPEI

/// Lo que se va a mandar, tal cual lo tecleó la persona. Sin referencia
/// numérica: la asigna el servidor (§4.1 v1.1).
public struct SolicitudSpei: Sendable, Equatable {
    public var destino: String
    public var importeCentavos: Int
    public var concepto: String

    public init(destino: String, importeCentavos: Int, concepto: String) {
        self.destino = destino
        self.importeCentavos = importeCentavos
        self.concepto = concepto
    }

    public var json: Json {
        ["destino": destino, "importeCentavos": importeCentavos, "concepto": concepto]
    }

    public static let largoConcepto = 35

    /// 1 a 35 de `[A-Za-z0-9 .,&-]`, sin espacios al inicio, al final ni dobles.
    public static func conceptoValido(_ c: String) -> Bool {
        guard (1...largoConcepto).contains(c.count) else { return false }
        guard c.first != " ", c.last != " ", !c.contains("  ") else { return false }
        return c.unicodeScalars.allSatisfy(Concepto.permitido)
    }
}

public struct PreparacionSpei: Sendable {
    public var reto: String
    public var expiraEn: Date
    public var titular: String
    public var banco: String?
    public var clabeFinal: String
    public var importeCentavos: Int
    public var comisionCentavos: Int
    public var concepto: String
    /// Siempre true en Finso Empresas: otra persona tiene que liberarlo.
    public var requiereLiberador: Bool

    init(json: Any?) throws {
        let l = try Lector(json)
        let r = try l.objeto("resumen")
        let d = try r.objeto("destino")
        reto = try l.texto("reto")
        expiraEn = try l.fecha("expiraEn")
        titular = try d.texto("titular")
        banco = d.textoOpcional("banco")
        clabeFinal = try d.texto("clabeFinal")
        importeCentavos = try r.entero("importeCentavos")
        comisionCentavos = r.enteroOpcional("comisionCentavos") ?? 0
        concepto = try r.texto("concepto")
        requiereLiberador = l.bool("requiereLiberador", siFalta: true)
    }
}

public enum EstadoOrden: String, Sendable {
    case porLiberar = "POR_LIBERAR"
    case enProceso = "EN_PROCESO"
    case enviada = "ENVIADA"
    case liquidada = "LIQUIDADA"
    case devuelta = "DEVUELTA"
    case rechazada = "RECHAZADA"
    case cancelada = "CANCELADA"

    public var etiqueta: String {
        switch self {
        case .porLiberar: return "Por liberar"
        case .enProceso: return "En proceso"
        case .enviada: return "Enviada"
        case .liquidada: return "Liquidada"
        case .devuelta: return "Devuelta"
        case .rechazada: return "Rechazada"
        case .cancelada: return "Cancelada"
        }
    }

    public var esFinal: Bool { [.liquidada, .devuelta, .rechazada, .cancelada].contains(self) }
}

public struct Orden: Sendable, Identifiable, Equatable {
    public var id: String
    public var estado: EstadoOrden
    /// nil: el destino se dio de baja después.
    public var destinoId: String?
    public var titular: String
    public var banco: String?
    public var clabeFinal: String
    public var importeCentavos: Int
    public var concepto: String
    /// La asigna el servidor (v1.1).
    public var referenciaNumerica: Int?
    public var claveRastreo: String?
    public var pedidaPor: String
    public var liberadaPor: String?
    public var creadaEn: Date
    public var actualizadaEn: Date
    public var motivo: String?
    public var version: Int
    /// Esta persona puede liberarla o rechazarla.
    public var puedoLiberar: Bool

    init(json: Any?) throws {
        let l = try Lector(json)
        let d = try l.objeto("destino")
        id = try l.texto("id")
        guard let e = EstadoOrden(rawValue: try l.texto("estado")) else {
            throw ErrorFinso(.respuestaInvalida)
        }
        estado = e
        destinoId = d.textoOpcional("id")
        titular = try d.texto("titular")
        banco = d.textoOpcional("banco")
        clabeFinal = try d.texto("clabeFinal")
        importeCentavos = try l.entero("importeCentavos")
        concepto = try l.texto("concepto")
        referenciaNumerica = l.enteroOpcional("referenciaNumerica")
        claveRastreo = l.textoOpcional("claveRastreo")
        pedidaPor = try l.texto("pedidaPor")
        liberadaPor = l.textoOpcional("liberadaPor")
        creadaEn = try l.fecha("creadaEn")
        actualizadaEn = try l.fecha("actualizadaEn")
        motivo = l.textoOpcional("motivo")
        version = l.enteroOpcional("version") ?? 1
        puedoLiberar = l.bool("puedoLiberar")
    }
}

/// Respuesta de `POST /spei/{id}/liberar/preparar`: el mismo reto sirve para
/// liberar o para rechazar (§4.4).
public struct PreparacionDeLiberacion: Sendable {
    public var reto: String
    public var expiraEn: Date
    public var importeCentavos: Int
    public var version: Int

    init(json: Any?) throws {
        let l = try Lector(json)
        reto = try l.texto("reto")
        expiraEn = try l.fecha("expiraEn")
        importeCentavos = try l.entero("importeCentavos")
        version = l.enteroOpcional("version") ?? 1
    }
}
