package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO inmutable (record) para el reporte y sincronización de eventos de viaje.
 */
public record EventoViajeDTO(
        String uuid,
        LocalDateTime tiempo,
        TipoEvento tipo,
        double latitud,
        double longitud,
        Long guiaId,
        IncidenteDTO incidente
) implements Serializable {
}
