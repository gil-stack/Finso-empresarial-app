import Foundation

/// API con datos de prueba (especificación §5), para usar la app completa sin
/// backend. Contesta con el JSON del contrato y lo pasa por los mismos
/// `init(json:)` que la API real.
///
/// Casos:
///  * Código del correo y del SMS: **123456** (siempre va primero el correo).
///  * `nuevo@…` captura el celular después del correo.
///  * `ana@…` es administradora y liberadora; cualquier otro correo es
///    solicitante (pide SPEI, no ve saldo ni libera).
///  * `varias@…` pertenece a dos empresas y debe elegir.
///  * Todo SPEI queda «Por liberar». Más de $890,000.00 se rechaza al liberar.
public final class ApiSimulada: FinsoApi, @unchecked Sendable {
    public static let codigoCorrecto = "123456"
    public static let tope = 89_000_000

    let reloj: @Sendable () -> Date
    /// Cuánto tarda en contestar, para ver las animaciones de carga.
    let demora: TimeInterval
    /// Cuánto tarda en liquidarse un SPEI liberado.
    let liquidaEn: TimeInterval
    private let lock = NSLock()

    public init(
        reloj: @escaping @Sendable () -> Date = { Date() }, demora: TimeInterval = 0.65,
        liquidaEn: TimeInterval = 2.5
    ) {
        self.reloj = reloj
        self.demora = demora
        self.liquidaEn = liquidaEn
        sembrar()
    }

    // MARK: Estado

    private var saldo = 0
    private var movimientosJson: [Json] = []
    private var destinosJson: [Json] = []
    private var ordenes: [String: Json] = [:]
    private var ordenDeAlta: [String] = []
    private var llaves: [String: String] = [:]
    private var retoActual: (id: String, pendientes: [PasoEntrada])?
    private var correo: String?
    private var sesionAbierta = false
    private var intentos = 0
    private var envios: [PasoEntrada: Int] = [:]
    private var porLlave: [String: String] = [:]
    private var retosDeLiberar: [String: String] = [:]
    private var liberacionesPorLlave: [String: String] = [:]
    private var consecutivo = 0
    private var semilla: UInt64 = 7
    /// Empresa elegida (lo que la app real manda en `X-Finso-Empresa`).
    public private(set) var empresaElegida: String?

    private var ahora: Date { reloj() }

    private func azar(_ hasta: Int) -> Int {
        semilla = semilla &* 6364136223846793005 &+ 1442695040888963407
        return Int((semilla >> 33) % UInt64(hasta))
    }

    private func id(_ prefijo: String) -> String {
        consecutivo += 1
        return "\(prefijo)_SIM" + String(format: "%06d", consecutivo)
    }

    private func iso(_ d: Date) -> String { Iso.texto(d) }

    /// Espera la demora y luego hace el trabajo con el estado bajo candado.
    private func contestar<T>(_ hacer: () throws -> T) async throws -> T {
        if demora > 0 { try? await Task.sleep(nanoseconds: UInt64(demora * 1_000_000_000)) }
        return try lock.con(hacer)
    }

    private func sembrar() {
        let ahora = self.ahora
        saldo = 124_821_537  // $1,248,215.37
        func destino(_ id: String, _ titular: String, _ tipo: String, _ banco: String, _ clabe: String,
                     _ desde: Date, activo: Bool = true) -> Json {
            ["id": id, "titular": titular, "tipo": tipo, "banco": banco, "clabeFinal": clabe,
             "estado": activo ? "ACTIVO" : "EN_ESPERA", "activoDesde": iso(desde)]
        }
        let dia: TimeInterval = 86_400
        destinosJson = [
            destino("dst_SIM_PAPELERA", "PAPELERA DEL CENTRO SA DE CV", "PROVEEDOR", "BBVA MEXICO", "4821",
                    ahora - 40 * dia),
            destino("dst_SIM_FLETES", "FLETES RAPIDOS DEL NORTE", "PROVEEDOR", "BANORTE", "0921", ahora - 12 * dia),
            destino("dst_SIM_LUCIA", "LUCIA HERNANDEZ RUIZ", "COLABORADOR", "SANTANDER", "7710", ahora - 3 * dia),
            // Nuevo: se registró hace 12 minutos y todavía está en espera.
            destino("dst_SIM_EMPAQUES", "EMPAQUES Y CARTON MX", "PROVEEDOR", "BANAMEX", "3305", ahora + 18 * 60,
                    activo: false),
        ]
        let contrapartes: [(String, String, String, Bool)] = [
            ("ABARROTES LA ESPERANZA", "BBVA MEXICO", "1187", true),
            ("PAPELERA DEL CENTRO SA DE CV", "BBVA MEXICO", "4821", false),
            ("COMERCIALIZADORA ALBA", "BANORTE", "6620", true),
            ("FLETES RAPIDOS DEL NORTE", "BANORTE", "0921", false),
            ("TIENDAS MONTERO", "SANTANDER", "0045", true),
        ]
        var s = saldo
        for i in 0..<26 {
            let (nombre, banco, clabe, entra) = contrapartes[i % contrapartes.count]
            let importe = 150_000 + azar(4_500_000)
            let fecha = ahora - TimeInterval((5 + i * 19) * 3600 + azar(50) * 60)
            movimientosJson.append([
                "id": id("mov"), "sentido": entra ? "ABONO" : "CARGO",
                "tipo": entra ? "SPEI_ENTRADA" : "SPEI_SALIDA", "importeCentavos": importe,
                "saldoResultanteCentavos": s, "fecha": iso(fecha),
                "concepto": entra ? "Pago factura \(1200 - i)" : "Pago a proveedor \(300 + i)",
                "claveRastreo": "FINSO\(Int(fecha.timeIntervalSince1970))",
                "contraparte": ["nombre": nombre, "banco": banco, "clabeFinal": clabe],
                "comprobante": !entra,
            ])
            s += entra ? -importe : importe
        }
        let hace40 = iso(ahora - 40 * 60)
        agregarOrden([
            "id": "spo_SIM_PENDIENTE", "estado": "POR_LIBERAR", "destino": resumen(destinosJson[1]),
            "importeCentavos": 1_845_000, "concepto": "Fletes semana 47", "referenciaNumerica": 47,
            "pedidaPor": "Luis Pérez", "creadaEn": hace40, "actualizadaEn": hace40, "version": 1,
        ])
    }

    private func agregarOrden(_ o: Json) {
        let i = o["id"] as! String
        ordenes[i] = o
        ordenDeAlta.append(i)
    }

    private func resumen(_ d: Json) -> Json {
        ["id": d["id"]!, "titular": d["titular"]!, "banco": d["banco"]!, "clabeFinal": d["clabeFinal"]!]
    }

    // MARK: Usuario

    private static let empresas: [Json] = [
        ["id": "emp_SIM", "nombre": "Distribuidora del Bajío"],
        ["id": "emp_SIM2", "nombre": "Transportes del Bajío"],
    ]

    private var esAna: Bool { (correo ?? "ana").hasPrefix("ana") }

    private func usuarioJson() -> Json {
        let ana = esAna
        let varias = (correo ?? "").contains("varias")
        let papeles = ana ? ["ADMINISTRADOR", "LIBERADOR"] : ["SOLICITANTE"]
        let lista = varias ? Self.empresas : [Self.empresas[0]]
        var j: Json = [
            "id": "usr_SIM", "nombre": ana ? "Ana López Garza" : "Luis Pérez Soto",
            "correo": correo ?? "ana@empresa.mx", "papeles": papeles,
            "empresas": lista.map { e -> Json in var e = e; e["papeles"] = papeles; return e },
            "permisos": [
                "verCuenta": ana, "verDestinos": true, "pedirSpei": true, "liberarSpei": ana, "verOrdenes": true,
            ],
            "operacionDelDueno": false,
        ]
        if varias {
            if let e = Self.empresas.first(where: { ($0["id"] as? String) == empresaElegida }) { j["empresa"] = e }
        } else {
            j["empresa"] = Self.empresas[0]
        }
        return j
    }

    private var nombreDeQuienEntro: String { usuarioJson()["nombre"] as! String }

    private func permiso(_ p: String) throws {
        guard sesionAbierta else { throw ErrorFinso("SESION_VENCIDA", http: 401) }
        let u = usuarioJson()
        guard u["empresa"] != nil else { throw ErrorFinso("EMPRESA_REQUERIDA", http: 409) }
        guard ((u["permisos"] as? Json)?[p] as? Bool) == true else { throw ErrorFinso("SIN_PERMISO", http: 403) }
    }

    private func sesionJson() -> Json {
        [
            "acceso": "acc_\(azar(1 << 30))", "accesoExpiraEn": iso(ahora + 600),
            "renovacion": "ren_\(azar(1 << 30))", "renovacionExpiraEn": iso(ahora + 3600),
        ]
    }

    // MARK: §1 Entrar

    public func iniciar(correo: String, contrasena: String, telefono: DatosDelTelefono) async throws -> RetoDeEntrada {
        try await contestar {
            let c = correo.trimmingCharacters(in: .whitespaces).lowercased()
            let partes = c.split(separator: "@")
            guard partes.count == 2, partes[1].contains("."), !contrasena.isEmpty else {
                throw ErrorFinso("CREDENCIALES_INVALIDAS", http: 401)
            }
            self.correo = c
            intentos = 0
            envios = [.codigoCorreo: 1]
            let nuevo = c.contains("nuevo")
            var j: Json = [
                "reto": id("rto"), "expiraEn": iso(ahora + 600),
                "pasos": nuevo ? ["CODIGO_CORREO", "CAPTURAR_CELULAR", "CODIGO_SMS"] : ["CODIGO_CORREO", "CODIGO_SMS"],
                "correoEnmascarado": "\(c.prefix(1))••@\(partes[1])",
                "reenvioDisponibleEn": iso(ahora + 60),
            ]
            if !nuevo { j["celularEnmascarado"] = "•••• ••• 4821" }
            let reto = try RetoDeEntrada(json: j)
            retoActual = (reto.reto, reto.pasos)
            return reto
        }
    }

    private func reto(_ id: String) throws -> [PasoEntrada] {
        guard let r = retoActual, r.id == id else { throw ErrorFinso("RETO_VENCIDO", http: 410) }
        return r.pendientes
    }

    public func capturarCelular(reto: RetoDeEntrada, celular: String) async throws -> RetoDeEntrada {
        try await contestar {
            var pendientes = try self.reto(reto.reto)
            guard pendientes.first == .capturarCelular else { throw ErrorFinso("PASO_FUERA_DE_ORDEN", http: 409) }
            var digitos = celular.filter(\.isNumber)
            if digitos.count == 12 && digitos.hasPrefix("52") { digitos.removeFirst(2) }
            guard digitos.count == 10 else { throw ErrorFinso("CELULAR_INVALIDO", http: 400) }
            pendientes.removeFirst()
            retoActual = (reto.reto, pendientes)
            envios[.codigoSms] = 1
            return reto.conPasos(
                pendientes, celularEnmascarado: "•••• ••• \(digitos.suffix(4))", reenvioDisponibleEn: ahora + 60)
        }
    }

    public func confirmarCodigo(reto: RetoDeEntrada, paso: PasoEntrada, codigo: String) async throws -> RetoDeEntrada {
        try await contestar {
            var pendientes = try self.reto(reto.reto)
            guard pendientes.first == paso else { throw ErrorFinso("PASO_FUERA_DE_ORDEN", http: 409) }
            guard codigo == Self.codigoCorrecto else {
                intentos += 1
                if intentos >= 5 {
                    retoActual = nil
                    throw ErrorFinso("DEMASIADOS_INTENTOS", http: 429)
                }
                throw ErrorFinso("CODIGO_INCORRECTO", http: 400, intentosRestantes: 5 - intentos)
            }
            intentos = 0
            pendientes.removeFirst()
            retoActual = (reto.reto, pendientes)
            // Confirmado el correo con celular ya verificado, el SMS sale solo.
            let smsSale = paso == .codigoCorreo && pendientes.first == .codigoSms
            if smsSale { envios[.codigoSms] = 1 }
            return reto.conPasos(pendientes, reenvioDisponibleEn: smsSale ? ahora + 60 : nil)
        }
    }

    public func reenviarCodigo(reto: String, paso: PasoEntrada) async throws -> Date {
        try await contestar {
            _ = try self.reto(reto)
            let enviados = envios[paso] ?? 0
            guard enviados < 3 else { throw ErrorFinso("DEMASIADOS_INTENTOS", http: 429) }
            envios[paso] = enviados + 1
            return ahora + 60
        }
    }

    public func registrarDispositivo(
        reto: String, llavePublica: String, firma: String, telefono: DatosDelTelefono
    ) async throws -> Ingreso {
        try await contestar {
            guard try self.reto(reto).isEmpty else { throw ErrorFinso("RETO_INCOMPLETO", http: 409) }
            // La simulada no verifica criptografía: solo que haya llave y firma.
            guard !llavePublica.isEmpty else { throw ErrorFinso("LLAVE_INVALIDA", http: 400) }
            guard !firma.isEmpty else { throw ErrorFinso("FIRMA_INVALIDA", http: 401) }
            let dispositivo = id("dev")
            llaves[dispositivo] = llavePublica
            retoActual = nil
            sesionAbierta = true
            return try Ingreso(json: ["dispositivo": dispositivo, "sesion": sesionJson(), "usuario": usuarioJson()])
        }
    }

    public func retoParaEntrar(dispositivo: String) async throws -> RetoDeFirma {
        try await contestar {
            if !llaves.isEmpty && llaves[dispositivo] == nil { throw ErrorFinso("DISPOSITIVO_REVOCADO", http: 401) }
            return try RetoDeFirma(json: ["reto": "r\(azar(1 << 30))", "expiraEn": iso(ahora + 60)])
        }
    }

    public func entrarConBiometria(dispositivo: String, reto: String, firma: String) async throws -> Ingreso {
        try await contestar {
            guard !firma.isEmpty else { throw ErrorFinso("FIRMA_INVALIDA", http: 401) }
            sesionAbierta = true
            return try Ingreso(json: ["sesion": sesionJson(), "usuario": usuarioJson()])
        }
    }

    public func salir() async throws { try await contestar { sesionAbierta = false } }

    public func yo() async throws -> Usuario {
        try await contestar {
            guard sesionAbierta else { throw ErrorFinso("SESION_VENCIDA", http: 401) }
            return try Usuario(json: usuarioJson())
        }
    }

    public func elegirEmpresa(_ id: String) async throws -> Usuario {
        lock.con { empresaElegida = id }
        return try await yo()
    }

    // MARK: §2 Cuenta

    private var disponible: Int {
        saldo - ordenes.values.filter { ["POR_LIBERAR", "EN_PROCESO"].contains($0["estado"] as? String) }
            .reduce(0) { $0 + ($1["importeCentavos"] as? Int ?? 0) }
    }

    public func cuenta() async throws -> LecturaDeCuenta {
        try await contestar {
            try permiso("verCuenta")
            return try LecturaDeCuenta(json: [
                "cuenta": [
                    "clabe": "646180535000004821", "titular": "DISTRIBUIDORA DEL BAJIO SA DE CV", "estado": "VIGENTE",
                    "saldoCentavos": saldo, "disponibleCentavos": disponible, "horaDelSaldo": iso(ahora),
                ] as Json,
                "fuente": "core", "sinConexion": false,
            ])
        }
    }

    public func movimientos(mes: String, cursor: String?) async throws -> PaginaDeMovimientos {
        try await contestar {
            try permiso("verCuenta")
            let delMes = movimientosJson.filter {
                Iso.fecha($0["fecha"] as? String ?? "").map { Fechas.mes($0) } == mes
            }.sorted { ($0["fecha"] as? String ?? "") > ($1["fecha"] as? String ?? "") }
            return try PaginaDeMovimientos(json: [
                "mes": mes, "elementos": delMes, "incompleto": false, "sinConexion": false,
            ])
        }
    }

    // MARK: §3 Destinos

    public func destinos() async throws -> [Destino] {
        try await contestar {
            try permiso("verDestinos")
            for i in destinosJson.indices where destinosJson[i]["estado"] as? String == "EN_ESPERA" {
                if let d = Iso.fecha(destinosJson[i]["activoDesde"] as? String ?? ""), ahora >= d {
                    destinosJson[i]["estado"] = "ACTIVO"
                }
            }
            return try destinosJson.map { try Destino(Lector($0)) }
        }
    }

    // MARK: §4 SPEI

    private func validar(_ s: SolicitudSpei) throws -> Json {
        try permiso("pedirSpei")
        guard let d = destinosJson.first(where: { $0["id"] as? String == s.destino }) else {
            throw ErrorFinso("DESTINO_NO_REGISTRADO", http: 422)
        }
        if d["tipo"] as? String == "COLABORADOR" { throw ErrorFinso("DESTINO_SOLO_NOMINA", http: 422) }
        if let desde = Iso.fecha(d["activoDesde"] as? String ?? ""), ahora < desde {
            throw ErrorFinso("DESTINO_EN_ESPERA", http: 422, reintentarEn: desde)
        }
        guard s.importeCentavos > 0 else { throw ErrorFinso("IMPORTE_INVALIDO", http: 400) }
        guard SolicitudSpei.conceptoValido(s.concepto) else { throw ErrorFinso("CONCEPTO_INVALIDO", http: 400) }
        guard s.importeCentavos <= disponible else { throw ErrorFinso("SALDO_INSUFICIENTE", http: 422) }
        return d
    }

    public func prepararSpei(_ solicitud: SolicitudSpei) async throws -> PreparacionSpei {
        try await contestar {
            let d = try validar(solicitud)
            return try PreparacionSpei(json: [
                "reto": "s\(azar(1 << 30))", "expiraEn": iso(ahora + 120),
                "resumen": [
                    "destino": resumen(d), "importeCentavos": solicitud.importeCentavos, "comisionCentavos": 0,
                    "concepto": solicitud.concepto,
                ] as Json,
                "requiereLiberador": true,
            ])
        }
    }

    /// La orden como la ve esta persona (`puedoLiberar`).
    private func comoLaVe(_ o: Json) throws -> Orden {
        let u = usuarioJson()
        var o = o
        o["puedoLiberar"] = o["estado"] as? String == "POR_LIBERAR"
            && ((u["permisos"] as? Json)?["liberarSpei"] as? Bool) == true
            && o["pedidaPor"] as? String != u["nombre"] as? String
        return try Orden(json: o)
    }

    public func mandarSpei(
        _ solicitud: SolicitudSpei, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden {
        try await contestar {
            if let repetida = porLlave[llaveDeIdempotencia], let o = ordenes[repetida] { return try comoLaVe(o) }
            let d = try validar(solicitud)
            guard !firma.isEmpty else { throw ErrorFinso("FIRMA_INVALIDA", http: 401) }
            let i = id("spo")
            let o: Json = [
                "id": i, "estado": "POR_LIBERAR", "destino": resumen(d), "importeCentavos": solicitud.importeCentavos,
                // La asigna el servidor, fija por orden.
                "concepto": solicitud.concepto, "referenciaNumerica": 100 + ordenes.count,
                "pedidaPor": nombreDeQuienEntro, "creadaEn": iso(ahora), "actualizadaEn": iso(ahora), "version": 1,
            ]
            agregarOrden(o)
            porLlave[llaveDeIdempotencia] = i
            return try comoLaVe(o)
        }
    }

    private func orden(json id: String) throws -> Json {
        guard let o = ordenes[id] else { throw ErrorFinso("NO_ENCONTRADA", http: 404) }
        return o
    }

    public func orden(_ id: String) async throws -> Orden {
        try await contestar {
            try permiso("verOrdenes")
            return try comoLaVe(try orden(json: id))
        }
    }

    public func porLiberar() async throws -> [Orden] {
        try await contestar {
            try permiso("verOrdenes")
            let u = usuarioJson()
            guard ((u["permisos"] as? Json)?["liberarSpei"] as? Bool) == true else { return [] }
            return try ordenDeAlta.compactMap { ordenes[$0] }.filter {
                $0["estado"] as? String == "POR_LIBERAR" && $0["pedidaPor"] as? String != u["nombre"] as? String
            }.map(comoLaVe)
        }
    }

    private func liberable(_ id: String) throws -> Json {
        try permiso("liberarSpei")
        let o = try orden(json: id)
        switch o["estado"] as? String {
        case "CANCELADA": throw ErrorFinso("CANCELADA", http: 409)
        case "POR_LIBERAR": break
        default: throw ErrorFinso("YA_LIBERADA", http: 409)
        }
        if o["pedidaPor"] as? String == nombreDeQuienEntro { throw ErrorFinso("MISMA_PERSONA", http: 409) }
        return o
    }

    public func prepararLiberacion(_ orden: String) async throws -> PreparacionDeLiberacion {
        try await contestar {
            let o = try liberable(orden)
            let reto = "l\(azar(1 << 30))"
            retosDeLiberar[reto] = orden
            return try PreparacionDeLiberacion(json: [
                "reto": reto, "expiraEn": iso(ahora + 120), "importeCentavos": o["importeCentavos"]!,
                "version": o["version"] ?? 1,
            ])
        }
    }

    private func usarReto(_ reto: String, _ orden: String, _ firma: String) throws {
        guard retosDeLiberar.removeValue(forKey: reto) == orden else { throw ErrorFinso("RETO_VENCIDO", http: 410) }
        guard !firma.isEmpty else { throw ErrorFinso("FIRMA_INVALIDA", http: 401) }
    }

    private func cambiar(_ id: String, _ cambios: Json) {
        guard var o = ordenes[id] else { return }
        for (k, v) in cambios { o[k] = v }
        o["actualizadaEn"] = iso(ahora)
        o["version"] = (o["version"] as? Int ?? 1) + 1
        ordenes[id] = o
    }

    public func liberar(orden: String, reto: String, firma: String, llaveDeIdempotencia: String) async throws -> Orden {
        try await contestar {
            if liberacionesPorLlave[llaveDeIdempotencia] == orden { return try comoLaVe(try self.orden(json: orden)) }
            let o = try liberable(orden)
            try usarReto(reto, orden, firma)
            liberacionesPorLlave[llaveDeIdempotencia] = orden
            // Los límites los aplica el core al liberar (§4.1 v1.1).
            if (o["importeCentavos"] as? Int ?? 0) > Self.tope {
                cambiar(orden, [
                    "estado": "RECHAZADA", "liberadaPor": nombreDeQuienEntro,
                    "motivo": "Pasa el tope por SPEI de $890,000.00.",
                ])
            } else {
                cambiar(orden, ["estado": "EN_PROCESO", "liberadaPor": nombreDeQuienEntro])
                programarLiquidacion(orden)
            }
            return try comoLaVe(try self.orden(json: orden))
        }
    }

    public func rechazar(
        orden: String, motivo: String, reto: String, firma: String, llaveDeIdempotencia: String
    ) async throws -> Orden {
        try await contestar {
            if liberacionesPorLlave[llaveDeIdempotencia] == orden { return try comoLaVe(try self.orden(json: orden)) }
            let m = motivo.trimmingCharacters(in: .whitespacesAndNewlines)
            guard (5...500).contains(m.count) else { throw ErrorFinso("DATOS_INVALIDOS", http: 400, campos: ["motivo"]) }
            _ = try liberable(orden)
            try usarReto(reto, orden, firma)
            liberacionesPorLlave[llaveDeIdempotencia] = orden
            cambiar(orden, ["estado": "RECHAZADA", "motivo": m])
            return try comoLaVe(try self.orden(json: orden))
        }
    }

    /// En la simulada el SPEI se liquida `liquidaEn` segundos después de salir.
    private func programarLiquidacion(_ id: String) {
        let espera = liquidaEn
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(espera * 1_000_000_000))
            self?.liquidar(id)
        }
    }

    private func liquidar(_ id: String) {
        lock.lock()
        defer { lock.unlock() }
        guard let o = ordenes[id], o["estado"] as? String == "EN_PROCESO" else { return }
        let rastreo = "FINSO\(Int(ahora.timeIntervalSince1970))"
        let importe = o["importeCentavos"] as? Int ?? 0
        cambiar(id, ["estado": "LIQUIDADA", "claveRastreo": rastreo])
        saldo -= importe
        let d = o["destino"] as? Json ?? [:]
        movimientosJson.append([
            "id": self.id("mov"), "sentido": "CARGO", "tipo": "SPEI_SALIDA", "importeCentavos": importe,
            "saldoResultanteCentavos": saldo, "fecha": iso(ahora), "concepto": o["concepto"] ?? "",
            "claveRastreo": rastreo,
            "contraparte": ["nombre": d["titular"] ?? "", "banco": d["banco"] ?? "", "clabeFinal": d["clabeFinal"] ?? ""],
            "orden": id, "comprobante": true,
        ])
    }
}
