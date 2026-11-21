import 'package:material_ui/material_ui.dart';

import 'tema.dart';

/// iOS expone «Reducir movimiento» aparte de `disableAnimations`: se leen las
/// dos para respetarlo en ambas plataformas.
bool prefiereMenosMovimiento(BuildContext context) =>
    MediaQuery.disableAnimationsOf(context) ||
    View.of(context).platformDispatcher.accessibilityFeatures.reduceMotion;

/// Entrada suave (aparece y sube 12 px). [retraso] en milisegundos sirve para
/// escalonar los elementos de una pantalla. Respeta «Reducir movimiento».
class Aparicion extends StatelessWidget {
  const Aparicion({super.key, required this.child, this.retraso = 0});
  final Widget child;
  final int retraso;

  @override
  Widget build(BuildContext context) {
    if (prefiereMenosMovimiento(context)) return child;
    final total = Animacion.normal.inMilliseconds + retraso;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: total),
      curve: Interval(retraso / total, 1, curve: Animacion.curva),
      builder: (context, v, child) => Opacity(
        opacity: v,
        child: Transform.translate(
          offset: Offset(0, 12 * (1 - v)),
          child: child,
        ),
      ),
      child: child,
    );
  }
}
