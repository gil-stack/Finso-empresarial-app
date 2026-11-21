import FinsoNucleo
import SwiftUI

/// Mandar un SPEI (§3.4), a pantalla completa: destino → importe y concepto →
/// revisar → resultado.
struct MandarSpei: View {
    let app: EstadoDeLaApp
    let usuario: Usuario
    @State private var envio: EnvioSpei
    @Environment(\.dismiss) private var cerrar
    @Environment(\.accessibilityReduceMotion) private var reducir

    init(app: EstadoDeLaApp, usuario: Usuario, disponible: Int?) {
        self.app = app
        self.usuario = usuario
        _envio = State(initialValue: app.envioSpei(para: usuario, disponible: disponible))
    }

    var body: some View {
        NavigationStack {
            contenido.toolbar(.hidden, for: .navigationBar)
        }
        .tint(Finso.acento)
        .onDisappear { app.cerroSpei(envio, usuario: usuario) }
    }

    private var contenido: some View {
        ZStack {
            Finso.fondo.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    if envio.paso != .resultado {
                        BotonRedondo(
                            simbolo: envio.paso == .destino || envio.regresarSale ? "xmark" : "chevron.left",
                            etiqueta: envio.paso == .destino || envio.regresarSale ? "Cerrar" : "Regresar"
                        ) {
                            if envio.paso == .destino || envio.regresarSale {
                                cerrar()
                            } else {
                                withAnimation(Ritmo.normal) { envio.regresar() }
                            }
                        }
                        .foregroundStyle(Finso.texto)
                        .accessibilityIdentifier("regresarSpei")
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .frame(minHeight: 48)
                Group {
                    switch envio.paso {
                    case .destino: ElegirDestino(envio: envio)
                    case .importe: ImporteYConcepto(envio: envio)
                    case .revisar: RevisarSpei(envio: envio)
                    case .resultado: ResultadoDeSpei(envio: envio) { cerrar() }
                    }
                }
                .id(envio.paso)
                .transition(Ritmo.paso(reducir: reducir))
            }
        }
        .animation(Ritmo.normal, value: envio.paso)
        .animation(Ritmo.rapido, value: envio.error)
        .sensoryFeedback(.error, trigger: envio.error) { _, nuevo in nuevo != nil && envio.paso == .revisar }
    }
}

// MARK: - 1. Destino

struct ElegirDestino: View {
    let envio: EnvioSpei

    var body: some View {
        ColumnaDeAcceso {
            EncabezadoDePaso(
                paso: nil, titulo: "¿A quién le mandas?",
                explicacion:
                    "Solo a cuentas registradas en el portal. Las nuevas se activan 30 minutos después de registrarlas.")
            if envio.cargando && envio.destinos.isEmpty {
                ForEach(0..<4, id: \.self) { _ in FilaEsqueleto() }
            } else if envio.destinos.isEmpty && envio.error == nil {
                Text("Todavía no tienes cuentas destino. Regístralas en el portal: Cuenta > Cuentas destino.")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.secundario)
                    .padding(.vertical, 12)
            }
            if let e = envio.error { AvisoDeError(texto: e) }
            TimelineView(.periodic(from: .now, by: 15)) { _ in
                VStack(spacing: 6) {
                    ForEach(Array(envio.destinosOrdenados.enumerated()), id: \.element.id) { i, d in
                        FilaDeDestino(destino: d, estado: envio.estado(de: d)) { envio.elegir(d) }
                            .escalonado(i)
                    }
                }
                .task(id: envio.hayQueRecargar) {
                    if envio.hayQueRecargar { await envio.cargarDestinos() }
                }
            }
        }
        .task { await envio.cargarDestinos() }
    }
}

struct FilaDeDestino: View {
    let destino: Destino
    let estado: EstadoDeDestino
    let elegir: () -> Void

    var body: some View {
        Button(action: elegir) {
            HStack(spacing: 14) {
                Image(systemName: simbolo)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(estado.sePuedeTocar ? Finso.acento : Finso.secundario)
                    .frame(width: 44, height: 44)
                    .background(Finso.elevado, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(Nombre.legible(destino.titular))
                        .font(Letra.subtitulo)
                        .foregroundStyle(Finso.texto)
                        .lineLimit(1)
                    Text(estado.texto(destino))
                        .font(Letra.chica)
                        .foregroundStyle(colorDelDetalle)
                        .lineLimit(2)
                }
                Spacer(minLength: 8)
                if estado.sePuedeTocar {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Finso.secundario)
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
            .opacity(estado.sePuedeTocar ? 1 : 0.55)
        }
        .buttonStyle(Presionable())
        .disabled(!estado.sePuedeTocar)
        .accessibilityIdentifier("destino-\(destino.id)")
    }

    private var simbolo: String {
        switch estado {
        case .listo: return "arrow.up.right"
        case .enEspera, .activandose: return "clock"
        case .soloNomina: return "person.text.rectangle"
        }
    }

    private var colorDelDetalle: Color {
        switch estado {
        case .enEspera, .activandose: return Finso.aviso
        default: return Finso.secundario
        }
    }
}

// MARK: - 2. Importe y concepto

struct ImporteYConcepto: View {
    let envio: EnvioSpei
    @FocusState private var enfocado: Campo?
    // Copias locales de lo tecleado: un TextField con Binding que transforma
    // el valor no siempre repinta lo limpio (la ñ se quedaba en pantalla).
    // Se limpian en onChange y se escriben de vuelta, que sí repinta.
    @State private var importe = ""
    @State private var concepto = ""

    enum Campo { case importe, concepto }

    private var tamano: CGFloat {
        switch envio.importeTecleado.count {
        case ..<9: return 48
        case ..<13: return 40
        default: return 32
        }
    }

    var body: some View {
        ColumnaDeAcceso {
            if let d = envio.destino {
                EncabezadoDePaso(paso: nil, titulo: Nombre.legible(d.titular), explicacion: d.detalle)
            }
            VStack(spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("$")
                        .font(Letra.gully(tamano * 0.6, .bold, relativoA: .largeTitle))
                        .foregroundStyle(Finso.secundario)
                    TextField(
                        "0",
                        text: $importe
                    )
                    .keyboardType(.decimalPad)
                    .font(Letra.gully(tamano, .bold, relativoA: .largeTitle))
                    .monospacedDigit()
                    .foregroundStyle(Finso.texto)
                    .fixedSize()
                    .focused($enfocado, equals: .importe)
                    .accessibilityLabel("Importe")
                    .accessibilityIdentifier("importe")
                    .onChange(of: importe) { _, nuevo in
                        envio.teclearImporte(nuevo)
                        if envio.importeTecleado != nuevo { importe = envio.importeTecleado }
                    }
                }
                .frame(maxWidth: .infinity)
                .animation(Ritmo.rapido, value: tamano)
                if let disp = envio.disponible {
                    Text(envio.pasaDelDisponible
                        ? "Pasa tu disponible de \(Dinero.pesos(disp))" : "Disponible \(Dinero.pesos(disp))")
                        .font(Letra.chica)
                        .foregroundStyle(envio.pasaDelDisponible ? Finso.peligro : Finso.secundario)
                        .accessibilityIdentifier("disponible")
                }
            }
            .padding(.vertical, 16)
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Concepto")
                        .font(Letra.etiqueta)
                        .foregroundStyle(Finso.secundario)
                    Spacer()
                    Text("\(envio.concepto.count)/\(SolicitudSpei.largoConcepto)")
                        .font(Letra.etiqueta)
                        .monospacedDigit()
                        .foregroundStyle(Finso.secundario)
                }
                TextField(
                    "", text: $concepto
                )
                .font(Letra.cuerpoGrande)
                .foregroundStyle(Finso.texto)
                .textInputAutocapitalization(.sentences)
                .autocorrectionDisabled()
                .focused($enfocado, equals: .concepto)
                .padding(.horizontal, 16)
                .frame(minHeight: 56)
                .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityLabel("Concepto")
                .accessibilityIdentifier("concepto")
                .onChange(of: concepto) { _, nuevo in
                    envio.teclearConcepto(nuevo)
                    if envio.concepto != nuevo { concepto = envio.concepto }
                }
                Text("Sin acentos ni ñ (así lo pide SPEI).")
                    .font(Letra.chica)
                    .foregroundStyle(Finso.secundario)
            }
            if let e = envio.error { AvisoDeError(texto: e) }
            Button("Revisar") {
                enfocado = nil
                Task { await envio.revisar() }
            }
            .buttonStyle(.principal(cargando: envio.ocupado))
            .disabled(!envio.puedeRevisar)
            .accessibilityIdentifier("revisar")
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Listo") { enfocado = enfocado == .importe ? .concepto : nil }
            }
        }
        .onAppear {
            importe = envio.importeTecleado
            concepto = envio.concepto
            enfocado = .importe
        }
    }
}

// MARK: - 3. Revisar

struct RevisarSpei: View {
    let envio: EnvioSpei

    var body: some View {
        ColumnaDeAcceso {
            if let p = envio.preparacion {
                VStack(spacing: 6) {
                    Text("Vas a mandar")
                        .font(Letra.cuerpo)
                        .foregroundStyle(Finso.secundario)
                    Text(Dinero.pesos(p.importeCentavos))
                        .importe(44)
                        .foregroundStyle(Finso.texto)
                    Text("a \(Nombre.legible(p.titular))")
                        .font(Letra.titulo)
                        .foregroundStyle(Finso.texto)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                Tarjeta {
                    RenglonDeDetalle(
                        etiqueta: "Cuenta",
                        valor: [p.banco, "•••• \(p.clabeFinal)"].compactMap { $0 }.joined(separator: " · "))
                    RenglonDeDetalle(etiqueta: "Concepto", valor: p.concepto)
                    RenglonDeDetalle(
                        etiqueta: "Comisión",
                        valor: p.comisionCentavos == 0 ? "Sin comisión" : Dinero.pesos(p.comisionCentavos))
                    RenglonDeDetalle(etiqueta: "Total", valor: Dinero.pesos(p.importeCentavos + p.comisionCentavos))
                }
                if p.requiereLiberador {
                    Label(
                        "Después de confirmarlo, otra persona de tu empresa tiene que liberarlo para que salga.",
                        systemImage: "person.2")
                        .font(Letra.cuerpo)
                        .foregroundStyle(Finso.aviso)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Finso.avisoFondo, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            if let e = envio.error { AvisoDeError(texto: e) }
            Button {
                Task { await envio.pedir() }
            } label: {
                if envio.sinConfirmar {
                    Label("Intentar otra vez", systemImage: "arrow.clockwise")
                } else {
                    Label("Pedir con \(Biometria.nombre)", systemImage: Biometria.simbolo)
                }
            }
            .buttonStyle(.principal(cargando: envio.ocupado))
            .accessibilityIdentifier("pedir")
        }
    }
}

// MARK: - 4. Resultado

struct ResultadoDeOrden: View {
    let orden: Orden

    private var simbolo: (String, Color, Color) {
        switch orden.estado {
        case .porLiberar: return ("hourglass", Finso.aviso, Finso.avisoFondo)
        case .enProceso, .enviada: return ("arrow.up.right", Finso.acento, Finso.positivoFondo)
        case .liquidada: return ("checkmark", Finso.palomita, Finso.positivoFondo)
        case .rechazada, .devuelta, .cancelada: return ("xmark", Finso.peligro, Finso.peligroFondo)
        }
    }

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: simbolo.0)
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(simbolo.1)
                .contentTransition(.symbolEffect(.replace))
                .frame(width: 92, height: 92)
                .background(simbolo.2, in: Circle())
                .padding(.top, 12)
            Text(orden.estado.tituloDeResultado)
                .font(Letra.tituloGrande)
                .foregroundStyle(Finso.texto)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("tituloResultado")
            if orden.estado == .rechazada, let m = orden.motivo {
                Text(m)
                    .font(Letra.cuerpoGrande)
                    .foregroundStyle(Finso.secundario)
                    .multilineTextAlignment(.center)
            }
            if let quien = orden.liberadaPor, orden.estado != .rechazada {
                Text("Liberada por \(quien)")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.secundario)
            }
            Tarjeta {
                RenglonDeDetalle(etiqueta: "Importe", valor: Dinero.pesos(orden.importeCentavos))
                RenglonDeDetalle(
                    etiqueta: "Cuenta",
                    valor: "\(Nombre.legible(orden.titular)) · •••• \(orden.clabeFinal)")
                RenglonDeDetalle(etiqueta: "Concepto", valor: orden.concepto)
                if let r = orden.referenciaNumerica { RenglonDeDetalle(etiqueta: "Referencia", valor: "\(r)") }
                if let c = orden.claveRastreo { RenglonDeDetalle(etiqueta: "Clave de rastreo", valor: c) }
                HStack {
                    Text("Estado").font(Letra.cuerpo).foregroundStyle(Finso.secundario)
                    Spacer()
                    InsigniaDeEstado(texto: orden.estado.etiqueta, color: simbolo.1, fondo: simbolo.2)
                }
                .padding(.vertical, 10)
            }
        }
        .animation(Ritmo.resorte, value: orden.estado)
    }
}

struct ResultadoDeSpei: View {
    let envio: EnvioSpei
    let listo: () -> Void

    var body: some View {
        ColumnaDeAcceso {
            if let o = envio.orden { ResultadoDeOrden(orden: o) }
            Button("Listo", action: listo)
                .buttonStyle(.principal)
                .accessibilityIdentifier("listo")
        }
        .sensoryFeedback(.success, trigger: envio.orden?.id)
        .task { await envio.seguir() }
    }
}
