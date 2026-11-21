import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Modo de la app (Sistema, Claro u Oscuro). Se guarda en el teléfono.
class Apariencia extends ChangeNotifier {
  Apariencia(this._preferencias) {
    final guardado = _preferencias.getString(llave);
    modo = ThemeMode.values.firstWhere(
      (m) => m.name == guardado,
      orElse: () => ThemeMode.system,
    );
  }

  static const llave = 'empresarial.apariencia';
  final SharedPreferences _preferencias;
  ThemeMode modo = ThemeMode.system;

  Future<void> elegir(ThemeMode nuevo) async {
    if (nuevo == modo) return;
    modo = nuevo;
    notifyListeners();
    try {
      await _preferencias.setString(llave, nuevo.name);
    } catch (_) {
      // Si no se pudo guardar, el modo sigue igual mientras la app esté abierta.
    }
  }
}
