import SwiftUI

// Piezas de las pantallas de acceso (siempre en oscuro, §3.1).

/// Campo con etiqueta, alto 56, radio 16. Con `secreto`, lleva el ojo.
struct CampoDeAcceso: View {
    let etiqueta: String
    @Binding var texto: String
    var secreto = false
    var teclado: UIKeyboardType = .default
    var contenido: UITextContentType?
    var identificador: String

    @State private var ver = false
    @FocusState private var enfocado: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(etiqueta)
                .font(Letra.etiqueta)
                .foregroundStyle(Finso.secundario)
            HStack(spacing: 8) {
                Group {
                    if secreto && !ver {
                        SecureField("", text: $texto)
                    } else {
                        TextField("", text: $texto)
                    }
                }
                .font(Letra.cuerpoGrande)
                .foregroundStyle(Finso.texto)
                .keyboardType(teclado)
                .textContentType(contenido)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($enfocado)
                .accessibilityLabel(etiqueta)
                .accessibilityIdentifier(identificador)
                if secreto {
                    Button {
                        ver.toggle()
                    } label: {
                        Image(systemName: ver ? "eye.slash" : "eye")
                            .foregroundStyle(Finso.secundario)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(ver ? "Ocultar contraseña" : "Mostrar contraseña")
                }
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 56)
            .background(Finso.elevado, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(enfocado ? Finso.menta : .clear, lineWidth: 1.5))
            .animation(Movimiento.rapido, value: enfocado)
            .contentShape(Rectangle())
            .onTapGesture { enfocado = true }
        }
    }
}

/// Aviso rojo bajo los campos.
struct AvisoDeError: View {
    let texto: String

    var body: some View {
        Label {
            Text(texto).fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "exclamationmark.circle.fill")
        }
        .font(Letra.cuerpo)
        .foregroundStyle(Finso.peligro)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Finso.peligroFondo, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .transition(.opacity.combined(with: .move(edge: .top)))
        .accessibilityIdentifier("avisoDeError")
        .onAppear { UIAccessibility.post(notification: .announcement, argument: texto) }
    }
}

/// «PASO 2 DE 4», título y explicación.
struct EncabezadoDePaso: View {
    let paso: String?
    let titulo: String
    var explicacion: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let paso {
                Text(paso)
                    .font(Letra.etiqueta)
                    .tracking(1.2)
                    .foregroundStyle(Finso.menta)
                    .accessibilityIdentifier("paso")
            }
            Text(titulo)
                .font(Letra.tituloGrande)
                .tracking(-0.6)
                .foregroundStyle(Finso.texto)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let explicacion {
                Text(explicacion)
                    .font(Letra.cuerpoGrande)
                    .foregroundStyle(Finso.secundario)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Sacude a los lados (código equivocado).
struct Sacudida: GeometryEffect {
    var veces: CGFloat
    var animatableData: CGFloat {
        get { veces }
        set { veces = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 9 * sin(veces * .pi * 4), y: 0))
    }
}

/// Columna de las pantallas de acceso: margen 20, máx. 520 de ancho, con
/// desplazamiento para teclados y letras grandes.
struct ColumnaDeAcceso<Contenido: View>: View {
    @ViewBuilder var contenido: Contenido

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) { contenido }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 24)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .scrollBounceBehavior(.basedOnSize)
    }
}
