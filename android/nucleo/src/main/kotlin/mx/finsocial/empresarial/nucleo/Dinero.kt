package mx.finsocial.empresarial.nucleo

/**
 * El dinero viaja y se guarda en **centavos enteros** (125050 = $1,250.50).
 * Nunca se usan `Double` para dinero: se redondean mal.
 */
object Dinero {
    /** Lo que se muestra cuando la persona oculta los importes. */
    const val OCULTO = "$ •••••"

    /** Signo menos tipográfico (U+2212), el mismo que usa la app de iPhone. */
    const val MENOS = "−"

    private val miles = Regex("""(\d)(?=(\d{3})+(?!\d))""")

    private fun agrupar(enteros: String) = enteros.replace(miles, "$1,")

    /** `125050` → `$1,250.50`. Con [conSigno], los abonos llevan `+` y los cargos `−`. */
    fun pesos(centavos: Long, conSigno: Boolean = false, sinCentavos: Boolean = false): String {
        val negativo = centavos < 0
        val absoluto = if (negativo) -centavos else centavos
        val enteros = agrupar((absoluto / 100).toString())
        val resto = (absoluto % 100).toString().padStart(2, '0')
        val cuerpo = if (sinCentavos) "$$enteros" else "$$enteros.$resto"
        return when {
            negativo -> "$MENOS$cuerpo"
            conSigno -> "+$cuerpo"
            else -> cuerpo
        }
    }

    /** Lo que la persona tecleó (`"1,250.5"`, `"$80"`) → centavos. `null` si no es un importe válido. */
    fun centavosDe(tecleado: String): Long? {
        val limpio = tecleado.replace(Regex("""[$,\s]"""), "")
        val m = Regex("""^(\d{1,12})(?:\.(\d{0,2}))?$""").matchEntire(limpio) ?: return null
        val enteros = m.groupValues[1].toLong()
        val dec = m.groupValues[2].padEnd(2, '0')
        return enteros * 100 + dec.toLong()
    }

    /**
     * Formatea el importe mientras se teclea: agrupa con comas (`1250.5` →
     * `1,250.5`) y no deja más de dos decimales. Una coma seguida de 0 a 2
     * dígitos recién tecleada (teclados con coma decimal) se toma como punto.
     * Si lo nuevo no es un importe, regresa [anterior].
     */
    fun formatearAlTeclear(anterior: String, nuevo: String): String {
        var texto = nuevo
        if ('.' !in texto && texto.length > anterior.length) {
            val coma = Regex(""",(\d{0,2})$""").find(texto)
            val yaEstaba = ',' in anterior && Regex(""",\d{3}""").containsMatchIn(anterior)
            if (coma != null && !yaEstaba) {
                texto = texto.replaceRange(coma.range.first, coma.range.first + 1, ".")
            }
        }
        val crudo = texto.replace(Regex("""[$,\s]"""), "")
        if (crudo.isEmpty()) return ""
        if (!Regex("""^\d{0,12}(\.\d{0,2})?$""").matches(crudo)) return anterior
        val partes = crudo.split('.')
        val enteros = partes[0].replaceFirst(Regex("""^0+(?=\d)"""), "")
        val agrupado = agrupar(enteros)
        return (agrupado.ifEmpty { "0" }) + (if (partes.size > 1) "." + partes[1] else "")
    }

    /** CLABE en grupos de 4 para leerla: `6461 8053 5000 0048 21`. */
    fun clabeAgrupada(clabe: String): String = clabe.chunked(4).joinToString(" ")
}
