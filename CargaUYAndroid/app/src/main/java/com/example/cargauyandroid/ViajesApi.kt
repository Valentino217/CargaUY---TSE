package com.example.cargauyandroid

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class IniciarGubRequest(
    val challenge: String
)

data class IniciarGubResponse(
    val state: String,
    val nonce: String,
    val url: String
)

data class TerminarGubRequest(
    val code: String,
    val state: String,
    val verifier: String
)

data class TerminarGubResponse(
    val token: String,
    val ci: Int? = null,
    val nombre: String? = null,
    val vence: String? = null
)

interface ViajesApi {

    @GET("CargaUY-web/api/chofer/guias")
    suspend fun obtenerViajes(
        @Query("ci") cedula: Int,
        @Query("estado") estado: String? = null
    ): List<Viaje>

    @GET("CargaUY-web/api/chofer/guia-asignada")
    suspend fun obtenerGuiaAsignada(
        @Query("ci") cedula: Int
    ): Viaje

    @GET("CargaUY-web/api/chofer/eventos/{guiaId}")
    suspend fun listarEventosPorGuia(
        @Path("guiaId") guiaId: Long
    ): List<EventoViajeDto>

    @POST("CargaUY-web/api/chofer/eventos/sync")
    suspend fun sincronizarEventos(
        @Body eventos: List<EventoViajeDto>
    ): List<SyncResultDto>

    @POST("CargaUY-web/api/auth/dev-chofer")
    suspend fun obtenerDevToken(
        @Body body: Map<String, Int>
    ): Map<String, Any>

    @POST("CargaUY-web/api/auth/gub/iniciar")
    suspend fun iniciarGub(
        @Body body: IniciarGubRequest
    ): IniciarGubResponse

    @POST("CargaUY-web/api/auth/gub/terminar")
    suspend fun terminarGub(
        @Body body: TerminarGubRequest
    ): TerminarGubResponse
}
