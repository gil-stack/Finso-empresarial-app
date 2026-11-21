package mx.finsocial.empresarial.nucleo

/**
 * Los bancos mandan los nombres en MAYÚSCULAS. Para leerlos mejor:
 * «PAPELERA DEL CENTRO SA DE CV» → «Papelera del Centro SA de CV».
 * Si el nombre ya trae minúsculas, se deja igual (alguien lo escribió así).
 */
object NombreLegible {
    private val siglas = setOf("SA", "CV", "SC", "SAPI", "RL", "MX", "SAB", "SOFOM", "ENR")
    private val menores = setOf("DE", "DEL", "LA", "LAS", "LOS", "Y", "E")

    fun de(nombre: String): String {
        if (nombre.any { it.isLowerCase() }) return nombre
        return nombre.trim().split(Regex("\\s+")).mapIndexed { i, palabra ->
            when {
                palabra in siglas -> palabra
                i > 0 && palabra in menores -> palabra.lowercase()
                else -> palabra.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        }.joinToString(" ")
    }

    /** La inicial que va en el círculo del renglón. */
    fun inicial(nombre: String): String = nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "·"

    /** El nombre corto para saludar: «Ana López Garza» → «Ana». */
    fun corto(nombre: String): String = nombre.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
}
