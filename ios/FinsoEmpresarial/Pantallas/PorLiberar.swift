import FinsoNucleo
import SwiftUI

/// SPEI por liberar (§3.5): los que pidió otra persona.
struct PorLiberar: View {
    let app: EstadoDeLaApp
    let datos: DatosDeInicio

    var body: some View {
        List {
            if datos.porLiberar.isEmpty {
                Text("No hay SPEI por liberar.")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.secundario)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 32)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            ForEach(Array(datos.porLiberar.enumerated()), id: \.element.id) { i, o in
                NavigationLink(value: RutaDeInicio.orden(o.id)) {
                    FilaDeOrden(orden: o)
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 20))
                .accessibilityIdentifier("orden-\(o.id)")
                .escalonado(i)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Finso.fondo.ignoresSafeArea())
        .refreshable { await datos.refrescarPorLiberar() }
        .navigationTitle("SPEI por liberar")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
    }
}

struct FilaDeOrden: View {
    let orden: Orden

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { c in
            HStack(spacing: 14) {
                Image(systemName: "hourglass")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Finso.aviso)
                    .frame(width: 44, height: 44)
                    .background(Finso.avisoFondo, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(Nombre.legible(orden.titular))
                        .font(Letra.subtitulo)
                        .foregroundStyle(Finso.texto)
                        .lineLimit(1)
                    Text("pidió \(orden.pedidaPor) · \(Self.hace(orden.creadaEn, ahora: c.date))")
                        .font(Letra.chica)
                        .foregroundStyle(Finso.secundario)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text(Dinero.pesos(orden.importeCentavos))
                    .font(Letra.gully(15, .semibold, relativoA: .subheadline))
                    .monospacedDigit()
                    .foregroundStyle(Finso.texto)
            }
            .padding(.vertical, 4)
        }
    }

    /// «hace 40 min», «hace 2 h», «ayer».
    static func hace(_ d: Date, ahora: Date) -> String {
        let s = max(0, Int(ahora.timeIntervalSince(d)))
        if s < 60 { return "hace un momento" }
        if s < 3600 { return "hace \(s / 60) min" }
        if s < 86_400 { return "hace \(s / 3600) h" }
        return Fechas.fechaCorta(d)
    }
}

/// Detalle de una orden por liberar: liberar o rechazar con Face ID.
struct DetalleDeOrden: View {
    let app: EstadoDeLaApp
    let id: String
    let alTerminar: () -> Void

    @State private var liberacion: Liberacion?
    @State private var error: String?
    @State private var rechazando = false
    @State private var motivo = ""
    @State private var exito = 0

    var body: some View {
        ColumnaDeAcceso {
            if let l = liberacion {
                contenido(l)
            } else if let error {
                AvisoDeError(texto: error)
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(.top, 60)
            }
        }
        .background(Finso.fondo.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .palomita(cuando: exito)
        .task { await cargar() }
        .sheet(isPresented: $rechazando) {
            if let l = liberacion { HojaDeRechazo(liberacion: l, motivo: $motivo) { rechazando = false } }
        }
        .onChange(of: liberacion?.orden) { _, o in
            if let o, liberacion?.terminada == true { ActividadesEnVivo.compartidas.seguir(o) }
        }
        .onChange(of: liberacion?.terminada) { _, t in
            if t == true {
                exito += 1
                alTerminar()
            }
        }
    }

    @ViewBuilder private func contenido(_ l: Liberacion) -> some View {
        let o = l.orden
        if o.estado == .porLiberar {
            VStack(spacing: 6) {
                Text("\(o.pedidaPor) pidió mandar")
                    .font(Letra.cuerpo)
                    .foregroundStyle(Finso.secundario)
                Text(Dinero.pesos(o.importeCentavos))
                    .importe(44)
                    .foregroundStyle(Finso.texto)
                Text("a \(Nombre.legible(o.titular))")
                    .font(Letra.titulo)
                    .foregroundStyle(Finso.texto)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            Tarjeta {
                RenglonDeDetalle(
                    etiqueta: "Cuenta", valor: [o.banco, "•••• \(o.clabeFinal)"].compactMap { $0 }.joined(separator: " · "))
                RenglonDeDetalle(etiqueta: "Concepto", valor: o.concepto)
                if let r = o.referenciaNumerica { RenglonDeDetalle(etiqueta: "Referencia", valor: "\(r)") }
                RenglonDeDetalle(etiqueta: "Pedido", valor: Fechas.fechaYHora(o.creadaEn))
            }
            if let e = l.error { AvisoDeError(texto: e) }
            if o.puedoLiberar {
                Button {
                    Task { await l.liberar() }
                } label: {
                    Label("Liberar con \(Biometria.nombre)", systemImage: Biometria.simbolo)
                }
                .buttonStyle(.principal(cargando: l.ocupado))
                .accessibilityIdentifier("liberar")
                Button("Rechazar", role: .destructive) { rechazando = true }
                    .font(Letra.subtitulo)
                    .foregroundStyle(Finso.peligro)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .disabled(l.ocupado)
                    .accessibilityIdentifier("rechazar")
            }
        } else {
            ResultadoDeOrden(orden: o)
                .task(id: o.estado) { await l.seguir() }
        }
    }

    private func cargar() async {
        guard liberacion == nil else { return }
        let c = app.control
        do {
            let o = try await c.api.orden(id)
            liberacion = Liberacion(orden: o, api: c.api, almacen: c.almacen, llave: c.llave)
        } catch let e as ErrorFinso {
            error = e.paraPersona
        } catch {
            self.error = ErrorFinso("ERROR").paraPersona
        }
    }
}

/// «¿Rechazar este SPEI?» con el motivo (5 a 500 caracteres).
struct HojaDeRechazo: View {
    let liberacion: Liberacion
    @Binding var motivo: String
    let cerrar: () -> Void
    @FocusState private var enfocado: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("¿Rechazar este SPEI?")
                .font(Letra.tituloMedio)
                .foregroundStyle(Finso.texto)
            Text("Motivo (lo verá quien lo pidió)")
                .font(Letra.etiqueta)
                .foregroundStyle(Finso.secundario)
            TextField("", text: $motivo, axis: .vertical)
                .lineLimit(3...6)
                .font(Letra.cuerpoGrande)
                .foregroundStyle(Finso.texto)
                .padding(14)
                .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .focused($enfocado)
                .accessibilityLabel("Motivo")
                .accessibilityIdentifier("motivo")
                .onChange(of: motivo) { _, m in
                    if m.count > Liberacion.motivoMaximo { motivo = String(m.prefix(Liberacion.motivoMaximo)) }
                }
            if let e = liberacion.error { AvisoDeError(texto: e) }
            Button {
                Task {
                    await liberacion.rechazar(motivo: motivo)
                    if liberacion.terminada { cerrar() }
                }
            } label: {
                Label("Rechazar con \(Biometria.nombre)", systemImage: Biometria.simbolo)
                    .font(Letra.boton)
                    .foregroundStyle(Finso.peligroFondo)
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(
                        Finso.peligro.opacity(Liberacion.motivoValido(motivo) ? 1 : 0.3),
                        in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .buttonStyle(Presionable())
            .disabled(!Liberacion.motivoValido(motivo) || liberacion.ocupado)
            .accessibilityIdentifier("confirmarRechazo")
            Spacer(minLength: 0)
        }
        .padding(20)
        .padding(.top, 8)
        .background(Finso.fondo.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .onAppear { enfocado = true }
    }
}
