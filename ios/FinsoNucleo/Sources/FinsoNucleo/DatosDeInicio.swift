import Foundation
import Observation

/// Movimientos de un día, para agruparlos en Inicio («Hoy», «Ayer», …).
public struct DiaDeMovimientos: Identifiable, Equatable, Sendable {
    public var id: String { titulo }
    public var titulo: String
    public var movimientos: [Movimiento]
}

/// Lo que muestra Inicio (§3.3): saldo, movimientos del mes y SPEI por
/// liberar, según los permisos de quien entró.
@MainActor
@Observable
public final class DatosDeInicio {
    public private(set) var lectura: LecturaDeCuenta?
    public private(set) var movimientos: [Movimiento] = []
    public private(set) var porLiberar: [Orden] = []
    public private(set) var cargando = false
    public private(set) var cargandoMas = false
    public private(set) var yaCargo = false
    public private(set) var error: String?
    /// Meses cargados, del actual hacia atrás (`2026-11`, `2026-10`, …).
    public private(set) var meses: [String] = []
    /// Ojo: oculta los importes.
    public var ocultarImportes = false

    public static let mesesMaximos = 12

    @ObservationIgnored public let usuario: Usuario
    @ObservationIgnored let api: FinsoApi
    @ObservationIgnored let reloj: @Sendable () -> Date
    @ObservationIgnored private var cursor: String?

    public init(api: FinsoApi, usuario: Usuario, reloj: @escaping @Sendable () -> Date = { Date() }) {
        self.api = api
        self.usuario = usuario
        self.reloj = reloj
    }

    public var cuenta: Cuenta? { lectura?.cuenta }
    public var puedeMandar: Bool { usuario.puedePedirSpei && !(cuenta?.bloqueada ?? false) }

    /// Agrupados por día, del más reciente al más viejo.
    public var dias: [DiaDeMovimientos] {
        let ahora = reloj()
        var fuera: [DiaDeMovimientos] = []
        for m in movimientos.sorted(by: { $0.fecha > $1.fecha }) {
            let t = Fechas.dia(m.fecha, ahora: ahora)
            if fuera.last?.titulo == t {
                fuera[fuera.count - 1].movimientos.append(m)
            } else {
                fuera.append(DiaDeMovimientos(titulo: t, movimientos: [m]))
            }
        }
        return fuera
    }

    /// «Ver octubre»: el mes anterior que se puede cargar (nil si ya no hay).
    public var mesAnterior: String? {
        guard usuario.puedeVerCuenta, cursor == nil, let ultimo = meses.last,
              meses.count < Self.mesesMaximos
        else { return nil }
        return Fechas.mesAnterior(ultimo)
    }

    /// Todo de nuevo (al entrar y al deslizar hacia abajo).
    public func refrescar() async {
        cargando = true
        error = nil
        defer {
            cargando = false
            yaCargo = true
        }
        if usuario.puedeVerCuenta {
            do {
                async let c = api.cuenta()
                async let p = api.movimientos(mes: Fechas.mes(reloj()), cursor: nil)
                let (lc, pm) = try await (c, p)
                lectura = lc
                movimientos = pm.elementos
                cursor = pm.siguienteCursor
                meses = [pm.mes]
            } catch let e as ErrorFinso {
                error = e.paraPersona
            } catch {
                self.error = ErrorFinso("ERROR").paraPersona
            }
        }
        await refrescarPorLiberar()
    }

    /// Siguiente página del mes, o el mes anterior.
    public func cargarMas() async {
        guard !cargandoMas, usuario.puedeVerCuenta, let mes = cursor != nil ? meses.last : mesAnterior else { return }
        cargandoMas = true
        defer { cargandoMas = false }
        do {
            let p = try await api.movimientos(mes: mes, cursor: cursor)
            let ya = Set(movimientos.map(\.id))
            movimientos += p.elementos.filter { !ya.contains($0.id) }
            cursor = p.siguienteCursor
            if !meses.contains(p.mes) { meses.append(p.mes) }
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }

    /// Solo los SPEI por liberar (al volver de liberar uno).
    public func refrescarPorLiberar() async {
        guard usuario.puedeLiberar else { return }
        if let l = try? await api.porLiberar() { porLiberar = l }
    }
}
