import 'package:cupertino_ui/cupertino_ui.dart'
    show CupertinoPageTransitionsBuilder;
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

// Paleta Finso, tomada de la app finsocial-v2 (lib/ui/app_theme.dart): verde
// petróleo, menta y blanco verdoso. La acción principal va en menta #1DBB9D
// con letra tinta, en claro y en oscuro (regla de diseño de Finso).
const tinta = Color(0xFF102D31);
const menta = Color(0xFF1DBB9D);
const lienzo = Color(0xFFF2F7F2);

// Dos familias a propósito: Gully en títulos y botones, Wanted Sans en texto.
const titular = 'Gully';
const texto = 'WantedSans';

/// Radios de la plataforma Finso: superficies generosas.
abstract final class Radios {
  static const chico = 12.0;
  static const medio = 16.0;
  static const grande = 20.0;
  static const tarjeta = 28.0;
  static const pildora = 999.0;
}

/// Colores de la app según el modo (claro u oscuro). Se leen con
/// `context.colores`.
@immutable
class Colores extends ThemeExtension<Colores> {
  const Colores({
    required this.fondo,
    required this.tarjeta,
    required this.elevado,
    required this.texto,
    required this.secundario,
    required this.acento,
    required this.borde,
    required this.sombra,
    required this.accion,
    required this.sobreAccion,
    required this.saldoInicio,
    required this.saldoFin,
    required this.sobreSaldo,
    required this.sobreSaldoSecundario,
    required this.positivo,
    required this.positivoFondo,
    required this.aviso,
    required this.avisoFondo,
    required this.peligro,
    required this.peligroFondo,
  });

  final Color fondo,
      tarjeta,
      elevado,
      texto,
      secundario,
      acento,
      borde,
      sombra,
      accion,
      sobreAccion,
      saldoInicio,
      saldoFin,
      sobreSaldo,
      sobreSaldoSecundario,
      positivo,
      positivoFondo,
      aviso,
      avisoFondo,
      peligro,
      peligroFondo;

  static const claro = Colores(
    fondo: lienzo,
    tarjeta: Color(0xFFFFFFFF),
    elevado: Color(0xFFDCF1E9),
    texto: tinta,
    secundario: Color(0xFF4A6668),
    // El menta rellena; como letra sobre claro va oscurecido hasta contrastar.
    acento: Color(0xFF0A6F5D),
    borde: Color(0xFF63827D),
    sombra: Color(0x14102D31),
    accion: menta,
    sobreAccion: tinta,
    saldoInicio: Color(0xFF17494B),
    saldoFin: tinta,
    sobreSaldo: lienzo,
    sobreSaldoSecundario: Color(0xFFA9C4BF),
    positivo: Color(0xFF0A6F5D),
    positivoFondo: Color(0xFFDCF1E9),
    aviso: Color(0xFF8A5A00),
    avisoFondo: Color(0xFFFBF0D9),
    peligro: Color(0xFFB3261E),
    peligroFondo: Color(0xFFFBE6E4),
  );

  // Oscuro casi negro con tinte verde.
  static const oscuro = Colores(
    fondo: Color(0xFF04100F),
    tarjeta: Color(0xFF0C1C1D),
    elevado: Color(0xFF142628),
    texto: lienzo,
    secundario: Color(0xFFA9C4BF),
    // Sobre el fondo petróleo el menta ya se lee.
    acento: menta,
    borde: Color(0xFF5E8A86),
    sombra: Color(0x66061417),
    accion: menta,
    sobreAccion: tinta,
    saldoInicio: Color(0xFF162B2D),
    saldoFin: Color(0xFF0A1517),
    sobreSaldo: lienzo,
    sobreSaldoSecundario: Color(0xFFA9C4BF),
    positivo: menta,
    positivoFondo: Color(0xFF143A36),
    aviso: Color(0xFFF5B544),
    avisoFondo: Color(0xFF33291B),
    peligro: Color(0xFFFFB4AB),
    peligroFondo: Color(0xFF3A1A18),
  );

  @override
  Colores copyWith({Color? fondo, Color? tarjeta}) => Colores(
    fondo: fondo ?? this.fondo,
    tarjeta: tarjeta ?? this.tarjeta,
    elevado: elevado,
    texto: texto,
    secundario: secundario,
    acento: acento,
    borde: borde,
    sombra: sombra,
    accion: accion,
    sobreAccion: sobreAccion,
    saldoInicio: saldoInicio,
    saldoFin: saldoFin,
    sobreSaldo: sobreSaldo,
    sobreSaldoSecundario: sobreSaldoSecundario,
    positivo: positivo,
    positivoFondo: positivoFondo,
    aviso: aviso,
    avisoFondo: avisoFondo,
    peligro: peligro,
    peligroFondo: peligroFondo,
  );

  @override
  Colores lerp(covariant Colores? otro, double t) {
    if (otro == null) return this;
    Color m(Color a, Color b) => Color.lerp(a, b, t)!;
    return Colores(
      fondo: m(fondo, otro.fondo),
      tarjeta: m(tarjeta, otro.tarjeta),
      elevado: m(elevado, otro.elevado),
      texto: m(texto, otro.texto),
      secundario: m(secundario, otro.secundario),
      acento: m(acento, otro.acento),
      borde: m(borde, otro.borde),
      sombra: m(sombra, otro.sombra),
      accion: m(accion, otro.accion),
      sobreAccion: m(sobreAccion, otro.sobreAccion),
      saldoInicio: m(saldoInicio, otro.saldoInicio),
      saldoFin: m(saldoFin, otro.saldoFin),
      sobreSaldo: m(sobreSaldo, otro.sobreSaldo),
      sobreSaldoSecundario: m(sobreSaldoSecundario, otro.sobreSaldoSecundario),
      positivo: m(positivo, otro.positivo),
      positivoFondo: m(positivoFondo, otro.positivoFondo),
      aviso: m(aviso, otro.aviso),
      avisoFondo: m(avisoFondo, otro.avisoFondo),
      peligro: m(peligro, otro.peligro),
      peligroFondo: m(peligroFondo, otro.peligroFondo),
    );
  }

  /// Degradado del área del saldo: diagonal en claro, radial en oscuro.
  Gradient degradadoSaldo({required bool oscuro}) => oscuro
      ? RadialGradient(
          center: Alignment.topLeft,
          radius: 1.5,
          colors: [saldoInicio, saldoFin],
        )
      : LinearGradient(
          colors: [saldoInicio, saldoFin],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        );
}

extension TemaFinso on BuildContext {
  Colores get colores => Theme.of(this).extension<Colores>() ?? Colores.claro;
  bool get esOscuro => Theme.of(this).brightness == Brightness.dark;
}

/// Barras del sistema según el fondo que tienen debajo. La de navegación de
/// Android va transparente y sin el velo gris de contraste.
SystemUiOverlayStyle barrasDelSistema({
  required bool arribaOscuro,
  required bool abajoOscuro,
}) => SystemUiOverlayStyle(
  statusBarColor: const Color(0x00000000),
  statusBarIconBrightness: arribaOscuro ? Brightness.light : Brightness.dark,
  statusBarBrightness: arribaOscuro ? Brightness.dark : Brightness.light,
  systemNavigationBarColor: const Color(0x00000000),
  systemNavigationBarDividerColor: const Color(0x00000000),
  systemNavigationBarIconBrightness: abajoOscuro
      ? Brightness.light
      : Brightness.dark,
  systemNavigationBarContrastEnforced: false,
);

/// Tiempos y curvas de las animaciones: todas las pantallas usan estos para
/// que la app se sienta de una sola pieza.
abstract final class Animacion {
  static const rapido = Duration(milliseconds: 180);
  static const normal = Duration(milliseconds: 320);
  static const lento = Duration(milliseconds: 520);
  static const curva = Curves.easeOutCubic;
  static const curvaEntrada = Curves.easeInOutCubic;
}

ThemeData temaFinso(Brightness brillo) {
  final oscuro = brillo == Brightness.dark;
  final c = oscuro ? Colores.oscuro : Colores.claro;
  final esquema = ColorScheme.fromSeed(seedColor: menta, brightness: brillo)
      .copyWith(
        primary: c.acento,
        onPrimary: oscuro ? tinta : const Color(0xFFFFFFFF),
        primaryContainer: c.accion,
        onPrimaryContainer: c.sobreAccion,
        surface: c.tarjeta,
        onSurface: c.texto,
        onSurfaceVariant: c.secundario,
        surfaceContainerHighest: c.elevado,
        outline: c.borde,
        error: c.peligro,
      );
  final base = ThemeData(
    useMaterial3: true,
    brightness: brillo,
    colorScheme: esquema,
    fontFamily: texto,
  );
  final botonTexto = const TextStyle(
    fontFamily: titular,
    fontWeight: FontWeight.w600,
    fontSize: 16,
    letterSpacing: -0.2,
  );
  final forma = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(Radios.medio),
  );
  return base.copyWith(
    extensions: [c],
    scaffoldBackgroundColor: c.fondo,
    splashFactory: InkSparkle.splashFactory,
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: {
        TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
        TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
      },
    ),
    textTheme: base.textTheme.copyWith(
      // Escala de la plataforma Finso: 30, 25, 17, 15, 13.
      headlineLarge: TextStyle(
        fontFamily: titular,
        fontWeight: FontWeight.w700,
        fontSize: 30,
        height: 36 / 30,
        letterSpacing: -0.6,
        color: c.texto,
      ),
      headlineMedium: TextStyle(
        fontFamily: titular,
        fontWeight: FontWeight.w700,
        fontSize: 25,
        height: 30 / 25,
        letterSpacing: -0.4,
        color: c.texto,
      ),
      titleLarge: TextStyle(
        fontFamily: titular,
        fontWeight: FontWeight.w600,
        fontSize: 17,
        height: 23 / 17,
        letterSpacing: -0.2,
        color: c.texto,
      ),
      titleMedium: TextStyle(
        fontFamily: texto,
        fontWeight: FontWeight.w600,
        fontSize: 15,
        height: 22 / 15,
        color: c.texto,
      ),
      bodyLarge: TextStyle(
        fontFamily: texto,
        fontSize: 15,
        height: 22 / 15,
        color: c.texto,
      ),
      bodyMedium: TextStyle(
        fontFamily: texto,
        fontSize: 14,
        height: 1.45,
        color: c.texto,
      ),
      bodySmall: TextStyle(
        fontFamily: texto,
        fontSize: 13,
        height: 18 / 13,
        color: c.secundario,
      ),
    ),
    iconTheme: IconThemeData(color: c.texto),
    dividerColor: c.borde.withValues(alpha: .25),
    appBarTheme: AppBarTheme(
      backgroundColor: c.fondo,
      foregroundColor: c.texto,
      systemOverlayStyle: barrasDelSistema(
        arribaOscuro: oscuro,
        abajoOscuro: oscuro,
      ),
      scrolledUnderElevation: 0,
      centerTitle: false,
      toolbarHeight: 52,
      titleTextStyle: TextStyle(
        fontFamily: titular,
        fontWeight: FontWeight.w600,
        fontSize: 17,
        letterSpacing: -0.2,
        color: c.texto,
      ),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: oscuro ? c.elevado : c.tarjeta,
      surfaceTintColor: const Color(0x00000000),
      showDragHandle: true,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: oscuro ? c.elevado : c.tarjeta,
      surfaceTintColor: const Color(0x00000000),
    ),
    // La acción principal: menta con letra tinta, en los dos modos.
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: c.accion,
        foregroundColor: c.sobreAccion,
        // Deshabilitado: menta tenue y letra a media luz, legible en los dos modos.
        disabledBackgroundColor: c.accion.withValues(alpha: .2),
        disabledForegroundColor: c.texto.withValues(alpha: .5),
        minimumSize: const Size(double.infinity, 56),
        elevation: 0,
        textStyle: botonTexto,
        shape: forma,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: c.texto,
        minimumSize: const Size(double.infinity, 56),
        side: BorderSide(color: c.borde),
        textStyle: botonTexto,
        shape: forma,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: c.acento,
        minimumSize: const Size(48, 48),
        textStyle: const TextStyle(
          fontFamily: texto,
          fontWeight: FontWeight.w600,
          fontSize: 15,
        ),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: oscuro ? c.elevado : tinta,
      contentTextStyle: TextStyle(
        fontFamily: texto,
        fontSize: 14,
        color: oscuro ? c.texto : lienzo,
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Radios.medio),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: oscuro ? c.elevado : c.tarjeta,
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
      hintStyle: TextStyle(
        fontFamily: texto,
        fontSize: 15,
        color: c.secundario,
      ),
      labelStyle: TextStyle(
        fontFamily: texto,
        fontSize: 15,
        color: c.secundario,
      ),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radios.pildora),
        borderSide: BorderSide(color: c.borde),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radios.pildora),
        borderSide: BorderSide(color: c.borde.withValues(alpha: .6)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radios.pildora),
        borderSide: BorderSide(color: c.acento, width: 1.5),
      ),
    ),
  );
}
