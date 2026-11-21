/// México (zona centro) está en UTC−6 todo el año desde 2022 (ya no hay
/// horario de verano). Las fechas llegan del servidor en UTC.
const _desfase = Duration(hours: -6);

DateTime enMexico(DateTime utc) => utc.toUtc().add(_desfase);

const _meses = [
  'ene',
  'feb',
  'mar',
  'abr',
  'may',
  'jun',
  'jul',
  'ago',
  'sep',
  'oct',
  'nov',
  'dic',
];
const _mesesLargos = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

String _dos(int n) => n.toString().padLeft(2, '0');

/// `14:32`
String hora(DateTime utc) {
  final m = enMexico(utc);
  return '${_dos(m.hour)}:${_dos(m.minute)}';
}

/// `21 nov 2026`
String fechaCorta(DateTime utc) {
  final m = enMexico(utc);
  return '${m.day} ${_meses[m.month - 1]} ${m.year}';
}

/// `21 nov 2026, 14:32`
String fechaYHora(DateTime utc) => '${fechaCorta(utc)}, ${hora(utc)}';

/// `noviembre 2026` a partir de `2026-11`.
String nombreDelMes(String mes) {
  final partes = mes.split('-');
  return '${_mesesLargos[int.parse(partes[1]) - 1]} ${partes[0]}';
}

/// `Hoy`, `Ayer` o `21 nov 2026`, para agrupar movimientos.
String dia(DateTime utc, {DateTime? ahora}) {
  final m = enMexico(utc);
  final hoy = enMexico(ahora ?? DateTime.now().toUtc());
  final d = DateTime.utc(m.year, m.month, m.day);
  final h = DateTime.utc(hoy.year, hoy.month, hoy.day);
  final diferencia = h.difference(d).inDays;
  if (diferencia == 0) return 'Hoy';
  if (diferencia == 1) return 'Ayer';
  return fechaCorta(utc);
}

/// `2026-11` del mes actual en México.
String mesActual({DateTime? ahora}) {
  final m = enMexico(ahora ?? DateTime.now().toUtc());
  return '${m.year}-${_dos(m.month)}';
}

/// Cuánto falta, en palabras: `en 12 min`, `en 1 h 5 min`, `en unos segundos`.
String faltan(Duration d) {
  if (d.inSeconds <= 59) return 'en unos segundos';
  final h = d.inHours;
  final min = d.inMinutes % 60 + (d.inSeconds % 60 > 0 ? 1 : 0);
  if (h == 0) return 'en $min min';
  return min == 0 ? 'en $h h' : 'en $h h $min min';
}
