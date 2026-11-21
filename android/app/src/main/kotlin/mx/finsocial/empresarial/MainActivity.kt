package mx.finsocial.empresarial

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import mx.finsocial.empresarial.pantallas.Bienvenida

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Las capturas y grabaciones de pantalla salen en negro, y el selector
        // de apps no muestra saldos (especificación §2 y §10). Solo se apaga
        // al armar las capturas para la tienda.
        if (!BuildConfig.MODO_CAPTURAS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // El flujo de acceso llega en el siguiente PR; por ahora, la bienvenida.
            Bienvenida(alEntrar = {})
        }
    }
}
