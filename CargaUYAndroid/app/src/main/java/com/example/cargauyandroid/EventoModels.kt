package com.example.cargauyandroid

enum class TipoEventoOption(
    val codigo: String,
    val etiqueta: String,
    val descripcionAyuda: String
) {
    INICIO(
        codigo = "INICIO",
        etiqueta = "Inicio de viaje",
        descripcionAyuda = "Registra la hora y ubicacion del inicio del viaje."
    ),
    FIN(
        codigo = "FIN",
        etiqueta = "Fin de viaje",
        descripcionAyuda = "Registra la hora y ubicacion de finalizacion del viaje."
    ),
    CARGA_PARCIAL(
        codigo = "CARGA_PARCIAL",
        etiqueta = "Carga parcial",
        descripcionAyuda = "Ingresa la cantidad en Kg, que se carga y a que empresa pertenece."
    ),
    DESCARGA_PARCIAL(
        codigo = "DESCARGA_PARCIAL",
        etiqueta = "Descarga parcial",
        descripcionAyuda = "Ingresa la cantidad en Kg, que se descargo y a que empresa pertenece."
    ),
    INCIDENTE(
        codigo = "INCIDENTE",
        etiqueta = "Incidente",
        descripcionAyuda = "Ingresa la descripcion del incidente y opcionalmente adjunta una foto."
    );

    companion object {
        fun fromCodigo(codigo: String): TipoEventoOption {
            return entries.find { it.codigo.equals(codigo, ignoreCase = true) } ?: INCIDENTE
        }

        fun etiquetaDe(codigo: String): String {
            return when (codigo.uppercase()) {
                "INICIO" -> "Inicio de viaje"
                "FIN" -> "Fin de viaje"
                "CARGA" -> "Carga"
                "CARGA_PARCIAL" -> "Carga parcial"
                "DESCARGA" -> "Descarga"
                "DESCARGA_PARCIAL" -> "Descarga parcial"
                "INCIDENTE" -> "Incidente"
                else -> codigo
            }
        }
    }
}

data class IncidenteDto(
    val foto: String? = null,
    val descripcion: String
)

data class EventoViajeDto(
    val uuid: String,
    val tiempo: String,
    val tipo: String,
    val latitud: Double,
    val longitud: Double,
    val guiaId: Long,
    val incidente: IncidenteDto? = null
)

data class SyncResultDto(
    val uuid: String,
    val estado: String,
    val mensaje: String? = null
)

enum class EstadoSyncLocal {
    PENDIENTE,
    SINCRONIZADO,
    DUPLICADO,
    ERROR
}

data class EventoLocal(
    val uuid: String,
    val guiaId: Long,
    val tipo: String,
    val tiempo: String,
    val latitud: Double,
    val longitud: Double,
    val descripcion: String? = null,
    val foto: String? = null,
    val estadoSync: String = EstadoSyncLocal.PENDIENTE.name,
    val mensajeSync: String? = null
) {
    fun toDto(): EventoViajeDto {
        val inc = if (!descripcion.isNullOrBlank() || !foto.isNullOrBlank()) {
            IncidenteDto(
                foto = foto?.takeIf { it.isNotBlank() },
                descripcion = descripcion ?: ""
            )
        } else {
            null
        }
        return EventoViajeDto(
            uuid = uuid,
            tiempo = tiempo,
            tipo = tipo,
            latitud = latitud,
            longitud = longitud,
            guiaId = guiaId,
            incidente = inc
        )
    }
}

data class ResultadoOperacionEvento(
    val evento: EventoLocal,
    val guardadoOffline: Boolean,
    val duplicadoDetectado: Boolean = false,
    val mensajeUsuario: String
)

data class ResumenSincronizacion(
    val totalEnviados: Int,
    val aceptados: Int,
    val duplicados: Int,
    val errores: Int,
    val rutaActualizada: Boolean,
    val mensaje: String
)

object EventoFormatter {
    fun formatearDetalleCargaParcial(
        esDescarga: Boolean,
        cantidadKg: String,
        mercaderia: String,
        empresa: String
    ): String {
        val accion = if (esDescarga) "Descarga parcial" else "Carga parcial"
        return "$accion: ${cantidadKg.trim()} Kg | Mercaderia: ${mercaderia.trim()}"
    }
}
