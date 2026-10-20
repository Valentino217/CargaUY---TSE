package com.example.cargauyandroid

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(5000, TimeUnit.MILLISECONDS)
            .writeTimeout(5000, TimeUnit.MILLISECONDS)
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
        val orderedUrls = listOf(activeBaseUrl) + Constants.CANDIDATE_URLS.filter { it != activeBaseUrl }
        var lastException: Exception? = null
        for (url in orderedUrls) {
            val api = clientsByUrl[url] ?: continue
            try {
                val result = block(api)
                activeBaseUrl = url
                return result
            } catch (e: Exception) {
                if (e is retrofit2.HttpException) throw e
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("No se pudo conectar a ninguna URL candidata")
    }
}

/** Token temporal en memoria para pruebas de integración. El login OIDC móvil sigue pendiente. */
object SesionMovil {
    @Volatile var token: String = ""
}

object ApiClient {
    val apiService: ViajesApi by lazy {
        RetrofitClient.retrofit.create(ViajesApi::class.java)
    }
}
