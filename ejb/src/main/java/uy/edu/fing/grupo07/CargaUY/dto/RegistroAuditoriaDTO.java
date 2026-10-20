package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO inmutable para la pista de auditoría inmutable de fiscalización (R005 / AC004).
 */
public record RegistroAuditoriaDTO(
        Long id,
        String resolucion,
        LocalDate fecha,
        LocalDateTime timestampCreacion,
        String usuario,
        String accion,
        String fundamento
) implements Serializable {
}
