package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO inmutable (record) para el registro y consulta de pesadas en balanzas de control.
 */
public record PesadaBalanzaDTO(
        Integer idPesada,
        LocalDate fecha,
        LocalTime hora,
        int pesoRegistrado,
        String matricula,
        Long guiaId
) implements Serializable {
}
