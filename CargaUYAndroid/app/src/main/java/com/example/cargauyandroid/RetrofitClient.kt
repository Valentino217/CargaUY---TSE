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
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException("No se pudo conectar a ninguna URL candidata")
    }
}

object ApiClient {
    val apiService: ViajesApi by lazy {
        RetrofitClient.retrofit.create(ViajesApi::class.java)
    }
}
