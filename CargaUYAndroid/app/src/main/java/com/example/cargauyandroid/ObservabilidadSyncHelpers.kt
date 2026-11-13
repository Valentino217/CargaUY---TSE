package com.example.cargauyandroid

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Utilidades puras (sin dependencias de Android runtime) para generar encabezados W3C
 * de observabilidad distribuida (`X-Correlation-ID` + `traceparent`) y detectar conflictos
 * al sincronizar eventos offline contra la lista actual de guías del chofer.
 */
object TraceHeaderHelper {

    fun generarCorrelacion(): String = UUID.randomUUID().toString()

    fun normalizarTraceIdHex(correlacion: String?): String {
        val raw = correlacion?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            try {
                return UUID.fromString(raw).toString().replace("-", "").lowercase(Locale.ROOT)
            } catch (_: Exception) {
                return UUID.nameUUIDFromBytes(raw.toByteArray(StandardCharsets.UTF_8))
                    .toString()
                    .replace("-", "")
                    .lowercase(Locale.ROOT)
            }
        }
        return UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
    }

    fun generarTraceparent(correlacion: String?, spanHex: String? = null): String {
        val traceId = normalizarTraceIdHex(correlacion)
        val spanLimpio = spanHex?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val spanId = if (spanLimpio.matches(Regex("[0-9a-f]{16}")) && spanLimpio != "0000000000000000") {
            spanLimpio
        } else {
            UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        }
        return "00-$traceId-$spanId-01"
    }
}

data class ResultadoFiltroPendientes(
    val enviables: List<EventoLocal>,
    val enConflicto: List<EventoLocal>,
    val rutaActualizada: Boolean
)

object SyncConflictResolver {

    fun mensajeConflictoGuia(guiaId: Long): String {
        return "Conflicto de sincronización: la guía #$guiaId fue reasignada o dejó de estar asignada al chofer."
    }

    fun filtrarPendientesContraGuias(
        pendientes: List<EventoLocal>,
        guiasPrevias: List<Viaje>,
        guiasServidor: List<Viaje>
    ): ResultadoFiltroPendientes {
        val idsActivos = guiasServidor.map { it.id }.toHashSet()
        val enviables = ArrayList<EventoLocal>(pendientes.size)
        val enConflicto = ArrayList<EventoLocal>()
        for (ev in pendientes) {
            if (idsActivos.contains(ev.guiaId)) {
                enviables.add(ev)
            } else {
                enConflicto.add(ev)
            }
        }

        val mapaPrevias = guiasPrevias.associateBy { it.id }
        val rutaActualizada = guiasServidor.any { gNueva ->
            val gAnt = mapaPrevias[gNueva.id]
            gAnt != null && (
                gAnt.version != gNueva.version ||
                    gAnt.destino != gNueva.destino ||
                    gAnt.origen != gNueva.origen
                )
        }
        return ResultadoFiltroPendientes(
            enviables = enviables,
            enConflicto = enConflicto,
            rutaActualizada = rutaActualizada
        )
    }
}
