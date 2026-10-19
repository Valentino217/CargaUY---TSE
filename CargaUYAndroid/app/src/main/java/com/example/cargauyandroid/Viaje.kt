package com.example.cargauyandroid

data class Viaje(
    val id: Long,
    val fecha: String?,
    val origen: String,
    val destino: String,
    val rubroCliente: String?,
    val volumenCarga: Float = 0f,
    val estado: String,
    val version: Int = 0,
    val vehiculoMatricula: String? = null,
    val vehiculoMarcaModelo: String? = null,
    val nroEmpresa: Int? = null,
    val empresaRazonSocial: String? = null,
    val choferId: Int? = null,
    val choferNombre: String? = null
)