package mx.finsocial.empresarial

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import mx.finsocial.empresarial.pantallas.Raiz
import java.lang.ref.WeakReference

/** La única pantalla de la app; todo lo demás son pantallas de Compose. */
class MainActivity : FragmentActivity() {
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
        val contenedor = (application as FinsoApp).contenedor
        if (savedInstanceState == null) contenedor.abrirEnlace(intent?.dataString)
        setContent { Raiz(contenedor) }
    }

    /** Una liga con la app ya abierta (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (application as FinsoApp).contenedor.abrirEnlace(intent.dataString)
    }

    override fun onResume() {
        super.onResume()
        Contenedor.actividad = WeakReference(this)
    }

    override fun onDestroy() {
        if (Contenedor.actividad.get() === this) Contenedor.actividad = WeakReference(null)
        super.onDestroy()
    }
}
