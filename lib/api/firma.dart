/// Textos que firma la llave del dispositivo (Face ID / huella). El servidor
/// arma el mismo texto y verifica la firma con la llave pública registrada.
/// Formato fijo del contrato (§1.6, §4.2, §4.4): no cambiar sin cambiarlo allá.
abstract final class TextoFirmado {
  static String entrar({required String dispositivo, required String reto}) =>
      'finso-movil:v1:entrar:$dispositivo:$reto';

  static String spei({
    required String dispositivo,
    required String reto,
    required String destino,
    required int importeCentavos,
    required int referenciaNumerica,
    required String concepto,
  }) =>
      'finso-movil:v1:spei:$dispositivo:$reto:$destino:$importeCentavos:'
      '$referenciaNumerica:$concepto';

  static String liberar({
    required String dispositivo,
    required String reto,
    required String orden,
    required int importeCentavos,
  }) => 'finso-movil:v1:liberar:$dispositivo:$reto:$orden:$importeCentavos';
}
