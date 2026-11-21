import 'dart:async';
import 'dart:math';

import 'errores.dart';
import 'finso_api.dart';
import 'modelos.dart';

/// API con datos de prueba, para usar la app completa sin backend. Contesta
/// con el mismo JSON del contrato y lo pasa por los mismos `desdeJson` que la
/// API real, así que una pantalla que funciona aquí funciona allá.
///
/// Para probar casos:
///  * El código de SMS o correo correcto es **123456**.
///  * Un correo que diga `bloqueado` da `USUARIO_BLOQUEADO`; uno que diga
///    `nuevo` pide capturar el celular antes del SMS.
///  * Un importe mayor al disponible da `SALDO_INSUFICIENTE`.
///  * Como `ana@…` (operación del dueño) el SPEI sale sin liberador; con otro
///    correo queda «Por liberar».
class ApiSimulada implements FinsoApi {
  ApiSimulada({
    DateTime Function()? reloj,
    this.demora = const Duration(milliseconds: 650),
  }) : _reloj = reloj ?? (() => DateTime.now().toUtc()) {
    _sembrar();
  }

  final DateTime Function() _reloj;

  /// Cuánto tarda en contestar, para ver las animaciones de carga.
  final Duration demora;
  static const codigoCorrecto = '123456';

  DateTime get _ahora => _reloj();
  String _iso(DateTime d) => d.toUtc().toIso8601String();
  final _azar = Random(7);
  int _consecutivo = 0;
  String _id(String prefijo) =>
      '${prefijo}_SIM${(++_consecutivo).toString().padLeft(6, '0')}';

  Future<T> _contestar<T>(T Function() hacer) async {
    if (demora > Duration.zero) await Future<void>.delayed(demora);
    return hacer();
  }

  // ───── Estado simulado ─────

  late int _saldo;
  final _movimientos = <Json>[];
  final _destinos = <Json>[];
  final _ordenes = <String, Json>{};
  final _llaves = <String, Object>{};
  _Reto? _retoActual;
  String? _correo;
  bool _sesionAbierta = false;
  int _intentos = 0;

  void _sembrar() {
    final ahora = _ahora;
    _saldo = 48_215_037; // $482,150.37
    _destinos.addAll([
      _destino(
        'dst_SIM_PAPELERA',
        'PAPELERA DEL CENTRO SA DE CV',
        'PROVEEDOR',
        'BBVA MEXICO',
        '4821',
        activoDesde: ahora.subtract(const Duration(days: 40)),
      ),
      _destino(
        'dst_SIM_FLETES',
        'FLETES RAPIDOS DEL NORTE',
        'PROVEEDOR',
        'BANORTE',
        '0921',
        activoDesde: ahora.subtract(const Duration(days: 12)),
      ),
      _destino(
        'dst_SIM_LUCIA',
        'LUCIA HERNANDEZ RUIZ',
        'COLABORADOR',
        'SANTANDER',
        '7710',
        activoDesde: ahora.subtract(const Duration(days: 3)),
      ),
      // Nuevo: se registró hace 12 minutos y todavía está en espera.
      _destino(
        'dst_SIM_EMPAQUES',
        'EMPAQUES Y CARTON MX',
        'PROVEEDOR',
        'BANAMEX',
        '3305',
        activoDesde: ahora.add(const Duration(minutes: 18)),
        activo: false,
      ),
    ]);
    final contrapartes = [
      ('ABARROTES LA ESPERANZA', 'BBVA MEXICO', '1187', true),
      ('PAPELERA DEL CENTRO SA DE CV', 'BBVA MEXICO', '4821', false),
      ('COMERCIALIZADORA ALBA', 'BANORTE', '6620', true),
      ('FLETES RAPIDOS DEL NORTE', 'BANORTE', '0921', false),
      ('TIENDAS MONTERO', 'SANTANDER', '0045', true),
    ];
    var saldo = _saldo;
    for (var i = 0; i < 26; i++) {
      final (nombre, banco, clabe, entra) =
          contrapartes[i % contrapartes.length];
      final importe = 150_000 + _azar.nextInt(4_500_000);
      final fecha = ahora.subtract(
        Duration(hours: 5 + i * 19, minutes: _azar.nextInt(50)),
      );
      _movimientos.add({
        'id': _id('mov'),
        'sentido': entra ? 'ABONO' : 'CARGO',
        'tipo': entra ? 'SPEI_ENTRADA' : 'SPEI_SALIDA',
        'importeCentavos': importe,
        'saldoResultanteCentavos': saldo,
        'fecha': _iso(fecha),
        'concepto': entra
            ? 'Pago factura ${1200 - i}'
            : 'Pago a proveedor ${300 + i}',
        'claveRastreo': 'FINSO${fecha.millisecondsSinceEpoch ~/ 1000}',
        'contraparte': {'nombre': nombre, 'banco': banco, 'clabeFinal': clabe},
        'orden': null,
        'comprobante': !entra,
      });
      saldo += entra ? -importe : importe;
    }
    _ordenes['spo_SIM_PENDIENTE'] = {
      'id': 'spo_SIM_PENDIENTE',
      'estado': 'POR_LIBERAR',
      'destino': _resumenDestino(_destinos[1]),
      'importeCentavos': 1_845_000,
      'concepto': 'Fletes semana 47',
      'referenciaNumerica': 47,
      'claveRastreo': null,
      'pedidaPor': 'Luis Pérez',
      'liberadaPor': null,
      'creadaEn': _iso(ahora.subtract(const Duration(minutes: 40))),
      'actualizadaEn': _iso(ahora.subtract(const Duration(minutes: 40))),
      'motivo': null,
    };
  }

  Json _destino(
    String id,
    String titular,
    String tipo,
    String banco,
    String clabe, {
    required DateTime activoDesde,
    bool activo = true,
  }) => {
    'id': id,
    'titular': titular,
    'tipo': tipo,
    'banco': banco,
    'clabeFinal': clabe,
    'estado': activo ? 'ACTIVO' : 'EN_ESPERA',
    'activoDesde': _iso(activoDesde),
  };

  Json _resumenDestino(Json d) => {
    'id': d['id'],
    'titular': d['titular'],
    'banco': d['banco'],
    'clabeFinal': d['clabeFinal'],
  };

  Json _usuario() {
    final dueno = (_correo ?? '').startsWith('ana');
    return {
      'id': 'usr_SIM',
      'nombre': dueno ? 'Ana López Garza' : 'Luis Pérez Soto',
      'correo': _correo ?? 'ana@empresa.mx',
      'papeles': dueno
          ? ['ADMINISTRADOR', 'SOLICITANTE', 'LIBERADOR']
          : ['SOLICITANTE', 'LIBERADOR'],
      'empresa': {'id': 'emp_SIM', 'nombre': 'Distribuidora del Bajío'},
      'operacionDelDueno': dueno,
    };
  }

  Json _sesion() => {
    'acceso': 'acc_${_azar.nextInt(1 << 32)}',
    'accesoExpiraEn': _iso(_ahora.add(const Duration(minutes: 10))),
    'renovacion': 'ren_${_azar.nextInt(1 << 32)}',
    'renovacionExpiraEn': _iso(_ahora.add(const Duration(hours: 12))),
  };

  void _exigirSesion() {
    if (!_sesionAbierta) throw const ErrorFinso('SESION_VENCIDA', http: 401);
  }

  // ───── §1 Entrar ─────

  @override
  Future<RetoDeEntrada> iniciar({
    required String correo,
    required String contrasena,
    required DatosDelTelefono telefono,
  }) => _contestar(() {
    final c = correo.trim().toLowerCase();
    if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(c) ||
        contrasena.isEmpty) {
      throw const ErrorFinso('CREDENCIALES_INVALIDAS', http: 401);
    }
    if (c.contains('bloqueado')) {
      throw ErrorFinso(
        'USUARIO_BLOQUEADO',
        http: 423,
        reintentarEn: _ahora.add(const Duration(minutes: 15)),
      );
    }
    _correo = c;
    _intentos = 0;
    final pasos = c.contains('nuevo')
        ? ['CAPTURAR_CELULAR', 'CODIGO_SMS']
        : ['CODIGO_SMS'];
    final reto = RetoDeEntrada.desdeJson({
      'reto': _id('rto'),
      'expiraEn': _iso(_ahora.add(const Duration(minutes: 10))),
      'pasos': pasos,
      'celularEnmascarado': c.contains('nuevo') ? null : '•••• ••• 4821',
      'correoEnmascarado': '${c[0]}••@${c.split('@').last}',
      'reenvioDisponibleEn': _iso(_ahora.add(const Duration(seconds: 30))),
    });
    _retoActual = _Reto(reto.reto, reto.pasos.toList());
    return reto;
  });

  _Reto _reto(String id) {
    final r = _retoActual;
    if (r == null || r.id != id) {
      throw const ErrorFinso('RETO_VENCIDO', http: 410);
    }
    return r;
  }

  @override
  Future<RetoDeEntrada> capturarCelular({
    required RetoDeEntrada reto,
    required String celular,
  }) => _contestar(() {
    final r = _reto(reto.reto);
    final digitos = celular.replaceAll(RegExp(r'\D'), '');
    if (digitos.length < 10) {
      throw const ErrorFinso('CELULAR_INVALIDO', http: 400);
    }
    r.pendientes.remove(PasoEntrada.capturarCelular);
    return reto.conPasos(
      r.pendientes.toList(),
      celularEnmascarado: '•••• ••• ${digitos.substring(digitos.length - 4)}',
      reenvioDisponibleEn: _ahora.add(const Duration(seconds: 30)),
    );
  });

  @override
  Future<List<PasoEntrada>> confirmarCodigo({
    required String reto,
    required PasoEntrada paso,
    required String codigo,
  }) => _contestar(() {
    final r = _reto(reto);
    if (codigo != codigoCorrecto) {
      _intentos++;
      if (_intentos >= 5) {
        _retoActual = null;
        throw const ErrorFinso('DEMASIADOS_INTENTOS', http: 429);
      }
      throw ErrorFinso(
        'CODIGO_INCORRECTO',
        http: 400,
        intentosRestantes: 5 - _intentos,
      );
    }
    r.pendientes.remove(paso);
    return r.pendientes.toList();
  });

  @override
  Future<DateTime> reenviarCodigo({
    required String reto,
    required PasoEntrada paso,
  }) => _contestar(() {
    _reto(reto);
    return _ahora.add(const Duration(seconds: 30));
  });

  @override
  Future<Ingreso> registrarDispositivo({
    required String reto,
    required String llavePublica,
    required DatosDelTelefono telefono,
  }) => _contestar(() {
    final r = _reto(reto);
    if (r.pendientes.isNotEmpty) {
      throw const ErrorFinso('RETO_INCOMPLETO', http: 409);
    }
    final dispositivo = _id('dev');
    _llaves[dispositivo] = llavePublica;
    _retoActual = null;
    _sesionAbierta = true;
    return Ingreso.desdeJson({
      'dispositivo': dispositivo,
      'sesion': _sesion(),
      'usuario': _usuario(),
    });
  });

  @override
  Future<RetoDeFirma> retoParaEntrar(String dispositivo) => _contestar(() {
    if (!_llaves.containsKey(dispositivo) && _llaves.isNotEmpty) {
      throw const ErrorFinso('DISPOSITIVO_REVOCADO', http: 401);
    }
    return RetoDeFirma.desdeJson({
      'reto': 'r${_azar.nextInt(1 << 32).toRadixString(36)}',
      'expiraEn': _iso(_ahora.add(const Duration(seconds: 60))),
    });
  });

  @override
  Future<Ingreso> entrarConBiometria({
    required String dispositivo,
    required String reto,
    required String firma,
  }) => _contestar(() {
    // La simulada no verifica criptografía: solo que haya firma.
    if (firma.isEmpty) throw const ErrorFinso('FIRMA_INVALIDA', http: 401);
    _sesionAbierta = true;
    return Ingreso.desdeJson({'sesion': _sesion(), 'usuario': _usuario()});
  });

  @override
  Future<void> salir() => _contestar(() => _sesionAbierta = false);

  @override
  Future<Usuario> yo() => _contestar(() {
    _exigirSesion();
    return Usuario.desdeJson(_usuario());
  });

  // ───── §2 Cuenta ─────

  @override
  Future<LecturaDeCuenta> cuenta() => _contestar(() {
    _exigirSesion();
    final retenido = _ordenes.values
        .where(
          (o) => o['estado'] == 'POR_LIBERAR' || o['estado'] == 'EN_PROCESO',
        )
        .fold<int>(0, (s, o) => s + (o['importeCentavos'] as int));
    return LecturaDeCuenta.desdeJson({
      'cuenta': {
        'clabe': '646180535000004821',
        'titular': 'DISTRIBUIDORA DEL BAJIO SA DE CV',
        'estado': 'VIGENTE',
        'saldoCentavos': _saldo,
        'disponibleCentavos': _saldo - retenido,
        'horaDelSaldo': _iso(_ahora),
      },
      'fuente': 'core',
      'sinConexion': false,
    });
  });

  @override
  Future<PaginaDeMovimientos> movimientos({
    required String mes,
    String? cursor,
  }) => _contestar(() {
    _exigirSesion();
    final delMes =
        _movimientos.where((m) {
          final f = DateTime.parse(m['fecha'] as String)
              .subtract(const Duration(hours: 6));
          return '${f.year}-${f.month.toString().padLeft(2, '0')}' == mes;
        }).toList()..sort(
          (a, b) => (b['fecha'] as String).compareTo(a['fecha'] as String),
        );
    return PaginaDeMovimientos.desdeJson({
      'mes': mes,
      'elementos': delMes,
      'siguienteCursor': null,
      'incompleto': false,
      'sinConexion': false,
    });
  });

  // ───── §3 Destinos ─────

  @override
  Future<List<Destino>> destinos() => _contestar(() {
    _exigirSesion();
    for (final d in _destinos) {
      if (d['estado'] == 'EN_ESPERA' &&
          !_ahora.isBefore(DateTime.parse(d['activoDesde'] as String))) {
        d['estado'] = 'ACTIVO';
      }
    }
    return [for (final d in _destinos) Destino.desdeJson(d)];
  });

  // ───── §4 SPEI ─────

  Json _validar(SolicitudSpei s) {
    _exigirSesion();
    final d = _destinos.where((d) => d['id'] == s.destino).firstOrNull;
    if (d == null) throw const ErrorFinso('DESTINO_NO_REGISTRADO', http: 422);
    final activoDesde = DateTime.parse(d['activoDesde'] as String);
    if (_ahora.isBefore(activoDesde)) {
      throw ErrorFinso(
        'DESTINO_EN_ESPERA',
        http: 422,
        reintentarEn: activoDesde,
      );
    }
    if (s.importeCentavos <= 0) {
      throw const ErrorFinso(
        'PETICION_INVALIDA',
        http: 400,
        campos: ['importeCentavos'],
      );
    }
    if (!SolicitudSpei.patronConcepto.hasMatch(s.concepto)) {
      throw const ErrorFinso(
        'PETICION_INVALIDA',
        http: 400,
        campos: ['concepto'],
        mensaje: 'El concepto solo admite letras sin acentos, números, espacios y . , & -',
      );
    }
    if (s.importeCentavos > 89_000_000) {
      throw const ErrorFinso(
        'LIMITE_EXCEDIDO',
        http: 422,
        mensaje: 'El tope por SPEI es de \$890,000.00.',
      );
    }
    return d;
  }

  @override
  Future<PreparacionSpei> prepararSpei(SolicitudSpei solicitud) =>
      _contestar(() {
        final d = _validar(solicitud);
        return PreparacionSpei.desdeJson({
          'reto': 's${_azar.nextInt(1 << 32).toRadixString(36)}',
          'expiraEn': _iso(_ahora.add(const Duration(seconds: 120))),
          'resumen': {
            'destino': _resumenDestino(d),
            'importeCentavos': solicitud.importeCentavos,
            'comisionCentavos': 0,
            'concepto': solicitud.concepto,
            'referenciaNumerica': solicitud.referenciaNumerica,
          },
          'requiereLiberador': !(_usuario()['operacionDelDueno'] as bool),
        });
      });

  final _porLlave = <String, String>{};

  @override
  Future<Orden> mandarSpei({
    required SolicitudSpei solicitud,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  }) => _contestar(() {
    final repetida = _porLlave[llaveDeIdempotencia];
    if (repetida != null) return Orden.desdeJson(_ordenes[repetida]!);
    final d = _validar(solicitud);
    if (firma.isEmpty) throw const ErrorFinso('FIRMA_INVALIDA', http: 401);
    final disponible = (cuentaSinDemora()['disponibleCentavos'] as int);
    if (solicitud.importeCentavos > disponible) {
      throw const ErrorFinso('SALDO_INSUFICIENTE', http: 422);
    }
    final dueno = _usuario()['operacionDelDueno'] as bool;
    final id = _id('spo');
    final orden = {
      'id': id,
      'estado': dueno ? 'EN_PROCESO' : 'POR_LIBERAR',
      'destino': _resumenDestino(d),
      'importeCentavos': solicitud.importeCentavos,
      'concepto': solicitud.concepto,
      'referenciaNumerica': solicitud.referenciaNumerica,
      'claveRastreo': null,
      'pedidaPor': _usuario()['nombre'],
      'liberadaPor': dueno ? _usuario()['nombre'] : null,
      'creadaEn': _iso(_ahora),
      'actualizadaEn': _iso(_ahora),
      'motivo': null,
    };
    _ordenes[id] = orden;
    _porLlave[llaveDeIdempotencia] = id;
    if (dueno) _programarLiquidacion(id);
    return Orden.desdeJson(orden);
  });

  Json cuentaSinDemora() {
    final retenido = _ordenes.values
        .where(
          (o) => o['estado'] == 'POR_LIBERAR' || o['estado'] == 'EN_PROCESO',
        )
        .fold<int>(0, (s, o) => s + (o['importeCentavos'] as int));
    return {'disponibleCentavos': _saldo - retenido};
  }

  /// En la simulada el SPEI se liquida 2.5 s después de salir.
  void _programarLiquidacion(String id) {
    Timer(const Duration(milliseconds: 2500), () {
      final o = _ordenes[id];
      if (o == null || o['estado'] != 'EN_PROCESO') return;
      o['estado'] = 'LIQUIDADA';
      o['claveRastreo'] = 'FINSO${_ahora.millisecondsSinceEpoch ~/ 1000}';
      o['actualizadaEn'] = _iso(_ahora);
      _saldo -= o['importeCentavos'] as int;
      final d = o['destino'] as Json;
      _movimientos.add({
        'id': _id('mov'),
        'sentido': 'CARGO',
        'tipo': 'SPEI_SALIDA',
        'importeCentavos': o['importeCentavos'],
        'saldoResultanteCentavos': _saldo,
        'fecha': _iso(_ahora),
        'concepto': o['concepto'],
        'claveRastreo': o['claveRastreo'],
        'contraparte': {
          'nombre': d['titular'],
          'banco': d['banco'],
          'clabeFinal': d['clabeFinal'],
        },
        'orden': id,
        'comprobante': true,
      });
    });
  }

  Json _ordenOError(String id) {
    final o = _ordenes[id];
    if (o == null) throw const ErrorFinso('NO_ENCONTRADA', http: 404);
    return o;
  }

  @override
  Future<Orden> orden(String id) => _contestar(() {
    _exigirSesion();
    return Orden.desdeJson(_ordenOError(id));
  });

  @override
  Future<List<Orden>> porLiberar() => _contestar(() {
    _exigirSesion();
    final mio = _usuario()['nombre'];
    final dueno = _usuario()['operacionDelDueno'] as bool;
    return [
      for (final o in _ordenes.values)
        if (o['estado'] == 'POR_LIBERAR' && (dueno || o['pedidaPor'] != mio))
          Orden.desdeJson(o),
    ];
  });

  @override
  Future<RetoDeFirma> prepararLiberacion(String orden) => _contestar(() {
    _exigirSesion();
    final o = _ordenOError(orden);
    if (o['estado'] != 'POR_LIBERAR') {
      throw const ErrorFinso('YA_LIBERADA', http: 409);
    }
    final dueno = _usuario()['operacionDelDueno'] as bool;
    if (!dueno && o['pedidaPor'] == _usuario()['nombre']) {
      throw const ErrorFinso('MISMA_PERSONA', http: 409);
    }
    return RetoDeFirma.desdeJson({
      'reto': 'l${_azar.nextInt(1 << 32).toRadixString(36)}',
      'expiraEn': _iso(_ahora.add(const Duration(seconds: 120))),
    });
  });

  @override
  Future<Orden> liberar({
    required String orden,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  }) => _contestar(() {
    _exigirSesion();
    final o = _ordenOError(orden);
    if (o['estado'] != 'POR_LIBERAR') return Orden.desdeJson(o);
    if (firma.isEmpty) throw const ErrorFinso('FIRMA_INVALIDA', http: 401);
    o['estado'] = 'EN_PROCESO';
    o['liberadaPor'] = _usuario()['nombre'];
    o['actualizadaEn'] = _iso(_ahora);
    _programarLiquidacion(orden);
    return Orden.desdeJson(o);
  });

  @override
  Future<Orden> rechazar({required String orden, required String motivo}) =>
      _contestar(() {
        _exigirSesion();
        final o = _ordenOError(orden);
        o['estado'] = 'CANCELADA';
        o['motivo'] = motivo;
        o['actualizadaEn'] = _iso(_ahora);
        return Orden.desdeJson(o);
      });
}

class _Reto {
  _Reto(this.id, this.pendientes);
  final String id;
  final List<PasoEntrada> pendientes;
}
