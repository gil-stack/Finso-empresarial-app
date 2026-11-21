import FinsoNucleo
import Foundation
import LocalAuthentication
import Security

/// Llave del teléfono en el Secure Enclave (especificación §2): EC P-256 que
/// solo firma con Face ID o huella (`.biometryCurrentSet`), así que si cambian
/// las caras o huellas del teléfono, la llave deja de servir.
final class LlaveSecureEnclave: LlaveDelDispositivo, @unchecked Sendable {
    static let etiqueta = "finso.empresarial.dispositivo"
    /// Cabecera SubjectPublicKeyInfo de una llave EC P-256.
    static let cabeceraSpki: [UInt8] = [
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48,
        0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    ]

    private var etiquetaDatos: Data { Data(Self.etiqueta.utf8) }

    /// ¿El teléfono tiene Face ID / huella configurados?
    static func hayBiometria() -> Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    func crear() async throws -> String {
        borrar()
        var error: Unmanaged<CFError>?
        guard let acceso = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            [.privateKeyUsage, .biometryCurrentSet], &error)
        else { throw ErrorDeLlave(.otro, error.map { "\($0.takeRetainedValue())" }) }
        guard Self.hayBiometria() else { throw ErrorDeLlave(.sinBiometria) }
        let atributos: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: etiquetaDatos,
                kSecAttrAccessControl as String: acceso,
            ] as [String: Any],
        ]
        guard let privada = SecKeyCreateRandomKey(atributos as CFDictionary, &error),
              let publica = SecKeyCopyPublicKey(privada),
              let punto = SecKeyCopyExternalRepresentation(publica, &error) as Data?
        else { throw ErrorDeLlave(.otro, error.map { "\($0.takeRetainedValue())" }) }
        return (Data(Self.cabeceraSpki) + punto).base64EncodedString()
    }

    private func buscar(_ contexto: LAContext?) throws -> SecKey {
        var consulta: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: etiquetaDatos,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true,
        ]
        if let contexto { consulta[kSecUseAuthenticationContext as String] = contexto }
        var resultado: CFTypeRef?
        let estado = SecItemCopyMatching(consulta as CFDictionary, &resultado)
        guard estado == errSecSuccess, let r = resultado else { throw ErrorDeLlave(.noExiste) }
        return r as! SecKey  // swiftlint:disable:this force_cast
    }

    func firmar(_ texto: String, motivo: String) async throws -> String {
        // Se pide Face ID primero con el motivo, y la firma usa ese mismo
        // contexto: un solo vistazo por operación.
        let contexto = LAContext()
        contexto.localizedCancelTitle = "Cancelar"
        do {
            _ = try await contexto.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: motivo)
        } catch let e as LAError {
            throw Self.traducir(e)
        }
        let llave = try buscar(contexto)
        var error: Unmanaged<CFError>?
        guard let firma = SecKeyCreateSignature(
            llave, .ecdsaSignatureMessageX962SHA256, Data(texto.utf8) as CFData, &error) as Data?
        else {
            // Con `.biometryCurrentSet`, si cambiaron las caras o huellas la
            // llave ya no firma.
            throw ErrorDeLlave(.invalidada, error.map { "\($0.takeRetainedValue())" })
        }
        return firma.base64EncodedString()
    }

    static func traducir(_ e: LAError) -> ErrorDeLlave {
        switch e.code {
        case .userCancel, .appCancel, .systemCancel, .userFallback: return ErrorDeLlave(.cancelada)
        case .biometryNotEnrolled, .biometryNotAvailable, .passcodeNotSet: return ErrorDeLlave(.sinBiometria)
        case .biometryLockout: return ErrorDeLlave(.bloqueada)
        default: return ErrorDeLlave(.otro, e.localizedDescription)
        }
    }

    func existe() -> Bool {
        // Sin contexto y sin pedir Face ID: solo se pregunta si está.
        let contexto = LAContext()
        contexto.interactionNotAllowed = true
        let consulta: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: etiquetaDatos,
            kSecUseAuthenticationContext as String: contexto,
            kSecReturnAttributes as String: true,
        ]
        let estado = SecItemCopyMatching(consulta as CFDictionary, nil)
        return estado == errSecSuccess || estado == errSecInteractionNotAllowed
    }

    func borrar() {
        let consulta: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: etiquetaDatos,
        ]
        SecItemDelete(consulta as CFDictionary)
    }
}
