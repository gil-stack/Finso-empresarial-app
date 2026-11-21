import 'dart:convert';

import 'package:finso_empresarial/api/api_http.dart';
import 'package:finso_empresarial/api/errores.dart';
import 'package:finso_empresarial/api/modelos.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

Sesion sesion(String acceso, {Duration vence = const Duration(minutes: 10)}) =>
    Sesion(
      acceso: acceso,
      accesoExpiraEn: DateTime.now().toUtc().add(vence),
      renovacion: 'ren-$acceso',
      renovacionExpiraEn: DateTime.now().toUtc().add(const Duration(hours: 12)),
    );

Json sesionJson(String acceso) => sesion(acceso).aJson();

const cuentaJson = {
  'cuenta': {
    'clabe': '646180535000004821',
    'titular': 'EMPRESA SA',
    'estado': 'VIGENTE',
    'saldoCentavos': 100,
    'disponibleCentavos': 90,
    'horaDelSaldo': '2026-11-21T01:00:00Z',
  },
  'fuente': 'core',
  'sinConexion': false,
};

void main() {
  test('manda los encabezados del contrato y el token', () async {
    late http.Request visto;
    final almacen = AlmacenEnMemoria()..dispositivoGuardado = 'dev_1';
    await almacen.guardar(sesion('a1'));
    final api = ApiHttp(
      almacen: almacen,
      plataforma: 'ios',
      base: 'https://ejemplo.test/movil/v1',
      cliente: MockClient((r) async {
        visto = r;
        return http.Response(jsonEncode(cuentaJson), 200);
      }),
    );
    final lectura = await api.cuenta();
    expect(lectura.cuenta!.disponibleCentavos, 90);
    expect(visto.url.toString(), 'https://ejemplo.test/movil/v1/cuenta');
    expect(visto.headers['Authorization'], 'Bearer a1');
    expect(visto.headers['X-Finso-Dispositivo'], 'dev_1');
    expect(visto.headers['X-Finso-Plataforma'], 'ios');
    expect(visto.headers['X-Finso-App'], startsWith('empresarial/'));
  });

  test('SESION_VENCIDA: renueva una vez y repite', () async {
    final almacen = AlmacenEnMemoria();
    await almacen.guardar(sesion('viejo'));
    final rutas = <String>[];
    final api = ApiHttp(
      almacen: almacen,
      plataforma: 'android',
      base: 'https://ejemplo.test/movil/v1',
      cliente: MockClient((r) async {
        rutas.add('${r.method} ${r.url.path}');
        if (r.url.path.endsWith('/sesion/renovar')) {
          expect(jsonDecode(r.body), {'renovacion': 'ren-viejo'});
          return http.Response(jsonEncode(sesionJson('nuevo')), 200);
        }
        if (r.headers['Authorization'] == 'Bearer viejo') {
          return http.Response(jsonEncode({'codigo': 'SESION_VENCIDA'}), 401);
        }
        return http.Response(jsonEncode(cuentaJson), 200);
      }),
    );
    await api.cuenta();
    expect(rutas, [
      'GET /movil/v1/cuenta',
      'POST /movil/v1/sesion/renovar',
      'GET /movil/v1/cuenta',
    ]);
    expect((await almacen.leer())!.acceso, 'nuevo');
  });

  test('SESION_REVOCADA borra la sesión y avisa', () async {
    final almacen = AlmacenEnMemoria();
    await almacen.guardar(sesion('a'));
    var avisado = false;
    final api = ApiHttp(
      almacen: almacen,
      plataforma: 'ios',
      base: 'https://ejemplo.test/movil/v1',
      alCerrarSesion: () => avisado = true,
      cliente: MockClient(
        (r) async =>
            http.Response(jsonEncode({'codigo': 'SESION_REVOCADA'}), 401),
      ),
    );
    await expectLater(api.yo(), throwsA(isA<ErrorFinso>()));
    expect(avisado, isTrue);
    expect(await almacen.leer(), isNull);
  });

  test('los errores traen el mensaje del servidor o uno propio', () async {
    final api = ApiHttp(
      almacen: AlmacenEnMemoria(),
      plataforma: 'ios',
      base: 'https://ejemplo.test/movil/v1',
      cliente: MockClient(
        (r) async => http.Response(
          jsonEncode({'codigo': 'CODIGO_INCORRECTO', 'intentosRestantes': 2}),
          400,
        ),
      ),
    );
    try {
      await api.confirmarCodigo(
        reto: 'rto_1',
        paso: PasoEntrada.codigoSms,
        codigo: '1',
      );
      fail('debió fallar');
    } on ErrorFinso catch (e) {
      expect(e.paraPersona, 'El código no es correcto. Te quedan 2 intentos.');
    }
  });

  test('SPEI lleva Idempotency-Key', () async {
    late http.Request visto;
    final almacen = AlmacenEnMemoria();
    await almacen.guardar(sesion('a'));
    final api = ApiHttp(
      almacen: almacen,
      plataforma: 'ios',
      base: 'https://ejemplo.test/movil/v1',
      cliente: MockClient((r) async {
        visto = r;
        return http.Response(
          jsonEncode({
            'id': 'spo_1',
            'estado': 'POR_LIBERAR',
            'destino': {
              'id': 'dst_1',
              'titular': 'X',
              'banco': null,
              'clabeFinal': '1234',
            },
            'importeCentavos': 100,
            'concepto': 'c',
            'referenciaNumerica': 1,
            'claveRastreo': null,
            'pedidaPor': 'Ana',
            'liberadaPor': null,
            'creadaEn': '2026-11-21T01:00:00Z',
            'actualizadaEn': '2026-11-21T01:00:00Z',
            'motivo': null,
          }),
          201,
        );
      }),
    );
    final orden = await api.mandarSpei(
      solicitud: const SolicitudSpei(
        destino: 'dst_1',
        importeCentavos: 100,
        concepto: 'c',
        referenciaNumerica: 1,
      ),
      reto: 'r',
      firma: 'f',
      llaveDeIdempotencia: 'k-1',
    );
    expect(orden.estado, EstadoOrden.porLiberar);
    expect(visto.headers['Idempotency-Key'], 'k-1');
    expect(jsonDecode(visto.body), containsPair('firma', 'f'));
  });
}
