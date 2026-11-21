package mx.finsocial.empresarial.seguridad

import android.content.Context
import android.os.Build
import android.provider.Settings
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono

/** Cómo se presenta el teléfono al registrarse (§1.1): «Pixel 9 de Ana» lo ve la persona en el portal. */
object Telefono {
    fun datos(contexto: Context): DatosDelTelefono {
        val nombre = runCatching { Settings.Global.getString(contexto.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        return DatosDelTelefono(
            nombre = nombre.take(60),
            plataforma = "android",
            modelo = Build.MODEL.take(60),
            versionSistema = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
        )
    }
}
