import 'package:finso_empresarial/util/fechas.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final utc = DateTime.utc(2026, 11, 21, 1, 30);

  test('las horas se muestran en hora de México (UTC−6)', () {
    expect(hora(utc), '19:30');
    expect(fechaCorta(utc), '20 nov 2026');
    expect(mesActual(ahora: utc), '2026-11');
    expect(mesActual(ahora: DateTime.utc(2026, 12, 1, 3)), '2026-11');
  });

  test('dia() dice Hoy, Ayer o la fecha', () {
    final ahora = DateTime.utc(2026, 11, 21, 18);
    expect(dia(DateTime.utc(2026, 11, 21, 15), ahora: ahora), 'Hoy');
    expect(dia(DateTime.utc(2026, 11, 20, 15), ahora: ahora), 'Ayer');
    expect(dia(DateTime.utc(2026, 11, 2, 15), ahora: ahora), '2 nov 2026');
  });

  test('faltan() en palabras', () {
    expect(faltan(const Duration(seconds: 20)), 'en unos segundos');
    expect(faltan(const Duration(minutes: 17, seconds: 30)), 'en 18 min');
    expect(faltan(const Duration(hours: 1, minutes: 5)), 'en 1 h 5 min');
    expect(nombreDelMes('2026-11'), 'noviembre 2026');
  });
}
