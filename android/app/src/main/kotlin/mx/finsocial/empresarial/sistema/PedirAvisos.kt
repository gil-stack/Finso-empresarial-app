package mx.finsocial.empresarial.sistema

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import mx.finsocial.empresarial.Contenedor

/**
 * Pide el permiso de notificaciones (Android 13+) la primera vez que se ve el
 * resultado de un SPEI: es cuando se entiende para qué sirve. Una sola vez; si
 * dice que no, la app funciona igual.
 */
@Composable
fun PedirAvisos(contenedor: Contenedor) {
    if (Build.VERSION.SDK_INT < 33 || !contenedor.debePedirAvisos()) return
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        contenedor.yaPidioAvisos()
        lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
