import Foundation

/// Objeto JSON tal como llega del servidor.
public typealias Json = [String: Any]

/// Fechas ISO-8601 en UTC, con o sin milisegundos (`2026-11-21T01:30:00Z`,
/// `2026-11-21T01:30:00.123Z`). Se arma a mano para que dé lo mismo en iPhone
/// y en Linux.
public enum Iso {
    private static func formateador(_ formato: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = formato
        return f
    }

    private static let conMilis = formateador("yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX")
    private static let sinMilis = formateador("yyyy-MM-dd'T'HH:mm:ssXXXXX")
    private static let lock = NSLock()

    public static func fecha(_ texto: String) -> Date? {
        lock.lock()
        defer { lock.unlock() }
        return conMilis.date(from: texto) ?? sinMilis.date(from: texto)
    }

    public static func texto(_ fecha: Date) -> String {
        lock.lock()
        defer { lock.unlock() }
        return conMilis.string(from: fecha)
    }
}

/// Lectura tolerante de un objeto JSON: si falta algo obligatorio, la
/// respuesta no es del contrato y se trata como `RESPUESTA_INVALIDA`.
struct Lector {
    let j: Json

    init(_ valor: Any?) throws {
        guard let j = valor as? Json else { throw ErrorFinso(.respuestaInvalida) }
        self.j = j
    }

    func texto(_ k: String) throws -> String {
        guard let v = j[k] as? String else { throw ErrorFinso(.respuestaInvalida) }
        return v
    }

    func textoOpcional(_ k: String) -> String? { j[k] as? String }

    func entero(_ k: String) throws -> Int {
        guard let v = enteroOpcional(k) else { throw ErrorFinso(.respuestaInvalida) }
        return v
    }

    func enteroOpcional(_ k: String) -> Int? {
        if let v = j[k] as? Int { return v }
        if let v = j[k] as? NSNumber { return v.intValue }
        return nil
    }

    func bool(_ k: String, siFalta: Bool = false) -> Bool {
        if let v = j[k] as? Bool { return v }
        if let v = j[k] as? NSNumber { return v.boolValue }
        return siFalta
    }

    func fecha(_ k: String) throws -> Date {
        guard let t = j[k] as? String, let f = Iso.fecha(t) else {
            throw ErrorFinso(.respuestaInvalida)
        }
        return f
    }

    func fechaOpcional(_ k: String) -> Date? {
        (j[k] as? String).flatMap(Iso.fecha)
    }

    func objeto(_ k: String) throws -> Lector { try Lector(j[k]) }

    func objetoOpcional(_ k: String) -> Lector? {
        guard let o = j[k] as? Json else { return nil }
        return Lector(sinRevisar: o)
    }

    func lista(_ k: String) -> [Any] { j[k] as? [Any] ?? [] }

    private init(sinRevisar j: Json) { self.j = j }
}

extension NSLock {
    /// Candado para usar desde código `async` (sin `lock()` suelto, que Swift
    /// 6 no deja en contextos asíncronos). Igual a `withLock`, que no existe
    /// en todas las versiones de Foundation de Linux.
    func con<T>(_ hacer: () throws -> T) rethrows -> T {
        lock()
        defer { unlock() }
        return try hacer()
    }
}
