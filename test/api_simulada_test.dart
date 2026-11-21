import 'package:finso_empresarial/api/api_simulada.dart';
import 'package:finso_empresarial/api/errores.dart';
import 'package:finso_empresarial/api/finso_api.dart';
import 'package:finso_empresarial/api/modelos.dart';
import 'package:flutter_test/flutter_test.dart';

const telefono = DatosDelTelefono(nombre: 'Prueba', plataforma: 'ios');

Future<Ingreso> entrar(ApiSimulada api, String correo) async {
  final reto = await api.iniciar(
    correo: correo,
    contrasena: 'secreta',
    telefono: telefono,
  );
  expect(reto.pasos, [PasoEntrada.codigoSms]);
  final faltan = await api.confirmarCodigo(
    reto: reto.reto,
    paso: PasoEntrada.codigoSms,
    codigo: ApiSimulada.codigoCorrecto,
  );
  expect(faltan, isEmpty);
  return api.registrarDispositivo(
    reto: reto.reto,
    llavePublica: 'llave',
    telefono: telefono,
  );
}

void main() {
  late DateTime ahora;
  late ApiSimulada api;
  setUp(() {
    ahora = DateTime.utc(2026, 11, 21, 18);
    api = ApiSimulada(reloj: () => ahora, demora: Duration.zero);
  });

  test('primer ingreso: correo, SMS y registro del teléfono', () async {
    final ingreso = await entrar(api, 'ana@empresa.mx');
    expect(ingreso.dispositivo, startsWith('dev_'));
    expect(ingreso.usuario.empresaNombre, 'Distribuidora del Bajío');
    expect(ingreso.usuario.operacionDelDueno, isTrue);
  });

  test('código equivocado dice cuántos intentos quedan', () async {
    final reto = await api.iniciar(
      correo: 'ana@empresa.mx',
      contrasena: 'x',
      telefono: telefono,
    );
    await expectLater(
      api.confirmarCodigo(
        reto: reto.reto,
        paso: PasoEntrada.codigoSms,
        codigo: '000000',
      ),
      throwsA(
        isA<ErrorFinso>()
            .having((e) => e.codigo, 'codigo', 'CODIGO_INCORRECTO')
            .having((e) => e.intentosRestantes, 'intentos', 4),
      ),
    );
  });

  test('usuario sin celular primero lo captura', () async {
    final reto = await api.iniciar(
      correo: 'nuevo@empresa.mx',
      contrasena: 'x',
      telefono: telefono,
    );
    expect(reto.pasos, [PasoEntrada.capturarCelular, PasoEntrada.codigoSms]);
    final siguiente = await api.capturarCelular(
      reto: reto,
      celular: '55 1234 9876',
    );
    expect(siguiente.pasos, [PasoEntrada.codigoSms]);
    expect(siguiente.celularEnmascarado, endsWith('9876'));
  });

  test('sin sesión no se ve la cuenta', () async {
    await expectLater(
      api.cuenta(),
      throwsA(isA<ErrorFinso>().having((e) => e.codigo, 'c', 'SESION_VENCIDA')),
    );
  });

  test('cuenta, movimientos del mes y destinos', () async {
    await entrar(api, 'ana@empresa.mx');
    final lectura = await api.cuenta();
    expect(lectura.cuenta!.clabe, hasLength(18));
    final pagina = await api.movimientos(mes: '2026-11');
    expect(pagina.elementos, isNotEmpty);
    final fechas = pagina.elementos.map((m) => m.fecha).toList();
    expect(fechas, orderedEquals([...fechas]..sort((a, b) => b.compareTo(a))));
    final destinos = await api.destinos();
    expect(destinos.where((d) => !d.activo), hasLength(1));
  });

  test('no se paga a un destino en espera (regla de 30 min)', () async {
    await entrar(api, 'ana@empresa.mx');
    final enEspera = (await api.destinos()).firstWhere((d) => !d.activo);
    final solicitud = SolicitudSpei(
      destino: enEspera.id,
      importeCentavos: 10000,
      concepto: 'Prueba',
      referenciaNumerica: 1,
    );
    await expectLater(
      api.prepararSpei(solicitud),
      throwsA(
        isA<ErrorFinso>()
            .having((e) => e.codigo, 'c', 'DESTINO_EN_ESPERA')
            .having((e) => e.reintentarEn, 'hora', enEspera.activoDesde),
      ),
    );
    // Pasada la espera, ya se puede.
    ahora = enEspera.activoDesde.add(const Duration(seconds: 1));
    final destino = (await api.destinos()).firstWhere(
      (d) => d.id == enEspera.id,
    );
    expect(destino.activo, isTrue);
    expect((await api.prepararSpei(solicitud)).titular, enEspera.titular);
  });

  test('SPEI con operación del dueño sale sin liberador y la llave evita duplicados', () async {
    await entrar(api, 'ana@empresa.mx');
    final destino = (await api.destinos()).firstWhere((d) => d.activo);
    final solicitud = SolicitudSpei(
      destino: destino.id,
      importeCentavos: 250000,
      concepto: 'Pago factura 118',
      referenciaNumerica: 118,
    );
    final prep = await api.prepararSpei(solicitud);
    expect(prep.requiereLiberador, isFalse);
    final a = await api.mandarSpei(
      solicitud: solicitud,
      reto: prep.reto,
      firma: 'firma',
      llaveDeIdempotencia: 'llave-1',
    );
    final b = await api.mandarSpei(
      solicitud: solicitud,
      reto: prep.reto,
      firma: 'firma',
      llaveDeIdempotencia: 'llave-1',
    );
    expect(a.id, b.id);
    expect(a.estado, EstadoOrden.enProceso);
  });

  test('sin operación del dueño el SPEI queda por liberar', () async {
    await entrar(api, 'luis@empresa.mx');
    final destino = (await api.destinos()).firstWhere((d) => d.activo);
    final solicitud = SolicitudSpei(
      destino: destino.id,
      importeCentavos: 99_999_999_999,
      concepto: 'Mucho',
      referenciaNumerica: 1,
    );
    await expectLater(
      api.prepararSpei(solicitud),
      throwsA(
        isA<ErrorFinso>().having((e) => e.codigo, 'c', 'LIMITE_EXCEDIDO'),
      ),
    );
    final chica = SolicitudSpei(
      destino: destino.id,
      importeCentavos: 10000,
      concepto: 'Chica',
      referenciaNumerica: 2,
    );
    final prep = await api.prepararSpei(chica);
    expect(prep.requiereLiberador, isTrue);
    final orden = await api.mandarSpei(
      solicitud: chica,
      reto: prep.reto,
      firma: 'f',
      llaveDeIdempotencia: 'l',
    );
    expect(orden.estado, EstadoOrden.porLiberar);
    // Su propio SPEI no aparece para liberar; el de otra persona sí.
    final pendientes = await api.porLiberar();
    expect(pendientes.map((o) => o.id), isNot(contains(orden.id)));
    expect(pendientes, isNotEmpty);
  });
}
