import 'dart:io' show Platform;

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api/api_http.dart';
import 'api/api_simulada.dart';
import 'api/finso_api.dart';
import 'app.dart';
import 'config/entorno.dart';
import 'ui/apariencia.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // De orilla a orilla: la app pinta detrás de las barras del sistema.
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  final preferencias = await SharedPreferences.getInstance();
  final FinsoApi api = Entorno.usarSimulada
      ? ApiSimulada()
      : ApiHttp(
          almacen: AlmacenEnMemoria(),
          plataforma: Platform.isIOS ? 'ios' : 'android',
        );
  runApp(FinsoEmpresarialApp(api: api, apariencia: Apariencia(preferencias)));
}
