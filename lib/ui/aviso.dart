import 'package:material_ui/material_ui.dart';

import 'tema.dart';

enum TipoAviso { error, espera, exito }

/// Píldora de aviso dentro de una pantalla (error, espera o éxito).
class Aviso extends StatelessWidget {
  const Aviso(this.mensaje, {super.key, this.tipo = TipoAviso.error});
  final String mensaje;
  final TipoAviso tipo;

  @override
  Widget build(BuildContext context) {
    final c = context.colores;
    final (color, fondo, icono) = switch (tipo) {
      TipoAviso.error => (
        c.peligro,
        c.peligroFondo,
        Icons.error_outline_rounded,
      ),
      TipoAviso.espera => (c.aviso, c.avisoFondo, Icons.schedule_rounded),
      TipoAviso.exito => (
        c.positivo,
        c.positivoFondo,
        Icons.check_circle_outline_rounded,
      ),
    };
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: fondo,
          borderRadius: BorderRadius.circular(Radios.chico),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 1),
              child: Icon(icono, size: 18, color: color),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                mensaje,
                style: TextStyle(fontFamily: texto, fontSize: 14, color: color),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
