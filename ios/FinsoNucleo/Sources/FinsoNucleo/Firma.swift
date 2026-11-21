import Foundation

/// Textos que firma la llave del teléfono (Face ID / huella). El servidor arma
/// el mismo texto y verifica la firma con la llave pública registrada.
/// Formato fijo del contrato v1.1 (§1.5, §1.6, §4.2, §4.4): no cambiar sin
/// cambiarlo allá y en la app de Android.
public enum TextoFirmado {
    public static func registrar(reto: String, huellaDeLlave: String) -> String {
        "finso-movil:v1:registrar:\(reto):\(huellaDeLlave)"
    }

    public static func entrar(dispositivo: String, reto: String) -> String {
        "finso-movil:v1:entrar:\(dispositivo):\(reto)"
    }

    public static func spei(
        dispositivo: String, reto: String, destino: String, importeCentavos: Int, concepto: String
    ) -> String {
        "finso-movil:v1:spei:\(dispositivo):\(reto):\(destino):\(importeCentavos):\(concepto)"
    }

    public static func liberar(dispositivo: String, reto: String, orden: String, importeCentavos: Int) -> String {
        "finso-movil:v1:liberar:\(dispositivo):\(reto):\(orden):\(importeCentavos)"
    }

    public static func rechazar(dispositivo: String, reto: String, orden: String) -> String {
        "finso-movil:v1:rechazar:\(dispositivo):\(reto):\(orden)"
    }
}

/// SHA-256 en hex minúsculas de los bytes DER de la llave pública (base64).
public func huellaDeLlave(_ llavePublicaBase64: String) -> String {
    let bytes = Data(base64Encoded: llavePublicaBase64) ?? Data()
    return Sha256.hex(bytes)
}

/// SHA-256 sin dependencias (CryptoKit no existe en Linux, donde corren las
/// pruebas del núcleo). Probado contra los vectores del estándar.
public enum Sha256 {
    private static let k: [UInt32] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ]

    private static func rotr(_ x: UInt32, _ n: UInt32) -> UInt32 { (x >> n) | (x << (32 - n)) }

    public static func digest(_ datos: Data) -> [UInt8] {
        var h: [UInt32] = [
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
        ]
        var m = [UInt8](datos)
        let largoEnBits = UInt64(m.count) * 8
        m.append(0x80)
        while m.count % 64 != 56 { m.append(0) }
        for i in (0..<8).reversed() { m.append(UInt8((largoEnBits >> (UInt64(i) * 8)) & 0xff)) }

        var w = [UInt32](repeating: 0, count: 64)
        for bloque in stride(from: 0, to: m.count, by: 64) {
            for t in 0..<16 {
                let i = bloque + t * 4
                w[t] = UInt32(m[i]) << 24 | UInt32(m[i + 1]) << 16 | UInt32(m[i + 2]) << 8 | UInt32(m[i + 3])
            }
            for t in 16..<64 {
                let s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >> 3)
                let s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >> 10)
                w[t] = w[t - 16] &+ s0 &+ w[t - 7] &+ s1
            }
            var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7]
            for t in 0..<64 {
                let s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
                let ch = (e & f) ^ (~e & g)
                let t1 = hh &+ s1 &+ ch &+ k[t] &+ w[t]
                let s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
                let maj = (a & b) ^ (a & c) ^ (b & c)
                let t2 = s0 &+ maj
                hh = g; g = f; f = e; e = d &+ t1
                d = c; c = b; b = a; a = t1 &+ t2
            }
            h[0] = h[0] &+ a; h[1] = h[1] &+ b; h[2] = h[2] &+ c; h[3] = h[3] &+ d
            h[4] = h[4] &+ e; h[5] = h[5] &+ f; h[6] = h[6] &+ g; h[7] = h[7] &+ hh
        }
        return h.flatMap { v in (0..<4).map { UInt8((v >> (24 - UInt32($0) * 8)) & 0xff) } }
    }

    public static func hex(_ datos: Data) -> String {
        digest(datos).map { String(format: "%02x", $0) }.joined()
    }
}
