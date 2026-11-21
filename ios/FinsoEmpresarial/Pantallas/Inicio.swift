import FinsoNucleo
import SwiftUI
import UIKit

/// Rutas dentro de Inicio.
enum RutaDeInicio: Hashable {
    case porLiberar
    case orden(String)
}

/// Inicio (§3.3): saldo, «Mandar SPEI», SPEI por liberar y movimientos del
/// mes, según los permisos.
struct Inicio: View {
    let app: EstadoDeLaApp
    let usuario: Usuario
    @State private var datos: DatosDeInicio
    @State private var ajustes = false
    @State private var mandar = false
    @State private var detalle: Movimiento?
    @State private var ruta: [RutaDeInicio] = []

    init(app: EstadoDeLaApp, usuario: Usuario) {
        self.app = app
        self.usuario = usuario
        _datos = State(initialValue: DatosDeInicio(api: app.control.api, usuario: usuario))
    }

    var body: some View {
        NavigationStack(path: $ruta) {
            List {
                Section {
                    encabezado
                    if usuario.puedeVerCuenta {
                        TarjetaDeSaldo(datos: datos)
                    } else {
                        aviso(
                            "Tu usuario no ve el saldo ni los movimientos. Si los necesitas, pídeselo a tu administrador.",
                            simbolo: "eye.slash")
                    }
                    if datos.puedeMandar {
                        Button {
                            mandar = true
                        } label: {
                            Label("Mandar SPEI", systemImage: "arrow.up.right")
                        }
                        .buttonStyle(.principal)
                        .accessibilityIdentifier("mandarSpei")
                    }
                    if usuario.puedeLiberar && !datos.porLiberar.isEmpty {
                        NavigationLink(value: RutaDeInicio.porLiberar) {
                            FilaPorLiberar(cantidad: datos.porLiberar.count)
                        }
                        .accessibilityIdentifier("porLiberar")
                    }
                    if let e = datos.error { AvisoDeError(texto: e) }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))

                if usuario.puedeVerCuenta { movimientos }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Finso.fondo.ignoresSafeArea())
            .refreshable { await datos.refrescar() }
            .navigationDestination(for: RutaDeInicio.self) { r in
                switch r {
                case .porLiberar:
                    PorLiberar(app: app, datos: datos)
                case .orden(let id):
                    DetalleDeOrden(app: app, id: id) { Task { await datos.refrescarPorLiberar() } }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .tint(Finso.acento)
        .task { if !datos.yaCargo { await datos.refrescar() } }
        .sheet(isPresented: $ajustes) {
            Ajustes(app: app, usuario: usuario)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $detalle) { m in
            DetalleDeMovimiento(movimiento: m, ocultar: datos.ocultarImportes)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .fullScreenCover(isPresented: $mandar, onDismiss: { Task { await datos.refrescar() } }) {
            MandarSpei(app: app, disponible: datos.cuenta?.disponibleCentavos)
        }
    }

    private var encabezado: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Hola, \(usuario.nombreCorto)")
                    .font(Letra.tituloMedio)
                    .tracking(-0.4)
                    .foregroundStyle(Finso.texto)
                    .accessibilityIdentifier("saludoInicio")
                Text(usuario.empresaNombre)
                    .font(Letra.chica)
                    .foregroundStyle(Finso.secundario)
            }
            Spacer()
            BotonRedondo(simbolo: "gearshape", etiqueta: "Ajustes") { ajustes = true }
                .foregroundStyle(Finso.texto)
                .accessibilityIdentifier("ajustes")
        }
        .padding(.top, 8)
    }

    private func aviso(_ texto: String, simbolo: String) -> some View {
        Label(texto, systemImage: simbolo)
            .font(Letra.cuerpo)
            .foregroundStyle(Finso.secundario)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    @ViewBuilder private var movimientos: some View {
        if !datos.yaCargo {
            Section {
                ForEach(0..<5, id: \.self) { _ in FilaEsqueleto() }
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        } else if datos.dias.isEmpty {
            Section {
                Text("Este mes todavía no hay movimientos.")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.secundario)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
        ForEach(datos.dias) { dia in
            Section {
                ForEach(Array(dia.movimientos.enumerated()), id: \.element.id) { i, m in
                    Button {
                        detalle = m
                    } label: {
                        FilaDeMovimiento(movimiento: m, ocultar: datos.ocultarImportes)
                    }
                    .buttonStyle(.plain)
                    .escalonado(i)
                }
            } header: {
                Text(dia.titulo)
                    .font(Letra.etiqueta)
                    .foregroundStyle(Finso.secundario)
                    .textCase(nil)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
        }
        if let mes = datos.mesAnterior {
            Section {
                Button {
                    Task { await datos.cargarMas() }
                } label: {
                    HStack {
                        Spacer()
                        if datos.cargandoMas {
                            ProgressView()
                        } else {
                            Text("Ver \(Fechas.nombreDelMes(mes).split(separator: " ").first.map(String.init) ?? mes)")
                                .font(Letra.subtitulo)
                                .foregroundStyle(Finso.acento)
                        }
                        Spacer()
                    }
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("verMesAnterior")
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        }
    }
}

// MARK: - Tarjeta de saldo

struct TarjetaDeSaldo: View {
    let datos: DatosDeInicio
    @Environment(\.colorScheme) private var esquema
    @Environment(\.accessibilityReduceMotion) private var reducir
    @State private var mostrado = 0.0
    @State private var copiada = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let c = datos.cuenta {
                HStack {
                    Text("Saldo disponible")
                        .font(Letra.cuerpo)
                        .foregroundStyle(Finso.sobreSaldoSecundario)
                    if c.bloqueada {
                        InsigniaDeEstado(texto: "Bloqueada", color: Finso.peligro, fondo: Finso.peligroFondo)
                    }
                    Spacer()
                    BotonRedondo(
                        simbolo: datos.ocultarImportes ? "eye.slash" : "eye",
                        etiqueta: datos.ocultarImportes ? "Mostrar importes" : "Ocultar importes"
                    ) {
                        withAnimation(Ritmo.rapido) { datos.ocultarImportes.toggle() }
                    }
                    .foregroundStyle(Finso.sobreSaldo)
                    .accessibilityIdentifier("ojo")
                }
                Text(datos.ocultarImportes ? Dinero.oculto : Dinero.pesos(Int(mostrado)))
                    .importe(44)
                    .foregroundStyle(Finso.sobreSaldo)
                    .contentTransition(.numericText(value: mostrado))
                    .accessibilityIdentifier("saldo")
                    .accessibilityLabel(datos.ocultarImportes ? "Saldo oculto" : Dinero.pesos(c.disponibleCentavos))
                Text("Saldo total \(datos.ocultarImportes ? Dinero.oculto : Dinero.pesos(c.saldoCentavos))")
                    .font(Letra.chica)
                    .foregroundStyle(Finso.sobreSaldoSecundario)
                Divider().overlay(Finso.sobreSaldo.opacity(0.15))
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("CLABE")
                            .font(Letra.etiqueta)
                            .foregroundStyle(Finso.sobreSaldoSecundario)
                        Text(c.clabeAgrupada)
                            .font(Letra.gully(17, .semibold, relativoA: .headline))
                            .monospacedDigit()
                            .foregroundStyle(Finso.sobreSaldo)
                            .accessibilityIdentifier("clabe")
                    }
                    Spacer()
                    BotonRedondo(simbolo: "doc.on.doc", etiqueta: "Copiar CLABE") {
                        UIPasteboard.general.string = c.clabe
                        copiada += 1
                    }
                    .foregroundStyle(Finso.sobreSaldo)
                }
                if datos.lectura?.sinConexion == true {
                    Text("Actualizado a las \(Fechas.hora(c.horaDelSaldo))")
                        .font(Letra.chica)
                        .foregroundStyle(Finso.sobreSaldoSecundario)
                }
            } else if datos.yaCargo && datos.error == nil {
                Text("Tu cuenta se está abriendo…")
                    .font(Letra.cuerpoGrande)
                    .foregroundStyle(Finso.sobreSaldo)
            } else {
                Text("Saldo disponible")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.sobreSaldoSecundario)
                RoundedRectangle(cornerRadius: 10).fill(Finso.sobreSaldo.opacity(0.12)).frame(width: 220, height: 44)
                RoundedRectangle(cornerRadius: 8).fill(Finso.sobreSaldo.opacity(0.08)).frame(width: 160, height: 16)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(fondo, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .strokeBorder(Color.white.opacity(esquema == .dark ? 0.08 : 0), lineWidth: 1))
        .shadow(color: Finso.sombra, radius: 18, y: 8)
        .palomita(cuando: copiada)
        .sensoryFeedback(.impact(weight: .light), trigger: copiada)
        .onChange(of: datos.cuenta?.disponibleCentavos) { _, nuevo in contar(hasta: nuevo) }
        .onAppear { contar(hasta: datos.cuenta?.disponibleCentavos) }
    }

    /// La primera vez el saldo cuenta de 0 al valor (sin animación con
    /// «reducir movimiento»).
    private func contar(hasta valor: Int?) {
        guard let v = valor else { return }
        if reducir || mostrado != 0 {
            mostrado = Double(v)
        } else {
            withAnimation(.easeOut(duration: 0.9)) { mostrado = Double(v) }
        }
    }

    private var fondo: AnyShapeStyle {
        if esquema == .dark {
            return AnyShapeStyle(RadialGradient(
                colors: [Finso.saldoArriba, Finso.saldoAbajo], center: .topLeading, startRadius: 0, endRadius: 420))
        }
        return AnyShapeStyle(LinearGradient(
            colors: [Finso.saldoArriba, Finso.saldoAbajo], startPoint: .topLeading, endPoint: .bottomTrailing))
    }
}

// MARK: - Filas

struct FilaPorLiberar: View {
    let cantidad: Int

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "hourglass")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Finso.aviso)
                .frame(width: 44, height: 44)
                .background(Finso.avisoFondo, in: Circle())
            Text("SPEI por liberar")
                .font(Letra.titulo)
                .foregroundStyle(Finso.texto)
            Spacer()
            Text("\(cantidad)")
                .font(Letra.etiqueta)
                .foregroundStyle(Finso.tinta)
                .frame(minWidth: 24, minHeight: 24)
                .background(Finso.menta, in: Capsule())
        }
        .padding(.vertical, 4)
    }
}

struct FilaDeMovimiento: View {
    let movimiento: Movimiento
    let ocultar: Bool

    private var nombre: String { Nombre.legible(movimiento.titulo) }

    var body: some View {
        HStack(spacing: 14) {
            Text(String(nombre.prefix(1)))
                .font(Letra.subtitulo)
                .foregroundStyle(movimiento.abono ? Finso.positivo : Finso.texto)
                .frame(width: 44, height: 44)
                .background(movimiento.abono ? Finso.positivoFondo : Finso.elevado, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(nombre)
                    .font(Letra.subtitulo)
                    .foregroundStyle(Finso.texto)
                    .lineLimit(1)
                Text(movimiento.concepto ?? movimiento.contraparte?.banco ?? Fechas.hora(movimiento.fecha))
                    .font(Letra.chica)
                    .foregroundStyle(Finso.secundario)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(ocultar ? Dinero.oculto : Dinero.pesos(movimiento.importeConSigno, conSigno: true))
                .font(Letra.gully(15, .semibold, relativoA: .subheadline))
                .monospacedDigit()
                .foregroundStyle(movimiento.abono ? Finso.positivo : Finso.texto)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

struct FilaEsqueleto: View {
    @State private var brillo = false

    var body: some View {
        HStack(spacing: 14) {
            Circle().fill(Finso.elevado).frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 6).fill(Finso.elevado).frame(width: 160, height: 14)
                RoundedRectangle(cornerRadius: 6).fill(Finso.elevado).frame(width: 100, height: 11)
            }
            Spacer()
            RoundedRectangle(cornerRadius: 6).fill(Finso.elevado).frame(width: 70, height: 14)
        }
        .opacity(brillo ? 0.5 : 1)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { brillo = true }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - Detalle de movimiento

struct DetalleDeMovimiento: View {
    let movimiento: Movimiento
    let ocultar: Bool
    @State private var copiada = 0

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                VStack(spacing: 6) {
                    Text(movimiento.abono ? "Recibiste" : "Enviaste")
                        .font(Letra.cuerpo)
                        .foregroundStyle(Finso.secundario)
                    Text(ocultar ? Dinero.oculto : Dinero.pesos(movimiento.importeCentavos))
                        .importe(40)
                        .foregroundStyle(movimiento.abono ? Finso.positivo : Finso.texto)
                    Text(Nombre.legible(movimiento.titulo))
                        .font(Letra.titulo)
                        .foregroundStyle(Finso.texto)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 24)
                Tarjeta {
                    RenglonDeDetalle(etiqueta: "Fecha", valor: Fechas.fechaYHora(movimiento.fecha))
                    if let b = movimiento.contraparte?.banco { RenglonDeDetalle(etiqueta: "Banco", valor: b) }
                    if let c = movimiento.contraparte?.clabeFinal {
                        RenglonDeDetalle(etiqueta: "Cuenta", valor: "•••• \(c)")
                    }
                    if let c = movimiento.concepto { RenglonDeDetalle(etiqueta: "Concepto", valor: c) }
                    if let r = movimiento.claveRastreo {
                        RenglonDeDetalle(etiqueta: "Clave de rastreo", valor: r)
                            .contentShape(Rectangle())
                            .onLongPressGesture {
                                UIPasteboard.general.string = r
                                copiada += 1
                            }
                    }
                    RenglonDeDetalle(
                        etiqueta: "Saldo después",
                        valor: ocultar ? Dinero.oculto : Dinero.pesos(movimiento.saldoResultanteCentavos))
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Finso.fondo.ignoresSafeArea())
        .palomita(cuando: copiada)
    }
}
