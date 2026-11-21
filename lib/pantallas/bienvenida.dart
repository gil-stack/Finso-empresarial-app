import 'package:material_ui/material_ui.dart';

import '../config/entorno.dart';
import '../ui/aparicion.dart';
import '../ui/boton.dart';
import '../ui/columna.dart';
import '../ui/fondo_acceso.dart';
import '../ui/tema.dart';

/// Primera pantalla: la marca y la entrada. Siempre sobre el fondo oscuro de
/// acceso, como finsocial-v2 y la plataforma Finso.
class Bienvenida extends StatelessWidget {
  const Bienvenida({super.key, this.alEntrar});

  /// Qué pasa al tocar «Entrar». El flujo de acceso llega en el siguiente PR.
  final VoidCallback? alEntrar;

  @override
  Widget build(BuildContext context) {
    // La bienvenida siempre va en oscuro, aunque la app esté en claro.
    final tema = temaFinso(Brightness.dark).textTheme;
    return Scaffold(
      backgroundColor: FondoAcceso.base,
      body: FondoAcceso(
        child: SafeArea(
          child: Columna(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Spacer(flex: 3),
                const Aparicion(child: Center(child: LogoFinso(ancho: 168))),
                const SizedBox(height: 14),
                Aparicion(
                  retraso: 80,
                  child: Text(
                    'EMPRESARIAL',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontFamily: texto,
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                      letterSpacing: 4.2,
                      color: menta.withValues(alpha: .9),
                    ),
                  ),
                ),
                const Spacer(flex: 2),
                Aparicion(
                  retraso: 160,
                  child: Text(
                    'La banca de tu empresa,\nen tu mano.',
                    textAlign: TextAlign.center,
                    style: tema.headlineMedium,
                  ),
                ),
                const SizedBox(height: 12),
                Aparicion(
                  retraso: 220,
                  child: Text(
                    'Consulta tu saldo, revisa movimientos y manda SPEI a tus cuentas registradas.',
                    textAlign: TextAlign.center,
                    style: tema.bodyLarge?.copyWith(
                      color: Colores.oscuro.secundario,
                    ),
                  ),
                ),
                const Spacer(flex: 3),
                Aparicion(
                  retraso: 300,
                  child: BotonPrincipal(texto: 'Entrar', alTocar: alEntrar),
                ),
                const SizedBox(height: 12),
                if (Entorno.usarSimulada)
                  Text(
                    'Modo de prueba: datos simulados',
                    textAlign: TextAlign.center,
                    style: tema.bodySmall,
                  ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
