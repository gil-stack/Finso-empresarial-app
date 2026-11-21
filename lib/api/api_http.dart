import 'dart:async';
import 'dart:convert';
import 'dart:io' show SocketException;

import 'package:http/http.dart' as http;

import '../config/entorno.dart';
import 'errores.dart';
import 'finso_api.dart';
import 'modelos.dart';

/// Dónde se guardan los tokens de la sesión. En el teléfono va en el llavero
/// (Keychain / Keystore); en pruebas, en memoria.
abstract interface class AlmacenDeSesion {
  Future<Sesion?> leer();
  Future<void> guardar(Sesion sesion);
  Future<void> borrar();
  Future<String?> dispositivo();
}

class AlmacenEnMemoria implements AlmacenDeSesion {
  Sesion? _sesion;
  String? dispositivoGuardado;
  @override
  Future<Sesion?> leer() async => _sesion;
  @override
  Future<void> guardar(Sesion sesion) async => _sesion = sesion;
  @override
  Future<void> borrar() async => _sesion = null;
  @override
  Future<String?> dispositivo() async => dispositivoGuardado;
}

/// La API real de Finso Empresas por HTTPS.
class ApiHttp implements FinsoApi {
  ApiHttp({
    required this.almacen,
    required this.plataforma,
    http.Client? cliente,
    String? base,
    this.alCerrarSesion,
  }) : _cliente = cliente ?? http.Client(),
       _base = Uri.parse(base ?? Entorno.apiUrl);

  final AlmacenDeSesion almacen;

  /// `ios` o `android`.
  final String plataforma;

  /// Se llama cuando el servidor revoca la sesión o el teléfono.
  final void Function()? alCerrarSesion;
  final http.Client _cliente;
  final Uri _base;
  static const _espera = Duration(seconds: 20);

  /// Una sola renovación a la vez, aunque varias pantallas pidan datos juntas.
  Future<void>? _renovando;

  Uri _url(String ruta, [Map<String, String>? consulta]) => _base.replace(
    path: '${_base.path}$ruta',
    queryParameters: consulta == null || consulta.isEmpty ? null : consulta,
  );

  Future<Map<String, String>> _encabezados({
    required bool conSesion,
    String? idempotencia,
  }) async {
    final dispositivo = await almacen.dispositivo();
    final e = <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json; charset=utf-8',
      'X-Finso-App': 'empresarial/${Entorno.version}',
      'X-Finso-Plataforma': plataforma,
      'X-Finso-Dispositivo': ?dispositivo,
      'Idempotency-Key': ?idempotencia,
    };
    if (conSesion) {
      final s = await almacen.leer();
      if (s != null) e['Authorization'] = 'Bearer ${s.acceso}';
    }
    return e;
  }

  Future<dynamic> _pedir(
    String metodo,
    String ruta, {
    Map<String, dynamic>? cuerpo,
    Map<String, String>? consulta,
    bool conSesion = true,
    String? idempotencia,
    bool reintentado = false,
  }) async {
    if (conSesion && !reintentado) await _renovarSiVence();
    final pedido = http.Request(metodo, _url(ruta, consulta))
      ..headers.addAll(
        await _encabezados(conSesion: conSesion, idempotencia: idempotencia),
      );
    if (cuerpo != null) pedido.body = jsonEncode(cuerpo);
    final http.Response respuesta;
    try {
      respuesta = await http.Response.fromStream(
        await _cliente.send(pedido).timeout(_espera),
      );
    } on SocketException {
      throw const ErrorFinso(ErrorFinso.sinRed);
    } on TimeoutException {
      throw const ErrorFinso(ErrorFinso.sinRed);
    } on http.ClientException {
      throw const ErrorFinso(ErrorFinso.sinRed);
    }
    final codigo = respuesta.statusCode;
    final json = respuesta.body.isEmpty ? null : _decodificar(respuesta);
    if (codigo >= 200 && codigo < 300) return json;
    final error = ErrorFinso.desdeJson(
      codigo,
      json is Map<String, dynamic> ? json : const {},
    );
    if (codigo == 401 &&
        error.codigo == 'SESION_VENCIDA' &&
        conSesion &&
        !reintentado) {
      await _renovar();
      return _pedir(
        metodo,
        ruta,
        cuerpo: cuerpo,
        consulta: consulta,
        idempotencia: idempotencia,
        reintentado: true,
      );
    }
    if (error.cierraSesion) {
      await almacen.borrar();
      alCerrarSesion?.call();
    }
    throw error;
  }

  dynamic _decodificar(http.Response r) {
    try {
      return jsonDecode(utf8.decode(r.bodyBytes));
    } on FormatException {
      throw ErrorFinso(ErrorFinso.respuestaInvalida, http: r.statusCode);
    }
  }

  Future<void> _renovarSiVence() async {
    final s = await almacen.leer();
    if (s == null) return;
    final margen = DateTime.now().toUtc().add(const Duration(seconds: 30));
    if (s.accesoExpiraEn.isBefore(margen)) await _renovar();
  }

  Future<void> _renovar() => _renovando ??= () async {
    try {
      final s = await almacen.leer();
      if (s == null) throw const ErrorFinso('SESION_REVOCADA', http: 401);
      final j = await _pedir(
        'POST',
        '/sesion/renovar',
        cuerpo: {'renovacion': s.renovacion},
        conSesion: false,
      );
      await almacen.guardar(Sesion.desdeJson(j as Json));
    } finally {
      _renovando = null;
    }
  }();

  Json _obj(dynamic j) => j as Json;

  // ───── §1 Entrar ─────

  @override
  Future<RetoDeEntrada> iniciar({
    required String correo,
    required String contrasena,
    required DatosDelTelefono telefono,
  }) async => RetoDeEntrada.desdeJson(
    _obj(
      await _pedir(
        'POST',
        '/sesion/inicio',
        conSesion: false,
        cuerpo: {
          'correo': correo,
          'contrasena': contrasena,
          'dispositivo': telefono.aJson(),
        },
      ),
    ),
  );

  @override
  Future<RetoDeEntrada> capturarCelular({
    required RetoDeEntrada reto,
    required String celular,
  }) async {
    final j = _obj(
      await _pedir(
        'POST',
        '/sesion/celular',
        conSesion: false,
        cuerpo: {'reto': reto.reto, 'celular': celular},
      ),
    );
    return reto.conPasos(
      reto.pasos.where((p) => p != PasoEntrada.capturarCelular).toList(),
      celularEnmascarado: j['celularEnmascarado'] as String?,
      reenvioDisponibleEn: j['reenvioDisponibleEn'] == null
          ? null
          : DateTime.parse(j['reenvioDisponibleEn'] as String).toUtc(),
    );
  }

  @override
  Future<List<PasoEntrada>> confirmarCodigo({
    required String reto,
    required PasoEntrada paso,
    required String codigo,
  }) async {
    final j = _obj(
      await _pedir(
        'POST',
        '/sesion/codigo',
        conSesion: false,
        cuerpo: {'reto': reto, 'paso': paso.clave, 'codigo': codigo},
      ),
    );
    return [
      for (final p in j['pasosPendientes'] as List)
        PasoEntrada.desde(p as String),
    ];
  }

  @override
  Future<DateTime> reenviarCodigo({
    required String reto,
    required PasoEntrada paso,
  }) async {
    final j = _obj(
      await _pedir(
        'POST',
        '/sesion/codigo/reenviar',
        conSesion: false,
        cuerpo: {'reto': reto, 'paso': paso.clave},
      ),
    );
    return DateTime.parse(j['reenvioDisponibleEn'] as String).toUtc();
  }

  @override
  Future<Ingreso> registrarDispositivo({
    required String reto,
    required String llavePublica,
    required DatosDelTelefono telefono,
  }) async {
    final ingreso = Ingreso.desdeJson(
      _obj(
        await _pedir(
          'POST',
          '/dispositivos',
          conSesion: false,
          cuerpo: {
            'reto': reto,
            'llavePublica': llavePublica,
            'algoritmo': 'ES256',
            'nombre': telefono.nombre,
            'plataforma': telefono.plataforma,
          },
        ),
      ),
    );
    await almacen.guardar(ingreso.sesion);
    return ingreso;
  }

  @override
  Future<RetoDeFirma> retoParaEntrar(String dispositivo) async =>
      RetoDeFirma.desdeJson(
        _obj(
          await _pedir(
            'POST',
            '/sesion/biometria/reto',
            conSesion: false,
            cuerpo: {'dispositivo': dispositivo},
          ),
        ),
      );

  @override
  Future<Ingreso> entrarConBiometria({
    required String dispositivo,
    required String reto,
    required String firma,
  }) async {
    final ingreso = Ingreso.desdeJson(
      _obj(
        await _pedir(
          'POST',
          '/sesion/biometria',
          conSesion: false,
          cuerpo: {'dispositivo': dispositivo, 'reto': reto, 'firma': firma},
        ),
      ),
    );
    await almacen.guardar(ingreso.sesion);
    return ingreso;
  }

  @override
  Future<void> salir() async {
    try {
      await _pedir('POST', '/sesion/salir');
    } finally {
      await almacen.borrar();
    }
  }

  @override
  Future<Usuario> yo() async =>
      Usuario.desdeJson(_obj(await _pedir('GET', '/yo')));

  // ───── §2 Cuenta ─────

  @override
  Future<LecturaDeCuenta> cuenta() async =>
      LecturaDeCuenta.desdeJson(_obj(await _pedir('GET', '/cuenta')));

  @override
  Future<PaginaDeMovimientos> movimientos({
    required String mes,
    String? cursor,
  }) async => PaginaDeMovimientos.desdeJson(
    _obj(
      await _pedir(
        'GET',
        '/movimientos',
        consulta: {'mes': mes, 'cursor': ?cursor},
      ),
    ),
  );

  // ───── §3 Destinos ─────

  @override
  Future<List<Destino>> destinos() async {
    final j = _obj(await _pedir('GET', '/destinos'));
    return [
      for (final d in j['elementos'] as List) Destino.desdeJson(d as Json),
    ];
  }

  // ───── §4 SPEI ─────

  @override
  Future<PreparacionSpei> prepararSpei(SolicitudSpei solicitud) async =>
      PreparacionSpei.desdeJson(
        _obj(await _pedir('POST', '/spei/preparar', cuerpo: solicitud.aJson())),
      );

  @override
  Future<Orden> mandarSpei({
    required SolicitudSpei solicitud,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  }) async => Orden.desdeJson(
    _obj(
      await _pedir(
        'POST',
        '/spei',
        idempotencia: llaveDeIdempotencia,
        cuerpo: {...solicitud.aJson(), 'reto': reto, 'firma': firma},
      ),
    ),
  );

  @override
  Future<Orden> orden(String id) async => Orden.desdeJson(
    _obj(await _pedir('GET', '/spei/${Uri.encodeComponent(id)}')),
  );

  @override
  Future<List<Orden>> porLiberar() async {
    final j = _obj(
      await _pedir('GET', '/spei', consulta: {'estado': 'POR_LIBERAR'}),
    );
    return [for (final o in j['elementos'] as List) Orden.desdeJson(o as Json)];
  }

  @override
  Future<RetoDeFirma> prepararLiberacion(String orden) async =>
      RetoDeFirma.desdeJson(
        _obj(
          await _pedir(
            'POST',
            '/spei/${Uri.encodeComponent(orden)}/liberar/preparar',
          ),
        ),
      );

  @override
  Future<Orden> liberar({
    required String orden,
    required String reto,
    required String firma,
    required String llaveDeIdempotencia,
  }) async => Orden.desdeJson(
    _obj(
      await _pedir(
        'POST',
        '/spei/${Uri.encodeComponent(orden)}/liberar',
        idempotencia: llaveDeIdempotencia,
        cuerpo: {'reto': reto, 'firma': firma},
      ),
    ),
  );

  @override
  Future<Orden> rechazar({
    required String orden,
    required String motivo,
  }) async => Orden.desdeJson(
    _obj(
      await _pedir(
        'POST',
        '/spei/${Uri.encodeComponent(orden)}/rechazar',
        cuerpo: {'motivo': motivo},
      ),
    ),
  );
}
