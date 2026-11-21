/// Configuración de la app que cambia por ambiente. Se elige al compilar:
///
///   flutter run                                      → datos simulados
///   flutter run --dart-define=FINSO_API=real         → Finso Empresas real
///   flutter run --dart-define=FINSO_API=real --dart-define=FINSO_API_URL=https://…
abstract final class Entorno {
  /// `simulada` (por omisión, mientras el backend móvil no existe) o `real`.
  static const api = String.fromEnvironment(
    'FINSO_API',
    defaultValue: 'simulada',
  );

  static bool get usarSimulada => api != 'real';

  /// Base de la API móvil de Finso Empresas. Contrato:
  /// /mnt/project-files/app-empresarial/contrato-api-movil.md
  static const apiUrl = String.fromEnvironment(
    'FINSO_API_URL',
    defaultValue: 'https://empresas.finsocial.app/movil/v1',
  );

  /// Versión que se manda en `X-Finso-App`. La pone el build
  /// (`--dart-define=FINSO_VERSION=1.0.0+1`); en desarrollo dice `dev`.
  static const version = String.fromEnvironment(
    'FINSO_VERSION',
    defaultValue: 'dev',
  );
}
