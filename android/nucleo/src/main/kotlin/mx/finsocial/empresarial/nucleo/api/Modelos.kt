package mx.finsocial.empresarial.nucleo.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lo que viaja entre la app y Finso Empresas (`/movil/v1`, contrato v1.1).
 * Fechas como texto ISO-8601 en UTC; dinero en centavos (`Long`).
 */
val JsonFinso = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** Pasos del primer ingreso, en el orden que manda el servidor (§1.1). */
object Paso {
    const val CODIGO_CORREO = "CODIGO_CORREO"
    const val CAPTURAR_CELULAR = "CAPTURAR_CELULAR"
    const val CODIGO_SMS = "CODIGO_SMS"
}

@Serializable data class CuerpoDeError(
    val codigo: String,
    val mensaje: String? = null,
    val campos: List<String>? = null,
    val reintentarEn: String? = null,
    val intentosRestantes: Int? = null,
)

// ───── §1 Entrar ─────

@Serializable data class DatosDelTelefono(
    val nombre: String,
    val plataforma: String = "android",
    val modelo: String,
    val versionSistema: String,
)

@Serializable data class PeticionInicio(val correo: String, val contrasena: String, val dispositivo: DatosDelTelefono)

@Serializable data class RetoDeEntrada(
    val reto: String,
    val expiraEn: String,
    val pasos: List<String>,
    val celularEnmascarado: String? = null,
    val correoEnmascarado: String? = null,
    val reenvioDisponibleEn: String? = null,
)

@Serializable data class PeticionCelular(val reto: String, val celular: String)

@Serializable data class RespuestaCelular(val celularEnmascarado: String? = null, val reenvioDisponibleEn: String? = null)

@Serializable data class PeticionCodigo(val reto: String, val paso: String, val codigo: String)

@Serializable data class RespuestaCodigo(
    val pasosPendientes: List<String>,
    val celularEnmascarado: String? = null,
    val reenvioDisponibleEn: String? = null,
)

@Serializable data class PeticionReenvio(val reto: String, val paso: String)

@Serializable data class RespuestaReenvio(val reenvioDisponibleEn: String? = null, val celularEnmascarado: String? = null)

@Serializable data class PeticionRegistro(
    val reto: String,
    val llavePublica: String,
    val algoritmo: String = "ES256",
    val nombre: String? = null,
    val firma: String,
)

@Serializable data class Sesion(
    val acceso: String,
    val accesoExpiraEn: String,
    val renovacion: String,
    val renovacionExpiraEn: String,
)

@Serializable data class EmpresaBreve(val id: String, val nombre: String)

@Serializable data class EmpresaDelUsuario(val id: String, val nombre: String, val papeles: List<String> = emptyList())

@Serializable data class Permisos(
    val verCuenta: Boolean = false,
    val verDestinos: Boolean = false,
    val pedirSpei: Boolean = false,
    val liberarSpei: Boolean = false,
    val verOrdenes: Boolean = false,
)

@Serializable data class Usuario(
    val id: String,
    val nombre: String,
    val correo: String,
    val papeles: List<String> = emptyList(),
    val empresa: EmpresaBreve? = null,
    val empresas: List<EmpresaDelUsuario> = emptyList(),
    val permisos: Permisos = Permisos(),
    val operacionDelDueno: Boolean = false,
) {
    /** Hay que elegir empresa (§1.8 v1.1). */
    val debeElegirEmpresa: Boolean get() = empresa == null && empresas.size > 1
}

@Serializable data class Registro(val dispositivo: String, val sesion: Sesion, val usuario: Usuario)

@Serializable data class PeticionRetoBiometria(val dispositivo: String)

@Serializable data class RetoDeFirma(val reto: String, val expiraEn: String)

@Serializable data class PeticionBiometria(val dispositivo: String, val reto: String, val firma: String)

@Serializable data class Ingreso(val sesion: Sesion, val usuario: Usuario)

@Serializable data class PeticionRenovar(val renovacion: String)

// ───── §2 Cuenta ─────

@Serializable data class Cuenta(
    val clabe: String,
    val titular: String,
    val estado: String,
    val saldoCentavos: Long,
    val disponibleCentavos: Long,
    val horaDelSaldo: String,
) {
    val bloqueada: Boolean get() = estado == "BLOQUEADA"
}

@Serializable data class LecturaDeCuenta(val cuenta: Cuenta? = null, val fuente: String? = null, val sinConexion: Boolean = false)

@Serializable data class Contraparte(val nombre: String? = null, val banco: String? = null, val clabeFinal: String? = null)

@Serializable data class Movimiento(
    val id: String,
    val sentido: String,
    val tipo: String,
    val importeCentavos: Long,
    val saldoResultanteCentavos: Long? = null,
    val fecha: String,
    val concepto: String? = null,
    val claveRastreo: String? = null,
    val contraparte: Contraparte? = null,
    val orden: String? = null,
    val comprobante: Boolean = false,
) {
    val esAbono: Boolean get() = sentido == "ABONO"
}

@Serializable data class PaginaDeMovimientos(
    val mes: String,
    val elementos: List<Movimiento>,
    val siguienteCursor: String? = null,
    val incompleto: Boolean = false,
    val sinConexion: Boolean = false,
)

// ───── §3 Destinos ─────

@Serializable data class Destino(
    val id: String,
    val titular: String,
    val tipo: String,
    val banco: String? = null,
    val clabeFinal: String,
    val estado: String,
    val activoDesde: String? = null,
) {
    val esColaborador: Boolean get() = tipo == "COLABORADOR"
    val enEspera: Boolean get() = estado == "EN_ESPERA"
    val sePuedePagar: Boolean get() = !esColaborador && estado == "ACTIVO"
}

@Serializable data class ListaDeDestinos(val elementos: List<Destino>)

// ───── §4 SPEI ─────

@Serializable data class SolicitudSpei(val destino: String, val importeCentavos: Long, val concepto: String)

@Serializable data class DestinoBreve(
    val id: String? = null,
    val titular: String,
    val banco: String? = null,
    val clabeFinal: String,
)

@Serializable data class ResumenSpei(
    val destino: DestinoBreve,
    val importeCentavos: Long,
    val comisionCentavos: Long = 0,
    val concepto: String,
)

@Serializable data class PreparacionSpei(
    val reto: String,
    val expiraEn: String,
    val resumen: ResumenSpei,
    val requiereLiberador: Boolean = true,
)

@Serializable data class PeticionSpei(
    val destino: String,
    val importeCentavos: Long,
    val concepto: String,
    val reto: String,
    val firma: String,
)

object EstadoOrden {
    const val POR_LIBERAR = "POR_LIBERAR"
    const val EN_PROCESO = "EN_PROCESO"
    const val ENVIADA = "ENVIADA"
    const val LIQUIDADA = "LIQUIDADA"
    const val DEVUELTA = "DEVUELTA"
    const val RECHAZADA = "RECHAZADA"
    const val CANCELADA = "CANCELADA"
    val finales = setOf(LIQUIDADA, DEVUELTA, RECHAZADA, CANCELADA)
}

@Serializable data class Orden(
    val id: String,
    val estado: String,
    val destino: DestinoBreve,
    val importeCentavos: Long,
    val concepto: String,
    val referenciaNumerica: Long? = null,
    val claveRastreo: String? = null,
    val pedidaPor: String,
    val liberadaPor: String? = null,
    val creadaEn: String,
    val actualizadaEn: String,
    val motivo: String? = null,
    val version: Int = 1,
    val puedoLiberar: Boolean = false,
) {
    val esFinal: Boolean get() = estado in EstadoOrden.finales
}

@Serializable data class ListaDeOrdenes(val elementos: List<Orden>, val siguienteCursor: String? = null)

@Serializable data class PreparacionLiberar(val reto: String, val expiraEn: String, val importeCentavos: Long, val version: Int = 1)

@Serializable data class PeticionFirma(val reto: String, val firma: String)

@Serializable data class PeticionRechazo(val motivo: String, val reto: String, val firma: String)
