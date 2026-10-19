package com.example.cargauyandroid

import retrofit2.http.GET
import retrofit2.http.Query

interface ViajeService {

    @GET("/CargaUY-web/api/chofer/guias")
    suspend fun getAllViajes(
        @Query("ci") cedula: Int = Constants.DEFAULT_CEDULA
    ): List<Viaje>
}