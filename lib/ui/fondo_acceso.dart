import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import 'tema.dart';

/// Fondo del flujo de acceso, igual al de finsocial-v2 y la plataforma Finso:
/// degradado diagonal, halo bajo la marca y viñeta abajo para que el botón
/// menta quede sobre la zona más oscura. Siempre oscuro, en los dos modos.
class FondoAcceso extends StatelessWidget {
  const FondoAcceso({super.key, required this.child});
  final Widget child;

  /// La parada más oscura: si parpadea un cuadro, que parpadee hacia el negro.
  static const base = Color(0xFF061518);

  @override
  Widget build(BuildContext context) => AnnotatedRegion<SystemUiOverlayStyle>(
    value: barrasDelSistema(arribaOscuro: true, abajoOscuro: true),
    child: ColoredBox(
      color: base,
      child: Stack(
        fit: StackFit.expand,
        children: [
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                stops: [0, .38, 1],
                colors: [Color(0xFF123035), Color(0xFF0C2327), base],
              ),
            ),
          ),
          // Centrado al 28% de la altura: lo que se ilumina es la marca.
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: Alignment(0, -.44),
                radius: .8,
                stops: [0, .45, 1],
                colors: [
                  Color(0x6B2A6B73),
                  Color(0x3817474E),
                  Color(0x00061518),
                ],
              ),
            ),
          ),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment(0, -.28),
                end: Alignment.bottomCenter,
                stops: [0, .55, 1],
                colors: [
                  Color(0x0003090A),
                  Color(0x8C03090A),
                  Color(0xF003090A),
                ],
              ),
            ),
          ),
          Opacity(
            opacity: .03,
            child: Image.asset(
              'assets/marca/patron-finso.png',
              fit: BoxFit.cover,
              color: const Color(0xFF2CB397),
              colorBlendMode: BlendMode.srcIn,
            ),
          ),
          // El tema oscuro va dentro: campos y textos se leen sobre el fondo.
          Theme(data: temaFinso(Brightness.dark), child: child),
        ],
      ),
    ),
  );
}

/// Logotipo de Finso (blanco). Solo se tiñe si se pide otro color.
class LogoFinso extends StatelessWidget {
  const LogoFinso({super.key, this.ancho = 150, this.color});
  final double ancho;
  final Color? color;

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Finso',
    image: true,
    child: Image.asset(
      'assets/marca/finso-wordmark.png',
      width: ancho,
      height: ancho * 302 / 900,
      fit: BoxFit.contain,
      color: color,
      colorBlendMode: color == null ? null : BlendMode.srcIn,
    ),
  );
}
