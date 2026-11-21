import 'package:flutter/services.dart';

/// El dinero viaja y se guarda en **centavos enteros** (125050 = $1,250.50).
/// Nunca se usan decimales (double) para dinero: se redondean mal.

/// `125050` → `$1,250.50`. Con [conSigno], los abonos llevan `+` y los cargos `−`.
String pesos(int centavos, {bool conSigno = false, bool sinCentavos = false}) {
  final negativo = centavos < 0;
  final absoluto = centavos.abs();
  final enteros = (absoluto ~/ 100).toString().replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
    (m) => '${m[1]},',
  );
  final resto = (absoluto % 100).toString().padLeft(2, '0');
  final cuerpo = sinCentavos ? '\$$enteros' : '\$$enteros.$resto';
  if (negativo) return '−$cuerpo';
  return conSigno ? '+$cuerpo' : cuerpo;
}

/// Lo que la persona tecleó (`"1,250.5"`, `"$80"`) → centavos. `null` si no es
/// un importe válido.
int? centavosDe(String tecleado) {
  final limpio = tecleado.replaceAll(RegExp(r'[\$,\s]'), '');
  final m = RegExp(r'^(\d{1,12})(?:\.(\d{0,2}))?$').firstMatch(limpio);
  if (m == null) return null;
  final enteros = int.parse(m[1]!);
  final dec = (m[2] ?? '').padRight(2, '0');
  return enteros * 100 + int.parse(dec.isEmpty ? '0' : dec);
}

/// Agrupa con comas mientras se escribe (`1250.5` → `1,250.5`) y no deja
/// teclear más de dos decimales. Una coma seguida de 1 o 2 dígitos recién
/// tecleada (teclados con coma decimal) se toma como punto.
class FormatoImporte extends TextInputFormatter {
  const FormatoImporte();

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue anterior,
    TextEditingValue nuevo,
  ) {
    if (!nuevo.composing.isCollapsed) return nuevo;
    var texto = nuevo.text;
    if (!texto.contains('.') && texto.length > anterior.text.length) {
      final coma = RegExp(r',(\d{0,2})$').firstMatch(texto);
      final yaEstaba =
          anterior.text.contains(',') &&
          RegExp(r',\d{3}').hasMatch(anterior.text);
      if (coma != null && !yaEstaba) {
        texto = texto.replaceRange(coma.start, coma.start + 1, '.');
      }
    }
    final crudo = texto.replaceAll(RegExp(r'[\$,\s]'), '');
    if (crudo.isEmpty) return TextEditingValue.empty;
    if (!RegExp(r'^\d{0,12}(\.\d{0,2})?$').hasMatch(crudo)) return anterior;
    final partes = crudo.split('.');
    final enteros = partes.first.replaceFirst(RegExp(r'^0+(?=\d)'), '');
    final agrupado = enteros.replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+(?!\d))'),
      (m) => '${m[1]},',
    );
    final salida =
        '${agrupado.isEmpty ? '0' : agrupado}${partes.length > 1 ? '.${partes[1]}' : ''}';
    return TextEditingValue(
      text: salida,
      selection: TextSelection.collapsed(offset: salida.length),
    );
  }
}
