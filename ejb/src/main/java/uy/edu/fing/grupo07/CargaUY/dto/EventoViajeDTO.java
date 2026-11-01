package uy.edu.fing.grupo07.CargaUY.dto;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO inmutable (record) para el reporte y sincronización de eventos de viaje,
 * incluyendo opcionalmente la distancia de desvío en kilómetros calculada por el FaaS.
 */
public record EventoViajeDTO(
        String uuid,
        LocalDateTime tiempo,
        TipoEvento tipo,
        double latitud,
        double longitud,
        Long guiaId,
        IncidenteDTO incidente,
        Double distanciaDesvioKm
) implements Serializable {

    @JsonbCreator
    public EventoViajeDTO(
            @JsonbProperty("uuid") String uuid,
            @JsonbProperty("tiempo") LocalDateTime tiempo,
            @JsonbProperty("tipo") TipoEvento tipo,
            @JsonbProperty("latitud") double latitud,
            @JsonbProperty("longitud") double longitud,
            @JsonbProperty("guiaId") Long guiaId,
            @JsonbProperty("incidente") IncidenteDTO incidente,
            @JsonbProperty("distanciaDesvioKm") Double distanciaDesvioKm
    ) {
        this.uuid = uuid;
        this.tiempo = tiempo;
        this.tipo = tipo;
        this.latitud = latitud;
        this.longitud = longitud;
        this.guiaId = guiaId;
        this.incidente = incidente;
        this.distanciaDesvioKm = distanciaDesvioKm;
    }

    public EventoViajeDTO(
            String uuid,
            LocalDateTime tiempo,
            TipoEvento tipo,
            double latitud,
            double longitud,
            Long guiaId,
            IncidenteDTO incidente
    ) {
        this(uuid, tiempo, tipo, latitud, longitud, guiaId, incidente, null);
    }
}
