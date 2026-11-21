package mx.finsocial.empresarial.nucleo

/**
 * El concepto de un SPEI (contrato §4.1): 1 a 35 caracteres de `[A-Za-z0-9 .,&-]`,
 * sin espacios al inicio, al final ni dobles. Finso Empresas le antepone «PAGO »
 * y el SPEI admite 40.
 */
object Concepto {
    const val MAXIMO = 35

    private val permitido = Regex("""[A-Za-z0-9 .,&-]""")
    private val valido = Regex("""^[A-Za-z0-9.,&-](?:[A-Za-z0-9.,&-]| (?! ))*$""")
    private val acentos = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
        'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U', 'Ü' to 'U', 'Ñ' to 'N',
    )

    /** ¿Se puede mandar tal cual? */
    fun esValido(c: String): Boolean = c.length in 1..MAXIMO && valido.matches(c) && !c.endsWith(" ")

    /**
     * Limpia mientras se escribe, para que la persona no se tope con un error al
     * final: quita acentos (á → a, ñ → n), descarta lo demás, colapsa espacios,
     * no deja espacio al inicio y corta en 35. El espacio final se quita al mandar ([paraMandar]).
     */
    fun limpiar(s: String): String {
        val b = StringBuilder()
        for (ch in s) {
            val c = acentos[ch] ?: ch
            if (permitido.matches(c.toString())) b.append(c)
        }
        val t = b.toString().replace(Regex(" {2,}"), " ").trimStart()
        return if (t.length > MAXIMO) t.substring(0, MAXIMO) else t
    }

    fun paraMandar(s: String): String = limpiar(s).trimEnd()
}
