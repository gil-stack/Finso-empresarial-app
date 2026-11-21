import 'package:finso_empresarial/api/api_simulada.dart';
import 'package:finso_empresarial/app.dart';
import 'package:finso_empresarial/ui/apariencia.dart';
import 'package:finso_empresarial/ui/tema.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  for (final modo in [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('la bienvenida se arma en modo ${modo.name}', (tester) async {
      SharedPreferences.setMockInitialValues({Apariencia.llave: modo.name});
      final apariencia = Apariencia(await SharedPreferences.getInstance());
      await tester.pumpWidget(
        FinsoEmpresarialApp(
          api: ApiSimulada(demora: Duration.zero),
          apariencia: apariencia,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Entrar'), findsOneWidget);
      expect(find.textContaining('La banca de tu empresa'), findsOneWidget);
    });
  }

  test('la acción principal es menta con letra tinta en claro y oscuro', () {
    for (final brillo in Brightness.values) {
      final tema = temaFinso(brillo);
      final estilo = tema.filledButtonTheme.style!;
      expect(estilo.backgroundColor!.resolve({}), menta);
      expect(estilo.foregroundColor!.resolve({}), tinta);
    }
  });

  test('Apariencia guarda el modo elegido', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final a = Apariencia(prefs);
    expect(a.modo, ThemeMode.system);
    await a.elegir(ThemeMode.dark);
    expect(Apariencia(prefs).modo, ThemeMode.dark);
  });
}
