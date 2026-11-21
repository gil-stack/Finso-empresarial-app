package mx.finsocial.empresarial

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** La aplicación: arma el [Contenedor] y avisa cuándo la app va y vuelve del segundo plano. */
class FinsoApp : Application() {
    private var armado: Contenedor? = null

    /** Se arma la primera vez que lo pide la pantalla (no en las pruebas, que arman el suyo). */
    val contenedor: Contenedor
        get() = armado ?: Contenedor.real(this).also { armado = it }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    armado?.sesion?.alIrAlFondo()
                }

                override fun onStart(owner: LifecycleOwner) {
                    armado?.sesion?.alVolver()
                }
            },
        )
    }
}
