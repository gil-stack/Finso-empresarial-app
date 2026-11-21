package mx.finsocial.empresarial.estado

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mx.finsocial.empresarial.nucleo.NombreLegible
import mx.finsocial.empresarial.nucleo.TextoFirmado
import mx.finsocial.empresarial.nucleo.Textos
import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.EmpresaBreve
import mx.finsocial.empresarial.nucleo.api.FallaApi
import mx.finsocial.empresarial.nucleo.api.FallaBiometria
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDelTelefono
import mx.finsocial.empresarial.nucleo.api.Usuario
import java.time.Duration
import java.time.Instant

/** En qué punto está la persona (especificación §3). */
sealed interface EstadoSesion {
    /** Leyendo el llavero. */
    data object Arrancando : EstadoSesion

    /** Teléfono sin registrar: bienvenida y primer ingreso. [aviso] se muestra una vez. */
    data class Fuera(val aviso: String? = null) : EstadoSesion

    /** Registrado: pide la huella. */
    data class Bloqueada(val nombre: String?) : EstadoSesion

    /** Trabaja con varias empresas y no ha elegido. */
    data class ElegirEmpresa(val usuario: Usuario) : EstadoSesion

    data class Dentro(val usuario: Usuario) : EstadoSesion
}

/**
 * Decide qué ve la persona y reacciona a lo que dice el servidor: sesión
 * vencida → huella; teléfono dado de baja → correo; empresa requerida →
 * elegir. Tras 5 minutos en segundo plano pide la huella otra vez.
 */
class ControlSesion(
    private val api: ApiFinso,
    private val guardado: Guardado,
    private val llave: LlaveDelTelefono,
    private val reloj: () -> Instant,
    private val alcance: CoroutineScope,
) {
    companion object {
        val BLOQUEO_EN_FONDO: Duration = Duration.ofMinutes(5)
    }

    private val _estado = MutableStateFlow<EstadoSesion>(EstadoSesion.Arrancando)
    val estado: StateFlow<EstadoSesion> = _estado

    private var enFondoDesde: Instant? = null

    init {
        api.alFallarSesion = { f -> alcance.launch { reaccionar(f) } }
    }

    fun arrancar() {
        if (_estado.value != EstadoSesion.Arrancando) return
        _estado.value = if (guardado.dispositivo != null && llave.existe()) {
            EstadoSesion.Bloqueada(guardado.nombreCorto)
        } else {
            // Registro a medias (sin llave o sin dispositivo): se limpia todo.
            val aviso = guardado.avisoDeSalida
            if (guardado.dispositivo != null || llave.existe()) {
                guardado.olvidarTodo()
                llave.borrar()
            }
            guardado.avisoDeSalida = null
            EstadoSesion.Fuera(aviso)
        }
    }

    private fun reaccionar(f: FallaApi) {
        val adentro = _estado.value is EstadoSesion.Dentro || _estado.value is EstadoSesion.ElegirEmpresa
        when {
            f.olvidaElTelefono -> if (_estado.value !is EstadoSesion.Fuera) olvidarTelefono(f.texto)
            f.pideBiometria -> if (adentro) _estado.value = EstadoSesion.Bloqueada(guardado.nombreCorto)
            f.codigo == "EMPRESA_REQUERIDA" -> {
                val u = (_estado.value as? EstadoSesion.Dentro)?.usuario
                if (u != null) _estado.value = EstadoSesion.ElegirEmpresa(u.copy(empresa = null))
            }
        }
    }

    /** Ya hay sesión (primer ingreso o huella): adentro o a elegir empresa. */
    fun alEntrar(usuario: Usuario) {
        guardado.nombreCorto = NombreLegible.corto(usuario.nombre)
        val elegida = guardado.empresa?.takeIf { id -> usuario.empresas.any { it.id == id } }
        when {
            !usuario.debeElegirEmpresa -> _estado.value = EstadoSesion.Dentro(usuario)
            elegida == null -> _estado.value = EstadoSesion.ElegirEmpresa(usuario)
            else -> {
                // Ya había elegido: /yo con su encabezado trae la empresa.
                val e = usuario.empresas.first { it.id == elegida }
                _estado.value = EstadoSesion.Dentro(usuario.copy(empresa = EmpresaBreve(e.id, e.nombre)))
                alcance.launch { runCatching { api.yo() }.onSuccess { actualizar(it) } }
            }
        }
    }

    /**
     * Ingresos siguientes (§1.6): reto, huella y entrada. Lanza [FallaApi] o
     * [FallaBiometria]; si la llave quedó inválida olvida el teléfono.
     */
    suspend fun entrarConHuella() {
        try {
            val reto = api.retoParaEntrar()
            val firma = llave.firmar(TextoFirmado.entrar(guardado.dispositivo ?: "", reto.reto), "Entrar a Finso Empresarial")
            alEntrar(api.entrarConBiometria(reto.reto, firma).usuario)
        } catch (e: FallaBiometria) {
            when (e.tipo) {
                FallaBiometria.Tipo.INVALIDADA -> olvidarTelefono(Textos.BIO_INVALIDADA)
                FallaBiometria.Tipo.NO_EXISTE -> olvidarTelefono(Textos.BIO_NO_EXISTE)
                else -> Unit
            }
            throw e
        }
    }

    suspend fun elegirEmpresa(id: String) {
        _estado.value = EstadoSesion.Dentro(api.elegirEmpresa(id))
    }

    /** Vuelve a elegir empresa desde Ajustes. */
    fun cambiarEmpresa() {
        val u = (_estado.value as? EstadoSesion.Dentro)?.usuario ?: return
        guardado.empresa = null
        _estado.value = EstadoSesion.ElegirEmpresa(u.copy(empresa = null))
    }

    /** Actualiza el usuario (p. ej. tras `/yo`). */
    fun actualizar(usuario: Usuario) {
        if (_estado.value is EstadoSesion.Dentro) _estado.value = EstadoSesion.Dentro(usuario)
    }

    /** «Salir»: la próxima vez se entra con la huella. */
    fun salir() {
        _estado.value = EstadoSesion.Bloqueada(guardado.nombreCorto)
        alcance.launch { api.salir() }
    }

    /** «Olvidar este teléfono» o baja desde el servidor: vuelve al correo. */
    fun olvidarTelefono(aviso: String? = null) {
        // Se avisa al servidor con el token que había (sin esperar) y se borra todo.
        val sesion = guardado.sesion
        val dispositivo = guardado.dispositivo
        if (sesion != null && dispositivo != null) alcance.launch { api.revocar(sesion, dispositivo) }
        guardado.olvidarTodo()
        llave.borrar()
        _estado.value = EstadoSesion.Fuera(aviso)
    }

    fun alIrAlFondo() {
        enFondoDesde = reloj()
    }

    fun alVolver() {
        val desde = enFondoDesde ?: return
        enFondoDesde = null
        val adentro = _estado.value is EstadoSesion.Dentro || _estado.value is EstadoSesion.ElegirEmpresa
        if (adentro && Duration.between(desde, reloj()) >= BLOQUEO_EN_FONDO) {
            _estado.value = EstadoSesion.Bloqueada(guardado.nombreCorto)
        }
    }
}
