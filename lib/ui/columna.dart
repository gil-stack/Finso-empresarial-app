import 'package:material_ui/material_ui.dart';

/// En una pantalla ancha (iPad, tableta) el contenido va en una columna
/// centrada del ancho de un teléfono grande. En el teléfono no cambia nada.
class Columna extends StatelessWidget {
  const Columna({super.key, required this.child, this.relleno = 24});
  final Widget child;
  final double relleno;
  static const anchoMaximo = 520.0;

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: anchoMaximo),
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: relleno),
        child: child,
      ),
    ),
  );
}
