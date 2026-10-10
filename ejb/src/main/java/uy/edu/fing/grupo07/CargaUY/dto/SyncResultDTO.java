package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;

/**
 * DTO inmutable (record) con el resultado del procesamiento de sincronización por evento.
 * Estados típicos: ACCEPTED, DUPLICATE, ERROR.
 */
public record SyncResultDTO(
        String uuid,
        String estado,
        String mensaje
) implements Serializable {

    public static SyncResultDTO accepted(String uuid) {
        return new SyncResultDTO(uuid, "ACCEPTED", "Evento procesado correctamente");
    }

    public static SyncResultDTO duplicate(String uuid) {
        return new SyncResultDTO(uuid, "DUPLICATE", "Evento descartado: UUID ya existente");
    }

    public static SyncResultDTO error(String uuid, String mensaje) {
        return new SyncResultDTO(uuid, "ERROR", mensaje);
    }
}
