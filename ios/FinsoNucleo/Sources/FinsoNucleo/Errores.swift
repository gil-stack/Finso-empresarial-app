import Foundation

/// Error que contesta la API (forma `{ codigo, mensaje?, … }` del contrato) o
/// que arma la app cuando no hay red. El texto para la persona sale del
/// servidor si lo mandó; si no, de la tabla de la especificación (§6).
public struct ErrorFinso: Error, Sendable, Equatable {
    public var codigo: String
    public var http: Int?
    public var mensaje: String?
    public var reintentarEn: Date?
    public var intentosRestantes: Int?
    public var campos: [String]

    public enum Comun: String {
        case sinRed = "SIN_RED"
        case respuestaInvalida = "RESPUESTA_INVALIDA"
    }

    public init(
        _ codigo: String, http: Int? = nil, mensaje: String? = nil, reintentarEn: Date? = nil,
        intentosRestantes: Int? = nil, campos: [String] = []
    ) {
        self.codigo = codigo
        self.http = http
        self.mensaje = mensaje
        self.reintentarEn = reintentarEn
        self.intentosRestantes = intentosRestantes
        self.campos = campos
    }

    public init(_ comun: Comun) { self.init(comun.rawValue) }

    init(http: Int, json: Any?) {
        let j = json as? Json ?? [:]
        let l = try? Lector(j)
        self.init(
            l?.textoOpcional("codigo") ?? "ERROR_\(http)", http: http,
            mensaje: l?.textoOpcional("mensaje"), reintentarEn: l?.fechaOpcional("reintentarEn"),
            intentosRestantes: l?.enteroOpcional("intentosRestantes"),
            campos: (j["campos"] as? [Any] ?? []).map { "\($0)" })
    }

    /// La sesión ya no sirve y hay que volver a entrar.
    public var cierraSesion: Bool { codigo == "SESION_REVOCADA" || codigo == "DISPOSITIVO_REVOCADO" }

    public var esSinRed: Bool { codigo == Comun.sinRed.rawValue }

    /// Texto para la persona.
    public var paraPersona: String {
        if let m = mensaje?.trimmingCharacters(in: .whitespacesAndNewlines), !m.isEmpty { return m }
        switch codigo {
        case Comun.sinRed.rawValue: return "No hay conexión. Revisa tu internet e inténtalo otra vez."
        case "CREDENCIALES_INVALIDAS": return "El correo o la contraseña no coinciden."
        case "EMPRESA_SUSPENDIDA": return "La cuenta de tu empresa está suspendida."
        case "SIN_ACCESO_MOVIL": return "Tu usuario no tiene acceso a la app. Pídeselo a tu administrador."
        case "DEMASIADOS_INTENTOS": return "Demasiados intentos. Espera unos minutos."
        case "CODIGO_INCORRECTO":
            if let n = intentosRestantes { return "El código no es correcto. Te quedan \(n) intentos." }
            return "El código no es correcto."
        case "CODIGO_VENCIDO": return "Ese código ya venció. Pide otro."
        case "RETO_VENCIDO": return "Pasó demasiado tiempo. Vuelve a empezar."
        case "PASO_FUERA_DE_ORDEN": return "Algo se desacomodó. Vuelve a empezar."
        case "RETO_INCOMPLETO": return "Faltan pasos. Vuelve a empezar."
        case "ESPERA_PARA_REENVIAR": return "Espera un momento antes de pedir otro código."
        case "SMS_NO_DISPONIBLE": return "Ahorita no podemos mandar SMS. Inténtalo en unos minutos."
        case "CORREO_NO_DISPONIBLE": return "Ahorita no podemos mandar el correo. Inténtalo en unos minutos."
        case "CELULAR_INVALIDO": return "Revisa el número: son 10 dígitos."
        case "CELULAR_EN_USO": return "Ese celular ya lo usa otra persona."
        case "DEMASIADOS_DISPOSITIVOS": return "Ya tienes el máximo de teléfonos. Da de baja uno en el portal."
        case "LLAVE_INVALIDA": return "No pudimos registrar la seguridad de este teléfono. Inténtalo otra vez."
        case "FIRMA_INVALIDA": return "No pudimos confirmar tu identidad. Inténtalo otra vez."
        case "DISPOSITIVO_REVOCADO": return "Este teléfono se dio de baja. Vuelve a registrarlo."
        case "SESION_REVOCADA", "SESION_VENCIDA": return "Tu sesión terminó. Vuelve a entrar."
        case "EMPRESA_REQUERIDA": return "Elige con qué empresa quieres entrar."
        case "SIN_PERMISO": return "Tu usuario no tiene permiso para esto."
        case "SIN_CONEXION": return "No pudimos leer tu cuenta en este momento. Inténtalo en unos minutos."
        case "DATOS_INVALIDOS": return "Algo en los datos no cuadra. Inténtalo otra vez."
        case "DESTINO_EN_ESPERA":
            return "Este destino es nuevo: por seguridad se le puede pagar 30 minutos después de registrarlo."
        case "DESTINO_NO_REGISTRADO": return "Solo se puede pagar a cuentas destino registradas en el portal."
        case "DESTINO_SOLO_NOMINA": return "A colaboradores se les paga desde Nómina en el portal."
        case "SALDO_INSUFICIENTE": return "No te alcanza el saldo disponible."
        case "IMPORTE_INVALIDO": return "Revisa el importe."
        case "CONCEPTO_INVALIDO":
            return "El concepto solo admite letras sin acentos, números, espacios y . , & - (hasta 35)."
        case "CUENTA_BLOQUEADA": return "La cuenta está bloqueada y no puede mandar dinero."
        case "RETO_DE_OTRA_OPERACION": return "Los datos cambiaron desde que los revisaste. Vuelve a revisarlos."
        case "MISMA_PERSONA": return "Quien pidió el SPEI no puede liberarlo."
        case "YA_LIBERADA": return "Este SPEI ya fue liberado."
        case "CANCELADA": return "Este SPEI ya fue cancelado."
        case "ORDEN_CAMBIO": return "Este SPEI cambió mientras lo veías. Revísalo otra vez."
        case "LLAVE_EN_CONFLICTO": return "Esta operación ya se mandó con otros datos."
        case "SIN_COMPROBANTE": return "Este movimiento no tiene comprobante."
        case "VERSION_NO_SOPORTADA": return "Actualiza la app desde la tienda para seguir."
        default: return "Algo salió mal. Inténtalo otra vez."
        }
    }
}
