import 'package:finso_empresarial/util/dinero.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('pesos() formatea centavos con comas y dos decimales', () {
    expect(pesos(0), r'$0.00');
    expect(pesos(5), r'$0.05');
    expect(pesos(125050), r'$1,250.50');
    expect(pesos(48215037), r'$482,150.37');
    expect(pesos(-150000), r'−$1,500.00');
    expect(pesos(150000, conSigno: true), r'+$1,500.00');
    expect(pesos(125050, sinCentavos: true), r'$1,250');
  });

  test(
    'centavosDe() lee lo que la persona teclea, sin decimales flotantes',
    () {
      expect(centavosDe('1,250.5'), 125050);
      expect(centavosDe(r'$80'), 8000);
      expect(centavosDe('0.1'), 10);
      expect(centavosDe('19.99'), 1999);
      expect(centavosDe('1.234'), isNull);
      expect(centavosDe('abc'), isNull);
      expect(centavosDe(''), isNull);
    },
  );

  test('FormatoImporte agrupa al teclear y toma la coma decimal', () {
    const f = FormatoImporte();
    TextEditingValue v(String t) => TextEditingValue(
      text: t,
      selection: TextSelection.collapsed(offset: t.length),
    );
    expect(f.formatEditUpdate(v('125'), v('1250')).text, '1,250');
    expect(f.formatEditUpdate(v('1,250'), v('1,250.5')).text, '1,250.5');
    // Teclado con coma decimal: "12,5" recién tecleado es 12.5.
    expect(f.formatEditUpdate(v('12'), v('12,5')).text, '12.5');
    // Tres decimales no se aceptan: se queda lo anterior.
    expect(f.formatEditUpdate(v('1.25'), v('1.255')).text, '1.25');
    expect(f.formatEditUpdate(v('1'), v('')).text, '');
  });
}
