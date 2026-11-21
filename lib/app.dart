import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import 'api/finso_api.dart';
import 'pantallas/bienvenida.dart';
import 'ui/apariencia.dart';
import 'ui/tema.dart';

class FinsoEmpresarialApp extends StatelessWidget {
  const FinsoEmpresarialApp({
    super.key,
    required this.api,
    required this.apariencia,
  });

  final FinsoApi api;
  final Apariencia apariencia;

  @override
  Widget build(BuildContext context) => MultiProvider(
    providers: [
      Provider<FinsoApi>.value(value: api),
      ChangeNotifierProvider<Apariencia>.value(value: apariencia),
    ],
    child: Consumer<Apariencia>(
      builder: (context, apariencia, _) => MaterialApp(
        title: 'Finso Empresarial',
        debugShowCheckedModeBanner: false,
        theme: temaFinso(Brightness.light),
        darkTheme: temaFinso(Brightness.dark),
        themeMode: apariencia.modo,
        themeAnimationDuration: Animacion.normal,
        themeAnimationCurve: Animacion.curvaEntrada,
        locale: const Locale('es', 'MX'),
        supportedLocales: const [Locale('es', 'MX'), Locale('es')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        home: const Bienvenida(),
      ),
    ),
  );
}
