package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;

/**
 * DTO inmutable (record) con datos de un incidente reportado en ruta.
 */
public record IncidenteDTO(
        String foto,
        String descripcion
) implements Serializable {
}
