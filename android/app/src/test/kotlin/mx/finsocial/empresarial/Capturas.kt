package mx.finsocial.empresarial

/**
 * Capturas de pantalla de las pruebas (Roborazzi). Quedan en
 * app/build/capturas y el CI las sube como artefacto para verlas sin
 * instalar nada.
 */
object Capturas {
    /** Un teléfono típico: 411×891 dp a 420 dpi (como un Pixel). `+` combina calificadores. */
    const val TELEFONO = "w411dp-h891dp-xxhdpi"

    fun ruta(nombre: String) = "build/capturas/$nombre.png"
}
