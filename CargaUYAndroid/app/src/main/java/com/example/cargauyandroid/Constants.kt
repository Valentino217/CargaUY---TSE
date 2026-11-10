package com.example.cargauyandroid

object Constants {
    const val BASE_URL = "http://10.0.0.16:8080/"
    val CANDIDATE_URLS = listOf(
        "http://10.0.0.16:8080/",
        "http://10.0.2.2:8080/",
        "http://127.0.0.1:8080/"
    )
    const val DEFAULT_CEDULA = 11223344
    const val DEFAULT_LATITUD = -34.9011
    const val DEFAULT_LONGITUD = -56.1645

    /**
     * Oculta por defecto las opciones de pruebas y configuración manual (Dev) en la UI.
     * Cambiar a true para mostrarlas inmediatamente, o tocar 5 veces el título en la pantalla.
     */
    const val HABILITAR_OPCIONES_DEV = false
}
