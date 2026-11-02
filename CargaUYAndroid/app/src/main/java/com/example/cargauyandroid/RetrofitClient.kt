package com.example.cargauyandroid

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout(8000, TimeUnit.MILLISECONDS)
            .writeTimeout(8000, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                SesionMovil.token.takeIf { it.isNotBlank() }?.let {
                    request.header("Authorization", "Bearer $it")
                }
                chain.proceed(request.build())
            }
            .build()
    }

    private val clientsByUrl: Map<String, ViajesApi> = Constants.CANDIDATE_URLS.associateWith { url ->
        Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ViajesApi::class.java)
    }

    @Volatile
    var activeBaseUrl: String = Constants.BASE_URL

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    suspend fun <T> executeWithFallback(block: suspend (ViajesApi) -> T): T {
        val orderedUrls = (listOf(activeBaseUrl) + Constants.CANDIDATE_URLS).distinct()
        var lastException: Exception? = null
        for (url in orderedUrls) {
            val api = clientsByUrl[url] ?: continue
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
     * reemplazándola por el host activo alcanzable por el emulador o dispositivo móvil.
     */
    fun resolverUrlProveedor(urlOriginal: String): String {
        return try {
            val uri = URI(urlOriginal)
            val host = uri.host ?: return urlOriginal
            if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") {
                val baseUri = URI(activeBaseUrl)
                val hostObjetivo = baseUri.host ?: "10.0.2.2"
                val puerto = if (uri.port != -1) ":${uri.port}" else ""
                val scheme = uri.scheme ?: "http"
                val path = uri.rawPath ?: ""
                val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
                val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""
                "$scheme://$hostObjetivo$puerto$path$query$fragment"
            } else {
                urlOriginal
            }
        } catch (e: Exception) {
            Log.w("CARGAUY", "No se pudo ajustar URL de proveedor: ${e.message}")
            urlOriginal
        }
    }
}

/** Estado de sesión autenticada en memoria y utilidades para el chofer */
object SesionMovil {
    @Volatile var token: String = ""
    @Volatile var cedulaAsociada: Int? = null
    @Volatile var nombreChofer: String? = null

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
