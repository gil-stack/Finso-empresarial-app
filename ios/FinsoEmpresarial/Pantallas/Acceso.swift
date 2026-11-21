import FinsoNucleo
import LocalAuthentication
import SwiftUI

/// Bienvenida y primer ingreso (§3.1). Siempre en oscuro, sobre el fondo de
/// marca. Los pasos entran deslizándose 6 % desde la derecha con fundido.
struct Acceso: View {
    let app: EstadoDeLaApp
    let aviso: String?
    @State private var flujo: FlujoDeEntrada?
    @Environment(\.accessibilityReduceMotion) private var reducir

    var body: some View {
        ZStack {
            if let flujo {
                PasosDeEntrada(flujo: flujo, simulada: app.simulada) {
                    withAnimation(Movimiento.normal) { self.flujo = nil }
                }
                .transition(Movimiento.paso(reducir: reducir))
            } else {
                Bienvenida(simulada: app.simulada, aviso: aviso) {
                    app.control.avisoVisto()
                    withAnimation(Movimiento.normal) { flujo = app.nuevoFlujo() }
                }
                .transition(.opacity)
            }
        }
        .environment(\.colorScheme, .dark)
    }
}

/// Los pasos del primer ingreso, en el orden que manda el servidor.
struct PasosDeEntrada: View {
    let flujo: FlujoDeEntrada
    let simulada: Bool
    let salir: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reducir

    var body: some View {
        ZStack {
            FondoDeAcceso()
            VStack(spacing: 0) {
                HStack {
                    BotonRedondo(simbolo: "chevron.left", etiqueta: "Regresar") {
                        if flujo.paso == .correo {
                            salir()
                        } else {
                            withAnimation(Movimiento.normal) { flujo.volverAlCorreo() }
                        }
                    }
                    .foregroundStyle(Finso.texto)
                    .accessibilityIdentifier("regresar")
                    Spacer()
                    MarcaFinso(alto: 22).foregroundStyle(Finso.texto)
                    Spacer()
                    Color.clear.frame(width: 44, height: 44)
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                pasoActual
                    .id(claveDelPaso)
                    .transition(Movimiento.paso(reducir: reducir))
            }
        }
        .animation(Movimiento.normal, value: flujo.paso)
        .animation(Movimiento.rapido, value: flujo.error)
    }

    private var claveDelPaso: String {
        switch flujo.paso {
        case .correo: return "correo"
        case .codigo(let p): return p.rawValue
        case .celular: return "celular"
        case .biometria: return "biometria"
        }
    }

    @ViewBuilder private var pasoActual: some View {
        switch flujo.paso {
        case .correo: PasoCorreo(flujo: flujo)
        case .codigo(let p): PasoCodigo(flujo: flujo, paso: p, simulada: simulada)
        case .celular: PasoCelular(flujo: flujo)
        case .biometria: PasoBiometria(flujo: flujo)
        }
    }
}

// MARK: - Correo y contraseña

struct PasoCorreo: View {
    let flujo: FlujoDeEntrada
    @State private var correo = ""
    @State private var contrasena = ""

    var body: some View {
        ColumnaDeAcceso {
            EncabezadoDePaso(
                paso: flujo.textoDelPaso, titulo: "Entra a tu cuenta",
                explicacion: "Usa el correo y la contraseña de Finso Empresas.")
            CampoDeAcceso(
                etiqueta: "Correo", texto: $correo, teclado: .emailAddress, contenido: .username,
                identificador: "correo")
            CampoDeAcceso(
                etiqueta: "Contraseña", texto: $contrasena, secreto: true, contenido: .password,
                identificador: "contrasena")
            if let e = flujo.error { AvisoDeError(texto: e) }
            Button("Continuar") {
                Task { await flujo.continuar(correo: correo, contrasena: contrasena) }
            }
            .buttonStyle(.principal(cargando: flujo.ocupado))
            .disabled(correo.isEmpty || contrasena.isEmpty)
            .accessibilityIdentifier("continuar")
            .padding(.top, 4)
        }
        .onSubmit { Task { await flujo.continuar(correo: correo, contrasena: contrasena) } }
    }
}

// MARK: - Código (correo o SMS)

struct PasoCodigo: View {
    let flujo: FlujoDeEntrada
    let paso: PasoEntrada
    let simulada: Bool
    @State private var codigo = ""
    @FocusState private var enfocado: Bool

    private var esCorreo: Bool { paso == .codigoCorreo }

    private var explicacion: String {
        if esCorreo {
            return "Te mandamos un código de 6 dígitos a \(flujo.correoEnmascarado ?? "tu correo")."
        }
        return "Te mandamos un código de 6 dígitos por SMS al \(flujo.celularEnmascarado ?? "celular registrado")."
    }

    var body: some View {
        ColumnaDeAcceso {
            EncabezadoDePaso(
                paso: flujo.textoDelPaso, titulo: esCorreo ? "Revisa tu correo" : "Revisa tus mensajes",
                explicacion: explicacion)
            casillas
                .modifier(Sacudida(veces: CGFloat(flujo.codigosEquivocados)))
                .animation(.linear(duration: 0.4), value: flujo.codigosEquivocados)
                .sensoryFeedback(.error, trigger: flujo.codigosEquivocados)
            if let e = flujo.error { AvisoDeError(texto: e) }
            HStack {
                TimelineView(.periodic(from: .now, by: 1)) { contexto in
                    if let espera = flujo.textoDeReenvio(ahora: contexto.date) {
                        Text(espera)
                            .font(Letra.cuerpo)
                            .foregroundStyle(Finso.secundario)
                            .monospacedDigit()
                    } else {
                        Button("Pedir otro código") { Task { await flujo.reenviar() } }
                            .font(Letra.subtitulo)
                            .foregroundStyle(Finso.acento)
                            .disabled(flujo.ocupado)
                            .accessibilityIdentifier("reenviar")
                    }
                }
                Spacer()
                if flujo.ocupado { ProgressView().tint(Finso.menta) }
            }
            if simulada {
                Text("Modo de prueba: el código es 123456")
                    .font(Letra.chica)
                    .foregroundStyle(Finso.secundario)
            }
        }
        .onAppear { enfocado = true }
        .onChange(of: flujo.codigosEquivocados) { _, _ in codigo = "" }
    }

    /// 6 casillas sobre un campo casi invisible que recibe el teclado y el
    /// autollenado del código de SMS.
    private var casillas: some View {
        let digitos = Array(codigo)
        return ZStack {
            HStack(spacing: 10) {
                ForEach(0..<6, id: \.self) { i in
                    let actual = i == digitos.count && enfocado
                    Text(i < digitos.count ? String(digitos[i]) : "")
                        .font(Letra.gully(26, .bold, relativoA: .title))
                        .foregroundStyle(Finso.texto)
                        .frame(maxWidth: .infinity, minHeight: 58)
                        .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(actual ? Finso.menta : .clear, lineWidth: 1.5))
                        .scaleEffect(i < digitos.count ? 1 : 0.98)
                        .animation(Movimiento.resorte, value: digitos.count)
                }
            }
            .accessibilityHidden(true)
            TextField("", text: $codigo)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($enfocado)
                .foregroundStyle(.clear)
                .tint(.clear)
                .opacity(0.02)
                .frame(maxWidth: .infinity, minHeight: 58)
                .accessibilityLabel("Código de 6 dígitos")
                .accessibilityIdentifier("codigo")
                .onChange(of: codigo) { _, nuevo in
                    let limpio = String(nuevo.filter(\.isNumber).prefix(6))
                    if limpio != nuevo { codigo = limpio }
                    if limpio.count == 6 && !flujo.ocupado {
                        Task { await flujo.confirmar(codigo: limpio) }
                    }
                }
        }
        .contentShape(Rectangle())
        .onTapGesture { enfocado = true }
    }
}

// MARK: - Celular

struct PasoCelular: View {
    let flujo: FlujoDeEntrada
    @State private var celular = ""

    var body: some View {
        ColumnaDeAcceso {
            EncabezadoDePaso(
                paso: flujo.textoDelPaso, titulo: "Tu celular",
                explicacion: "Te mandaremos un código por SMS para confirmarlo.")
            HStack(spacing: 10) {
                Text("+52")
                    .font(Letra.cuerpoGrande)
                    .foregroundStyle(Finso.secundario)
                    .frame(minHeight: 56)
                    .padding(.horizontal, 14)
                    .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .padding(.top, 25)
                CampoDeAcceso(
                    etiqueta: "Celular (10 dígitos)", texto: $celular, teclado: .numberPad,
                    contenido: .telephoneNumber, identificador: "celular")
            }
            if let e = flujo.error { AvisoDeError(texto: e) }
            Button("Mandar código") { Task { await flujo.capturar(celular: celular) } }
                .buttonStyle(.principal(cargando: flujo.ocupado))
                .disabled(celular.filter(\.isNumber).count != 10)
                .accessibilityIdentifier("mandarCodigo")
        }
        .onChange(of: celular) { _, nuevo in
            let f = Self.agrupar(nuevo)
            if f != nuevo { celular = f }
        }
    }

    /// `5512345678` → `55 1234 5678`.
    static func agrupar(_ s: String) -> String {
        var d = s.filter(\.isNumber)
        if d.count > 10 && d.hasPrefix("52") { d.removeFirst(2) }
        d = String(d.prefix(10))
        var fuera = ""
        for (i, c) in d.enumerated() {
            if i == 2 || i == 6 { fuera.append(" ") }
            fuera.append(c)
        }
        return fuera
    }
}

// MARK: - Activar Face ID

enum Biometria {
    /// «Face ID» o «Touch ID», según el teléfono.
    static var nombre: String {
        let c = LAContext()
        _ = c.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return c.biometryType == .touchID ? "Touch ID" : "Face ID"
    }

    static var simbolo: String { nombre == "Touch ID" ? "touchid" : "faceid" }
}

struct PasoBiometria: View {
    let flujo: FlujoDeEntrada
    @State private var latido = false

    var body: some View {
        ColumnaDeAcceso {
            EncabezadoDePaso(
                paso: flujo.textoDelPaso, titulo: "Activa \(Biometria.nombre)",
                explicacion:
                    "Desde ahora entras y firmas tus SPEI con \(Biometria.nombre). La llave vive en el chip seguro de este iPhone y nunca sale de ahí.")
            Image(systemName: Biometria.simbolo)
                .font(.system(size: 76, weight: .light))
                .foregroundStyle(Finso.menta)
                .symbolEffect(.pulse, options: .repeating, isActive: flujo.ocupado)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .accessibilityHidden(true)
            if let e = flujo.error { AvisoDeError(texto: e) }
            Button("Activar \(Biometria.nombre)") { Task { await flujo.activarBiometria() } }
                .buttonStyle(.principal(cargando: flujo.ocupado))
                .accessibilityIdentifier("activarBiometria")
        }
    }
}
