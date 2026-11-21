import Foundation

/// Por qué no se pudo usar la llave del teléfono.
public enum FalloDeLlave: Sendable, Equatable {
    /// La persona canceló Face ID / huella.
    case cancelada
    /// El teléfono no tiene Face ID / huella configurados.
    case sinBiometria
    /// Demasiados intentos: el sistema bloqueó la biometría un rato.
    case bloqueada
    /// Cambiaron las huellas o la cara: la llave ya no sirve.
    case invalidada
    /// No hay llave en este teléfono.
    case noExiste
    case otro
}

public struct ErrorDeLlave: Error, Sendable, Equatable {
    public var fallo: FalloDeLlave
    public var detalle: String?

    public init(_ fallo: FalloDeLlave, _ detalle: String? = nil) {
        self.fallo = fallo
        self.detalle = detalle
    }

    public var paraPersona: String {
        switch fallo {
        case .cancelada: return "Cancelaste la verificación."
        case .sinBiometria: return "Configura Face ID o tu huella en los ajustes del teléfono para usar la app."
        case .bloqueada:
            return "La biometría está bloqueada por muchos intentos. Desbloquea tu teléfono con su código e inténtalo otra vez."
        case .invalidada:
            return "Cambiaron las huellas o la cara de este teléfono. Por seguridad, vuelve a entrar con tu correo."
        case .noExiste: return "Este teléfono no está registrado. Entra con tu correo."
        case .otro: return "No pudimos usar la biometría. Inténtalo otra vez."
        }
    }
}

/// Llave del teléfono (contrato §1.5): EC P-256 en el chip seguro que **solo
/// firma con Face ID o huella**. La privada nunca sale del chip.
public protocol LlaveDelDispositivo: AnyObject, Sendable {
    /// Crea la llave (reemplaza la anterior) y devuelve la pública en base64
    /// de SubjectPublicKeyInfo DER.
    func crear() async throws -> String
    /// Firma `texto` (UTF-8, ECDSA-SHA256, DER en base64). Pide Face ID o
    /// huella con `motivo` como explicación.
    func firmar(_ texto: String, motivo: String) async throws -> String
    func existe() -> Bool
    func borrar()
}

/// Llave de juguete para pruebas y para el modo simulado en el simulador
/// (que no tiene chip seguro). Guarda lo que firmó.
public final class LlaveSimulada: LlaveDelDispositivo, @unchecked Sendable {
    private let lock = NSLock()
    private var _existe = false
    /// El siguiente uso falla con esto (una sola vez).
    public var siguienteFallo: FalloDeLlave?
    public private(set) var firmados: [String] = []

    /// SPKI de P-256 de juguete (cabecera real y punto inventado).
    public static let publica =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/QA=="

    public init(existe: Bool = false) { _existe = existe }

    private func revisar() throws {
        try lock.con {
            if let f = siguienteFallo {
                siguienteFallo = nil
                throw ErrorDeLlave(f)
            }
        }
    }

    public func crear() async throws -> String {
        try revisar()
        lock.con { _existe = true }
        return Self.publica
    }

    public func firmar(_ texto: String, motivo: String) async throws -> String {
        try revisar()
        return try lock.con {
            guard _existe else { throw ErrorDeLlave(.noExiste) }
            firmados.append(texto)
            return Data("firma:\(texto)".utf8).base64EncodedString()
        }
    }

    public func existe() -> Bool { lock.con { _existe } }

    public func borrar() { lock.con { _existe = false } }
}
