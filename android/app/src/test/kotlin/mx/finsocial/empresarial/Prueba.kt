package mx.finsocial.empresarial

import mx.finsocial.empresarial.nucleo.api.ApiFinso
import mx.finsocial.empresarial.nucleo.api.DatosDelTelefono
import mx.finsocial.empresarial.nucleo.api.Guardado
import mx.finsocial.empresarial.nucleo.api.LlaveDeSoftware
import mx.finsocial.empresarial.nucleo.api.LlaveroEnMemoria
import mx.finsocial.empresarial.nucleo.api.ServidorSimulado
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Instant

/** La app armada para pruebas: servidor simulado sin demora, llave en software y llavero en memoria. */
class AppDePrueba(var ahora: Instant = Instant.parse("2026-11-21T18:00:00Z")) {
    val servidor = ServidorSimulado(reloj = { ahora }, demoraMs = 0)

    /** La siguiente llamada POST a esta ruta llega al servidor, pero la respuesta se pierde. */
    @Volatile var cortarDespuesDe: String? = null
    val llave = LlaveDeSoftware()
    val guardado = Guardado(LlaveroEnMemoria())
    val api = ApiFinso(
        "https://empresas.finsocial.app/movil/v1",
        "empresarial/1.0.0+1",
        guardado,
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                // Simula que se corta la red justo después de que el servidor procesó la llamada.
                val r = chain.proceed(chain.request())
                val ruta = cortarDespuesDe
                if (ruta != null && chain.request().method == "POST" && chain.request().url.encodedPath.endsWith(ruta)) {
                    cortarDespuesDe = null
                    r.close()
                    throw IOException("se cortó")
                }
                r
            })
            .addInterceptor(servidor)
            .build(),
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
