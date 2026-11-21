package mx.finsocial.empresarial

import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDeSoftware
import mx.finsocial.empresarial.nucleo.api.LlaveroEnMemoria
import mx.finsocial.empresarial.nucleo.api.ServidorSimulado
import okhttp3.OkHttpClient
import java.time.Instant

/** La app armada para pruebas: servidor simulado sin demora, llave en software y llavero en memoria. */
class AppDePrueba(var ahora: Instant = Instant.parse("2026-11-21T18:00:00Z")) {
    val servidor = ServidorSimulado(reloj = { ahora }, demoraMs = 0)
    val llave = LlaveDeSoftware()
    val guardado = Guardado(LlaveroEnMemoria())
    val api = ApiFinso(
        "https://empresas.finsocial.app/movil/v1",
        "empresarial/1.0.0+1",
        guardado,
        OkHttpClient.Builder().addInterceptor(servidor).build(),
        reloj = { ahora },
    )
    val contenedor = Contenedor(
        guardado = guardado,
        api = api,
        llave = llave,
        telefono = DatosDelTelefono("Pixel de prueba", modelo = "Pixel 9", versionSistema = "16"),
        simulada = true,
        preferencias = Contenedor.PreferenciasEnMemoria(),
        reloj = { ahora },
    )
}
