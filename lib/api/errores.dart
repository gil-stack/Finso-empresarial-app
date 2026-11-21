/// Error que contesta la API (forma `{ codigo, mensaje?, ... }` del contrato) o
/// que arma la app cuando no hay red.
class ErrorFinso implements Exception {
  const ErrorFinso(
    this.codigo, {
    this.http,
    this.mensaje,
    this.reintentarEn,
    this.intentosRestantes,
    this.campos = const [],
  });

  final String codigo;
  final int? http;
  final String? mensaje;
  final DateTime? reintentarEn;
  final int? intentosRestantes;
  final List<String> campos;

  static const sinRed = 'SIN_RED';
  static const respuestaInvalida = 'RESPUESTA_INVALIDA';

  factory ErrorFinso.desdeJson(int http, Map<String, dynamic> j) => ErrorFinso(
    j['codigo'] as String? ?? 'ERROR_$http',
    http: http,
    mensaje: j['mensaje'] as String?,
    reintentarEn: j['reintentarEn'] == null
        ? null
        : DateTime.tryParse(j['reintentarEn'] as String)?.toUtc(),
    intentosRestantes: j['intentosRestantes'] as int?,
    campos: [for (final c in (j['campos'] as List?) ?? const []) '$c'],
  );

  /// La sesión ya no sirve y hay que volver a entrar.
  bool get cierraSesion =>
      codigo == 'SESION_REVOCADA' || codigo == 'DISPOSITIVO_REVOCADO';

  /// Texto para la persona: el del servidor si lo mandó, si no uno propio.
  String get paraPersona {
    if (mensaje != null && mensaje!.trim().isNotEmpty) return mensaje!;
    return switch (codigo) {
      sinRed => 'No hay conexión. Revisa tu internet e inténtalo otra vez.',
      'CREDENCIALES_INVALIDAS' => 'El correo o la contraseña no coinciden.',
      'USUARIO_BLOQUEADO' => 'Por seguridad tu acceso está pausado. Inténtalo más tarde o pide ayuda a tu administrador.',
      'EMPRESA_SUSPENDIDA' => 'La cuenta de tu empresa está suspendida.',
      'SIN_ACCESO_MOVIL' =>
        'Tu usuario no tiene acceso a la app. Pídeselo a tu administrador.',
      'DEMASIADOS_INTENTOS' => 'Demasiados intentos. Espera unos minutos.',
      'CODIGO_INCORRECTO' =>
        intentosRestantes == null
            ? 'El código no es correcto.'
            : 'El código no es correcto. Te quedan $intentosRestantes intentos.',
      'RETO_VENCIDO' => 'Pasó demasiado tiempo. Vuelve a empezar.',
      'ESPERA_PARA_REENVIAR' => 'Espera un momento antes de pedir otro código.',
      'CELULAR_INVALIDO' => 'Revisa el número: son 10 dígitos.',
      'CELULAR_EN_USO' => 'Ese celular ya lo usa otra persona.',
      'DEMASIADOS_DISPOSITIVOS' =>
        'Ya tienes el máximo de teléfonos. Da de baja uno en el portal.',
      'FIRMA_INVALIDA' =>
        'No pudimos confirmar tu identidad. Inténtalo otra vez.',
      'DISPOSITIVO_REVOCADO' =>
        'Este teléfono se dio de baja. Vuelve a registrarlo.',
      'SESION_REVOCADA' ||
      'SESION_VENCIDA' => 'Tu sesión terminó. Vuelve a entrar.',
      'DESTINO_EN_ESPERA' => 'Este destino es nuevo: por seguridad se le puede pagar 30 minutos después de registrarlo.',
      'DESTINO_NO_REGISTRADO' =>
        'Solo se puede pagar a cuentas destino registradas en el portal.',
      'SALDO_INSUFICIENTE' => 'No te alcanza el saldo disponible.',
      'LIMITE_EXCEDIDO' => 'El importe pasa uno de los límites de tu empresa.',
      'CUENTA_BLOQUEADA' =>
        'La cuenta está bloqueada y no puede mandar dinero.',
      'SIN_PERMISO' => 'Tu usuario no tiene permiso para esto.',
      'MISMA_PERSONA' => 'Quien pidió el SPEI no puede liberarlo.',
      'YA_LIBERADA' => 'Este SPEI ya fue liberado.',
      'LLAVE_EN_CONFLICTO' => 'Esta operación ya se mandó con otros datos.',
      'VERSION_NO_SOPORTADA' => 'Actualiza la app desde la tienda para seguir.',
      _ => 'Algo salió mal. Inténtalo otra vez.',
    };
  }

  @override
  String toString() => 'ErrorFinso($codigo, http: $http)';
}
