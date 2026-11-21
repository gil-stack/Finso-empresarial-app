import 'package:finso_empresarial/api/firma.dart';
import 'package:flutter_test/flutter_test.dart';

// Si estas pruebas cambian, el servidor tiene que cambiar igual (contrato §1.6, §4.2, §4.4).
void main() {
  test('texto que se firma al entrar', () {
    expect(
      TextoFirmado.entrar(dispositivo: 'dev_1', reto: 'abc'),
      'finso-movil:v1:entrar:dev_1:abc',
    );
  });

  test('texto que se firma al mandar un SPEI', () {
    expect(
      TextoFirmado.spei(
        dispositivo: 'dev_1',
        reto: 'r',
        destino: 'dst_9',
        importeCentavos: 150000,
        referenciaNumerica: 118,
        concepto: 'Pago factura 118',
      ),
      'finso-movil:v1:spei:dev_1:r:dst_9:150000:118:Pago factura 118',
    );
  });

  test('texto que se firma al liberar', () {
    expect(
      TextoFirmado.liberar(
        dispositivo: 'dev_1',
        reto: 'r',
        orden: 'spo_2',
        importeCentavos: 5,
      ),
      'finso-movil:v1:liberar:dev_1:r:spo_2:5',
    );
  });
}
