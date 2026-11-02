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

        fun opcionesPermitidasParaEstado(estadoGuia: String?): List<TipoEventoOption> {
            return when (estadoGuia?.trim()?.uppercase()) {
                "SIN_INICIAR" -> listOf(INICIO)
                "EN_CURSO" -> listOf(CARGA_PARCIAL, DESCARGA_PARCIAL, INCIDENTE, FIN)
                "FINALIZADO" -> emptyList()
                else -> entries.toList()
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
    val incidente: IncidenteDto? = null,
    val cantidadKg: Double? = null,
    val mercaderia: String? = null,
    val empresaCarga: String? = null,
    val matricula: String? = null
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
    ERROR;

    companion object {
        fun desdeRespuestaServidor(estadoServidor: String?): EstadoSyncLocal {
            return when (estadoServidor?.trim()?.uppercase()) {
                "ACCEPTED" -> SINCRONIZADO
                "DUPLICATE" -> DUPLICADO
                "REJECTED", "ERROR" -> ERROR
                else -> PENDIENTE
            }
        }
    }
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
    val cantidadKg: Double? = null,
    val mercaderia: String? = null,
    val empresaCarga: String? = null,
    val estadoSync: String = EstadoSyncLocal.PENDIENTE.name,
    val mensajeSync: String? = null
) {
    fun toDto(matricula: String? = null): EventoViajeDto {
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
            incidente = inc,
            cantidadKg = cantidadKg,
            mercaderia = mercaderia?.takeIf { it.isNotBlank() },
            empresaCarga = empresaCarga?.takeIf { it.isNotBlank() },
            matricula = matricula?.takeIf { it.isNotBlank() }
        )
    }
}

data class ResultadoOperacionEvento(
    val evento: EventoLocal,
    val guardadoOffline: Boolean,
    val duplicadoDetectado: Boolean = false,
    val rechazadoPorServidor: Boolean = false,
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
        return "$accion: ${cantidadKg.trim()} Kg | Mercaderia: ${mercaderia.trim()} | Empresa: ${empresa.trim()}"
    }
}

object SyncBatchChunker {
    const val MAX_EVENTOS_POR_LOTE = 10
    const val MAX_BYTES_ESTIMADOS_POR_LOTE = 450_000

    fun estimarBytesJson(dto: EventoViajeDto): Int {
        var total = 256 +
            dto.uuid.length +
            dto.tiempo.length +
            dto.tipo.length +
            (dto.mercaderia?.length ?: 0) +
            (dto.empresaCarga?.length ?: 0) +
            (dto.matricula?.length ?: 0)
        if (dto.incidente != null) {
            total += 64 + dto.incidente.descripcion.length + (dto.incidente.foto?.length ?: 0)
        }
        return total
    }

    fun particionarLotes(
        eventos: List<EventoViajeDto>,
        maxEventosPorLote: Int = MAX_EVENTOS_POR_LOTE,
        maxBytesPorLote: Int = MAX_BYTES_ESTIMADOS_POR_LOTE
    ): List<List<EventoViajeDto>> {
        if (eventos.isEmpty()) return emptyList()
        val limiteCantidad = maxEventosPorLote.coerceAtLeast(1)
        val limiteBytes = maxBytesPorLote.coerceAtLeast(1024)
        val lotes = mutableListOf<List<EventoViajeDto>>()
        var loteActual = mutableListOf<EventoViajeDto>()
        var bytesActuales = 2 // "[]"

        for (dto in eventos) {
            val bytesEvento = estimarBytesJson(dto)
            val excedeCantidad = loteActual.size >= limiteCantidad
            val excedeTamano = loteActual.isNotEmpty() && (bytesActuales + bytesEvento > limiteBytes)
            if (excedeCantidad || excedeTamano) {
                lotes.add(loteActual)
                loteActual = mutableListOf()
                bytesActuales = 2
            }
            loteActual.add(dto)
            bytesActuales += bytesEvento
        }
        if (loteActual.isNotEmpty()) {
            lotes.add(loteActual)
        }
        return lotes
    }
}

