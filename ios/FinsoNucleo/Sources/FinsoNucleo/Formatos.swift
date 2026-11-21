import Foundation

// Formatos de la especificación (§7). Se arman a mano, sin formateadores del
// sistema, para que den exactamente lo mismo en iPhone, en Linux y en Android.

// MARK: - Dinero

public enum Dinero {
    /// `125050` → `$1,250.50`. Con `conSigno`: `+$1,500.00` / `−$1,500.00`.
    /// Con `sinCentavos`: `$1,250`.
    public static func pesos(_ centavos: Int, conSigno: Bool = false, sinCentavos: Bool = false) -> String {
        let negativo = centavos < 0
        let absoluto = centavos.magnitude
        let resto = absoluto % 100
        let cuerpo = "$" + agrupar(String(absoluto / 100))
            + (sinCentavos ? "" : "." + (resto < 10 ? "0" : "") + String(resto))
        if negativo { return "−" + cuerpo }
        return conSigno ? "+" + cuerpo : cuerpo
    }

    /// Importe oculto con el ojo.
    public static let oculto = "$ •••••"

    /// `1250` → `1,250`.
    static func agrupar(_ digitos: String) -> String {
        var fuera = ""
        for (i, c) in digitos.reversed().enumerated() {
            if i > 0 && i % 3 == 0 { fuera.append(",") }
            fuera.append(c)
        }
        return String(fuera.reversed())
    }

    static func soloDigitos(_ s: Substring) -> Bool { s.unicodeScalars.allSatisfy { ("0"..."9").contains($0) } }

    /// Lo que la persona tecleó (`"1,250.5"`, `"$80"`) → centavos. nil si no
    /// es un importe válido. Nunca pasa por decimales flotantes.
    public static func centavos(de tecleado: String) -> Int? {
        let limpio = tecleado.filter { !"$, ".contains($0) }
        let partes = limpio.split(separator: ".", omittingEmptySubsequences: false)
        guard (1...2).contains(partes.count), (1...12).contains(partes[0].count), soloDigitos(partes[0]),
              let enteros = Int(partes[0])
        else { return nil }
        var dec = partes.count == 2 ? String(partes[1]) : ""
        guard dec.count <= 2, soloDigitos(Substring(dec)) else { return nil }
        while dec.count < 2 { dec += "0" }
        return enteros * 100 + (Int(dec) ?? 0)
    }

    /// Formato mientras se teclea (`1250.5` → `1,250.5`), igual en las dos
    /// apps. No deja más de dos decimales: si se pasa, regresa `anterior`. Una
    /// coma recién tecleada seguida de 0 a 2 dígitos (teclados con coma
    /// decimal) se toma como punto.
    public static func alTeclear(anterior: String, nuevo: String) -> String {
        var texto = nuevo
        if !texto.contains("."), texto.count > anterior.count, let coma = texto.lastIndex(of: ",") {
            let despues = texto[texto.index(after: coma)...]
            let yaEstaba = anterior.contains(",") && anterior.split(separator: ",").dropFirst()
                .contains { $0.count >= 3 && soloDigitos($0.prefix(3)) }
            if despues.count <= 2, soloDigitos(despues), !yaEstaba {
                texto.replaceSubrange(coma...coma, with: ".")
            }
        }
        let crudo = texto.filter { !"$, ".contains($0) }
        if crudo.isEmpty { return "" }
        let partes = crudo.split(separator: ".", omittingEmptySubsequences: false)
        guard partes.count <= 2, partes[0].count <= 12, soloDigitos(partes[0]),
              partes.count == 1 || (partes[1].count <= 2 && soloDigitos(partes[1]))
        else { return anterior }
        var enteros = String(partes[0])
        while enteros.count > 1 && enteros.hasPrefix("0") { enteros.removeFirst() }
        let agrupado = agrupar(enteros)
        return (agrupado.isEmpty ? "0" : agrupado) + (partes.count > 1 ? "." + partes[1] : "")
    }
}

// MARK: - Fechas (hora de México, UTC−6)

public enum Fechas {
    public static let mexico = TimeZone(secondsFromGMT: -6 * 3600)!
    static let mesesCortos = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
    static let mesesLargos = [
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
        "octubre", "noviembre", "diciembre",
    ]

    static var calendario: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = mexico
        return c
    }

    static func partes(_ d: Date) -> DateComponents {
        calendario.dateComponents([.year, .month, .day, .hour, .minute], from: d)
    }

    /// `19:35`
    public static func hora(_ d: Date) -> String {
        let p = partes(d)
        return String(format: "%02d:%02d", p.hour ?? 0, p.minute ?? 0)
    }

    /// `21 nov 2026`
    public static func fechaCorta(_ d: Date) -> String {
        let p = partes(d)
        return "\(p.day ?? 1) \(mesesCortos[(p.month ?? 1) - 1]) \(p.year ?? 0)"
    }

    /// `20 nov 2026, 19:35`
    public static func fechaYHora(_ d: Date) -> String { "\(fechaCorta(d)), \(hora(d))" }

    /// `Hoy`, `Ayer` o `21 nov 2026`, para agrupar movimientos.
    public static func dia(_ d: Date, ahora: Date = Date()) -> String {
        let c = calendario
        let dias = c.dateComponents([.day], from: c.startOfDay(for: d), to: c.startOfDay(for: ahora)).day ?? 0
        if dias == 0 { return "Hoy" }
        if dias == 1 { return "Ayer" }
        return fechaCorta(d)
    }

    /// `2026-11` del mes en México.
    public static func mes(_ d: Date = Date()) -> String {
        let p = partes(d)
        return String(format: "%04d-%02d", p.year ?? 0, p.month ?? 1)
    }

    /// El mes anterior a `2026-11` → `2026-10`.
    public static func mesAnterior(_ mes: String) -> String {
        let p = mes.split(separator: "-").compactMap { Int($0) }
        guard p.count == 2 else { return mes }
        return p[1] == 1 ? String(format: "%04d-12", p[0] - 1) : String(format: "%04d-%02d", p[0], p[1] - 1)
    }

    /// `noviembre 2026` a partir de `2026-11`.
    public static func nombreDelMes(_ mes: String) -> String {
        let p = mes.split(separator: "-").compactMap { Int($0) }
        guard p.count == 2, (1...12).contains(p[1]) else { return mes }
        return "\(mesesLargos[p[1] - 1]) \(p[0])"
    }

    /// `en unos segundos`, `en 18 min`, `en 1 h`, `en 1 h 5 min`.
    public static func faltan(_ segundos: TimeInterval) -> String {
        let s = Int(segundos.rounded(.up))
        if s <= 59 { return "en unos segundos" }
        let h = s / 3600
        let min = (s / 60) % 60 + (s % 60 > 0 ? 1 : 0)
        if h == 0 { return "en \(min) min" }
        return min == 0 ? "en \(h) h" : "en \(h) h \(min) min"
    }
}

// MARK: - Nombres

public enum Nombre {
    static let siglas: Set<String> = ["SA", "CV", "SC", "SAPI", "RL", "MX", "SAB", "SOFOM", "ENR"]
    static let chicas: Set<String> = ["DE", "DEL", "LA", "LAS", "LOS", "Y", "E"]

    /// «PAPELERA DEL CENTRO SA DE CV» → «Papelera del Centro SA de CV».
    /// Si ya trae minúsculas, se deja igual.
    public static func legible(_ s: String) -> String {
        guard s == s.uppercased() else { return s }
        var primera = true
        return s.split(separator: " ", omittingEmptySubsequences: false).map { p -> String in
            defer { primera = false }
            let w = String(p)
            if w.isEmpty || siglas.contains(w) { return w }
            if chicas.contains(w) && !primera { return w.lowercased() }
            return w.prefix(1) + w.dropFirst().lowercased()
        }.joined(separator: " ")
    }
}

// MARK: - Concepto del SPEI

public enum Concepto {
    static let acentos: [Character: Character] = [
        "á": "a", "é": "e", "í": "i", "ó": "o", "ú": "u", "ü": "u", "ñ": "n",
        "Á": "A", "É": "E", "Í": "I", "Ó": "O", "Ú": "U", "Ü": "U", "Ñ": "N",
    ]

    static func permitido(_ u: Unicode.Scalar) -> Bool {
        switch u {
        case "A"..."Z", "a"..."z", "0"..."9", " ", ".", ",", "&", "-": return true
        default: return false
        }
    }

    /// Mientras se escribe: quita acentos (á → a, ñ → n), descarta lo que SPEI
    /// no admite, no deja espacio al inicio ni dobles y corta en 35.
    public static func limpiar(_ s: String) -> String {
        var fuera = ""
        for ch in s {
            let c = acentos[ch] ?? ch
            guard c.unicodeScalars.count == 1, let u = c.unicodeScalars.first, permitido(u) else { continue }
            if c == " " && (fuera.isEmpty || fuera.hasSuffix(" ")) { continue }
            fuera.append(c)
        }
        return String(fuera.prefix(SolicitudSpei.largoConcepto))
    }
}
