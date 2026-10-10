package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO inmutable (record) para el reporte de incidentes en ruta por parte del chofer.
 */
public record ReportarIncidenteDTO(
        String uuid,
        Long guiaId,
        LocalDateTime tiempo,
        double latitud,
        double longitud,
        String foto,
        String descripcion
) implements Serializable {
}
