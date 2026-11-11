package com.example.cargauyandroid

import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout(8000, TimeUnit.MILLISECONDS)
            .writeTimeout(8000, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                // Propagación de trazabilidad distribuida (X-Correlation-ID) hacia el backend y colas JMS
                if (chain.request().header("X-Correlation-ID").isNullOrBlank()) {
                    request.header("X-Correlation-ID", UUID.randomUUID().toString())
                }
                SesionMovil.token.takeIf { it.isNotBlank() }?.let {
                    request.header("Authorization", "Bearer $it")
                }
                chain.proceed(request.build())
            }
            .build()
    }

    private val clientsByUrl: MutableMap<String, ViajesApi> = Constants.CANDIDATE_URLS.associateWith { url ->
        crearApiParaUrl(url)
    }.toMutableMap()

    private fun crearApiParaUrl(url: String): ViajesApi {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ViajesApi::class.java)
    }

    /**
     * Detecta si la app se ejecuta dentro del emulador oficial de Android (AVD/QEMU)
     */
    fun esEmulador(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    // Inicializar URL base según si corre en emulador o dispositivo real
    var activeBaseUrl: String by mutableStateOf(
        Constants.BASE_URL
    )

    fun actualizarBaseUrl(nuevaUrl: String) {
        val urlNormalizada = if (nuevaUrl.endsWith("/")) nuevaUrl else "$nuevaUrl/"
        if (!clientsByUrl.containsKey(urlNormalizada)) {
            clientsByUrl[urlNormalizada] = crearApiParaUrl(urlNormalizada)
        }
        activeBaseUrl = urlNormalizada
        Log.d("CARGAUY", "URL base activa cambiada a: $activeBaseUrl")
    }

    val retrofit: Retrofit
        get() = Retrofit.Builder()
            .baseUrl(activeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    suspend fun <T> executeWithFallback(block: suspend (ViajesApi) -> T): T {
        // En emulador, priorizar 10.0.2.2; en físico, priorizar la IP activa y candidatos
        val urlsOrdenadas = if (esEmulador()) {
            (listOf(activeBaseUrl) + Constants.CANDIDATE_URLS).distinct()
        } else {
            (listOf(activeBaseUrl) + Constants.CANDIDATE_URLS).distinct()
        }

        var lastException: Exception? = null
        for (url in urlsOrdenadas) {
            val api = clientsByUrl.getOrPut(url) { crearApiParaUrl(url) }
            try {
                Log.d("CARGAUY", "Intentando llamada en $url ...")
                val result = block(api)
                activeBaseUrl = url
                Log.d("CARGAUY", "Llamada exitosa en $url")
                return result
            } catch (e: Exception) {
                Log.w("CARGAUY", "Fallo llamada en $url: ${e.javaClass.simpleName}: ${e.message}")
                if (e is retrofit2.HttpException) throw e
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("No se pudo conectar a ninguna URL candidata")
    }

    /**
     * Ajusta la URL de autorización de Keycloak/gub.uy si el backend devolvió "localhost" o "127.0.0.1",
     * reemplazándola por el host alcanzable por el emulador (10.0.2.2) o la IP del servidor en LAN.
     */
    fun resolverUrlProveedor(urlOriginal: String): String {
        return try {
            val uri = URI(urlOriginal)
            val hostOriginal = uri.host ?: return urlOriginal
            if (hostOriginal != "localhost" && hostOriginal != "127.0.0.1") {
                return urlOriginal
            }
            val puertoOriginal = if (uri.port != -1) ":${uri.port}" else ""

            val hostActivo = try { URI(activeBaseUrl).host } catch (_: Exception) { null }

            val hostDestino = when {
                hostOriginal.contains("gub.uy") || hostOriginal.contains("elasticloud.uy") -> hostOriginal
                hostActivo != null && hostActivo.contains("elasticloud.uy") -> hostActivo
                // Si estamos en emulador y la conexión activa es local o 10.0.2.2, Keycloak está en 10.0.2.2:8083
                esEmulador() && (hostActivo == null || hostActivo == "10.0.2.2" || hostActivo == "localhost" || hostActivo == "127.0.0.1") -> "10.0.2.2"
                // Si la conexión activa es una IP LAN específica (ej. 10.0.0.16), Keycloak está en esa misma IP LAN
                hostActivo != null && hostActivo != "localhost" && hostActivo != "127.0.0.1" -> hostActivo
                // Fallback para emulador si la URL venía con localhost
                esEmulador() -> "10.0.2.2"
                else -> hostActivo ?: hostOriginal
            }

            val scheme = uri.scheme ?: "http"
            val path = uri.rawPath ?: ""
            val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
            val urlFinal = "$scheme://$hostDestino$puertoOriginal$path$query$fragment"
            Log.d("CARGAUY", "URL Proveedor resuelta: $urlFinal (desde original: $urlOriginal)")
            urlFinal
        } catch (e: Exception) {
            Log.w("CARGAUY", "No se pudo ajustar URL de proveedor: ${e.message}")
            urlOriginal
        }
    }
}

/**
 * Estado de sesión autenticada reactivo con Compose:
 * Al modificar cualquiera de sus campos, la UI se recompone de inmediato.
 */
object SesionMovil {
    var token: String by mutableStateOf("")
    var cedulaAsociada: Int? by mutableStateOf(null)
    var nombreChofer: String? by mutableStateOf(null)

    val estaAutenticado: Boolean
        get() = token.isNotBlank()

    fun limpiar() {
        token = ""
        cedulaAsociada = null
        nombreChofer = null
    }
}

object ApiClient {
    val apiService: ViajesApi by lazy {
        RetrofitClient.retrofit.create(ViajesApi::class.java)
    }
}
