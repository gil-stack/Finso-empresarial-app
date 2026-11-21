package mx.finsocial.empresarial.nucleo

/**
 * Textos de error para la persona cuando el servidor no manda `mensaje`
 * (especificación compartida §6). Iguales en iPhone y Android.
 */
object Textos {
    const val SIN_RED = "No hay conexión. Revisa tu internet e inténtalo otra vez."
    const val OTRO = "Algo salió mal. Inténtalo otra vez."

    private val porCodigo = mapOf(
        "CREDENCIALES_INVALIDAS" to "El correo o la contraseña no coinciden.",
        "SIN_ACCESO_MOVIL" to "Tu usuario no tiene acceso a la app. Pídeselo a tu administrador.",
        "DEMASIADOS_INTENTOS" to "Demasiados intentos. Espera unos minutos.",
        "CODIGO_VENCIDO" to "Ese código ya venció. Pide otro.",
        "RETO_VENCIDO" to "Pasó demasiado tiempo. Vuelve a empezar.",
        "PASO_FUERA_DE_ORDEN" to "Algo se desacomodó. Vuelve a empezar.",
        "RETO_INCOMPLETO" to "Faltan pasos. Vuelve a empezar.",
        "ESPERA_PARA_REENVIAR" to "Espera un momento antes de pedir otro código.",
        "SMS_NO_DISPONIBLE" to "Ahorita no podemos mandar SMS. Inténtalo en unos minutos.",
        "CORREO_NO_DISPONIBLE" to "Ahorita no podemos mandar el correo. Inténtalo en unos minutos.",
        "CELULAR_INVALIDO" to "Revisa el número: son 10 dígitos.",
        "CELULAR_EN_USO" to "Ese celular ya lo usa otra persona.",
        "DEMASIADOS_DISPOSITIVOS" to "Ya tienes el máximo de teléfonos. Da de baja uno en el portal.",
        "LLAVE_INVALIDA" to "No pudimos registrar la seguridad de este teléfono. Inténtalo otra vez.",
        "FIRMA_INVALIDA" to "No pudimos confirmar tu identidad. Inténtalo otra vez.",
        "DISPOSITIVO_REVOCADO" to "Este teléfono se dio de baja. Vuelve a registrarlo.",
        "SESION_VENCIDA" to "Tu sesión terminó. Vuelve a entrar.",
        "SESION_REVOCADA" to "Tu sesión terminó. Vuelve a entrar.",
        "EMPRESA_REQUERIDA" to "Elige con qué empresa quieres entrar.",
        "SIN_PERMISO" to "Tu usuario no tiene permiso para esto.",
        "SIN_CONEXION" to "No pudimos leer tu cuenta en este momento. Inténtalo en unos minutos.",
        "DATOS_INVALIDOS" to "Algo en los datos no cuadra. Inténtalo otra vez.",
        "DESTINO_EN_ESPERA" to "Este destino es nuevo: por seguridad se le puede pagar 30 minutos después de registrarlo.",
        "DESTINO_NO_REGISTRADO" to "Solo se puede pagar a cuentas destino registradas en el portal.",
        "DESTINO_SOLO_NOMINA" to "A colaboradores se les paga desde Nómina en el portal.",
        "SALDO_INSUFICIENTE" to "No te alcanza el saldo disponible.",
        "IMPORTE_INVALIDO" to "Revisa el importe.",
        "CONCEPTO_INVALIDO" to "El concepto solo admite letras sin acentos, números, espacios y . , & - (hasta 35).",
        "CUENTA_BLOQUEADA" to "La cuenta está bloqueada y no puede mandar dinero.",
        "RETO_DE_OTRA_OPERACION" to "Los datos cambiaron desde que los revisaste. Vuelve a revisarlos.",
        "MISMA_PERSONA" to "Quien pidió el SPEI no puede liberarlo.",
        "YA_LIBERADA" to "Este SPEI ya fue liberado.",
        "CANCELADA" to "Este SPEI ya fue cancelado.",
        "ORDEN_CAMBIO" to "Este SPEI cambió mientras lo veías. Revísalo otra vez.",
        "LLAVE_EN_CONFLICTO" to "Esta operación ya se mandó con otros datos.",
        "SIN_COMPROBANTE" to "Este movimiento no tiene comprobante.",
        "VERSION_NO_SOPORTADA" to "Actualiza la app desde la tienda para seguir.",
    )

    /** El texto para [codigo]. `CODIGO_INCORRECTO` lleva los intentos que quedan. */
    fun deError(codigo: String, intentosRestantes: Int? = null): String = when (codigo) {
        "CODIGO_INCORRECTO" -> if (intentosRestantes != null) {
            "El código no es correcto. Te quedan $intentosRestantes ${if (intentosRestantes == 1) "intento" else "intentos"}."
        } else {
            "El código no es correcto."
        }
        else -> porCodigo[codigo] ?: OTRO
    }

    // Biometría (§6, segunda tabla).
    const val BIO_CANCELO = "Cancelaste la verificación."
    const val BIO_SIN = "Configura tu huella en los ajustes del teléfono para usar la app."
    const val BIO_BLOQUEADA = "La biometría está bloqueada por muchos intentos. Desbloquea tu teléfono con su código e inténtalo otra vez."
    const val BIO_INVALIDADA = "Cambiaron las huellas o la cara de este teléfono. Por seguridad, vuelve a entrar con tu correo."
    const val BIO_NO_EXISTE = "Este teléfono no está registrado. Entra con tu correo."
}
