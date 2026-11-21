import 'modelos.dart';

/// Datos del teléfono que se mandan al entrar por primera vez.
class DatosDelTelefono {
  const DatosDelTelefono({
    required this.nombre,
    required this.plataforma,
    this.modelo,
    this.versionSistema,
  });
  final String nombre;

  /// `ios` o `android`.
  final String plataforma;
  final String? modelo;
  final String? versionSistema;

  Map<String, dynamic> aJson() => {
    'nombre': nombre,
    'plataforma': plataforma,
    if (modelo != null) 'modelo': modelo,
    if (versionSistema != null) 'versionSistema': versionSistema,
  };
}

/// Todo lo que la app le pide a Finso Empresas. Hay dos implementaciones:
/// [ApiHttp] (la real) y [ApiSimulada] (datos de prueba mientras el backend
/// móvil no exista). Las pantallas solo conocen esta interfaz.
///
/// Contrato: /mnt/project-files/app-empresarial/contrato-api-movil.md
/// Los errores salen como [ErrorFinso].
abstract interface class FinsoApi {
  // §1 Entrar
  Future<RetoDeEntrada> iniciar({
    required String correo,
    required String contrasena,
    required DatosDelTelefono telefono,
  });
  Future<RetoDeEntrada> capturarCelular({
    required RetoDeEntrada reto,
    required String celular,
  });

  /// Devuelve los pasos que faltan (vacío = listo para registrar el teléfono).
  Future<List<PasoEntrada>> confirmarCodigo({
    required String reto,
    required PasoEntrada paso,
    required String codigo,
  });
  Future<DateTime> reenviarCodigo({
    required String reto,
    required PasoEntrada paso,
  });
  Future<Ingreso> registrarDispositivo({
    required String reto,
    required String llavePublica,
    required DatosDelTelefono telefono,
  });
  Future<RetoDeFirma> retoParaEntrar(String dispositivo);
  Future<Ingreso> entrarConBiometria({
    required String dispositivo,
    required String reto,
    required String firma,
  });
  Future<void> salir();
  Future<Usuario> yo();

  // §2 Cuenta
  Future<LecturaDeCuenta> cuenta();
  Future<PaginaDeMovimientos> movimientos({
    required String mes,
    String? cursor,
  });

  // §3 Destinos
  Future<List<Destino>> destinos();

  // §4 SPEI
  Future<PreparacionSpei> prepararSpei(SolicitudSpei solicitud);
  Future<Orden> mandarSpei({
    required SolicitudSpei solicitud,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  });
  Future<Orden> orden(String id);
  Future<List<Orden>> porLiberar();
  Future<RetoDeFirma> prepararLiberacion(String orden);
  Future<Orden> liberar({
    required String orden,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  });
  Future<Orden> rechazar({required String orden, required String motivo});
}
