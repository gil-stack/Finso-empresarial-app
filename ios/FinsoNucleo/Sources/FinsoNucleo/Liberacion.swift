import Foundation
import Observation

/// Liberar o rechazar un SPEI que pidió otra persona (§3.5). Misma regla de
/// reintentos que el SPEI: si falla la red se repite con la misma llave y la
/// misma firma; cualquier otro error gasta el reto.
@MainActor
@Observable
public final class Liberacion {
    public private(set) var orden: Orden
    public private(set) var ocupado = false
    public private(set) var error: String?
    /// Para la palomita y la vibración de éxito.
    public private(set) var terminada = false

    public static let motivoMinimo = 5
    public static let motivoMaximo = 500

    @ObservationIgnored let api: FinsoApi
    @ObservationIgnored let almacen: AlmacenDeSesion
    @ObservationIgnored let llave: LlaveDelDispositivo
    @ObservationIgnored let esperar: @Sendable (TimeInterval) async -> Void
    @ObservationIgnored private var intento: (accion: String, reto: String, llave: String, firma: String)?

    public init(
        orden: Orden, api: FinsoApi, almacen: AlmacenDeSesion, llave: LlaveDelDispositivo,
        esperar: @escaping @Sendable (TimeInterval) async -> Void = { s in
            try? await Task.sleep(nanoseconds: UInt64(s * 1_000_000_000))
        }
    ) {
        self.orden = orden
        self.api = api
        self.almacen = almacen
        self.llave = llave
        self.esperar = esperar
    }

    public static func motivoValido(_ m: String) -> Bool {
        (motivoMinimo...motivoMaximo).contains(m.trimmingCharacters(in: .whitespacesAndNewlines).count)
    }

    private var titular: String { Nombre.legible(orden.titular) }

    private func trabajar(_ hacer: () async throws -> Void) async {
        guard !ocupado else { return }
        ocupado = true
        error = nil
        defer { ocupado = false }
        do {
            try await hacer()
        } catch let e as ErrorDeLlave {
            if e.fallo != .cancelada { error = e.paraPersona }
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }

    /// Prepara el reto (o reusa el del intento anterior si falló la red) y
    /// firma con Face ID.
    private func firmar(_ accion: String, texto: (String, String) -> String, motivo: String) async throws
        -> (reto: String, llave: String, firma: String)
    {
        if let i = intento, i.accion == accion { return (i.reto, i.llave, i.firma) }
        guard let dispositivo = almacen.dispositivo() else { throw ErrorFinso("DISPOSITIVO_REVOCADO", http: 401) }
        let p = try await api.prepararLiberacion(orden.id)
        guard p.importeCentavos == orden.importeCentavos, p.version == orden.version else {
            // Cambió mientras se veía: se vuelve a leer.
            if let nueva = try? await api.orden(orden.id) { orden = nueva }
            throw ErrorFinso("ORDEN_CAMBIO", http: 409)
        }
        let firma = try await llave.firmar(texto(dispositivo, p.reto), motivo: motivo)
        let i = (accion: accion, reto: p.reto, llave: nuevaLlaveDeIdempotencia(), firma: firma)
        intento = i
        return (i.reto, i.llave, i.firma)
    }

    private func mandar(_ hacer: () async throws -> Orden) async throws {
        do {
            orden = try await hacer()
            intento = nil
            terminada = true
        } catch let e as ErrorFinso where !e.esSinRed {
            intento = nil
            throw e
        }
    }

    /// «Liberar con Face ID».
    public func liberar() async {
        let o = orden
        await trabajar {
            let f = try await firmar(
                "liberar",
                texto: { TextoFirmado.liberar(dispositivo: $0, reto: $1, orden: o.id, importeCentavos: o.importeCentavos) },
                motivo: "Liberar \(Dinero.pesos(o.importeCentavos)) a \(titular)")
            try await mandar {
                try await api.liberar(orden: o.id, reto: f.reto, firma: f.firma, llaveDeIdempotencia: f.llave)
            }
        }
    }

    /// «Rechazar con Face ID» con el motivo (lo verá quien lo pidió).
    public func rechazar(motivo: String) async {
        let m = motivo.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.motivoValido(m) else { return }
        let o = orden
        await trabajar {
            let f = try await firmar(
                "rechazar", texto: { TextoFirmado.rechazar(dispositivo: $0, reto: $1, orden: o.id) },
                motivo: "Rechazar el SPEI de \(Dinero.pesos(o.importeCentavos))")
            try await mandar {
                try await api.rechazar(
                    orden: o.id, motivo: m, reto: f.reto, firma: f.firma, llaveDeIdempotencia: f.llave)
            }
        }
    }

    /// Después de liberar: consulta cada 3 s mientras esté en proceso.
    public func seguir() async {
        var veces = 0
        while !orden.estado.esFinal, orden.estado != .porLiberar, veces < 20 {
            await esperar(3)
            veces += 1
            if let nueva = try? await api.orden(orden.id) { orden = nueva }
        }
    }
}
