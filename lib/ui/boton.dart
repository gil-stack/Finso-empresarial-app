import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import 'tema.dart';

/// Botón de la acción principal: menta con letra tinta. Mientras [cargando],
/// muestra un indicador y no se puede volver a tocar (evita dobles envíos).
class BotonPrincipal extends StatelessWidget {
  const BotonPrincipal({
    super.key,
    required this.texto,
    required this.alTocar,
    this.cargando = false,
    this.icono,
  });
  final String texto;
  final VoidCallback? alTocar;
  final bool cargando;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    final c = context.colores;
    final contenido = AnimatedSwitcher(
      duration: Animacion.rapido,
      child: cargando
          ? SizedBox(
              key: const ValueKey('cargando'),
              width: 22,
              height: 22,
              child: CircularProgressIndicator(
                strokeWidth: 2.4,
                color: c.sobreAccion,
              ),
            )
          : Row(
              key: const ValueKey('texto'),
              mainAxisSize: MainAxisSize.min,
              children: [
                if (icono != null) ...[
                  Icon(icono, size: 20),
                  const SizedBox(width: 10),
                ],
                Text(texto),
              ],
            ),
    );
    return Semantics(
      button: true,
      label: cargando ? '$texto, cargando' : null,
      child: FilledButton(
        onPressed: alTocar == null || cargando
            ? (cargando ? () {} : null)
            : () {
                HapticFeedback.lightImpact();
                alTocar!();
              },
        child: contenido,
      ),
    );
  }
}
