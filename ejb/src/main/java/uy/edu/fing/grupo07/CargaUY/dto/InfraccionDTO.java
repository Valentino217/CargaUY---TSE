package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO inmutable para transferir el estado y detalle de un caso de infracción.
 */
public record InfraccionDTO(
        int idCaso,
        LocalDate fecha,
        TipoInfraccion tipo,
        EstadoInfraccion estado,
        String evidencia,
        String fundamento,
        LocalDate fechaResolucion,
        Long guiaId,
        String matricula,
        String nombreEmpresa,
        String textoDescargo,
        LocalDate fechaDescargo,
        RegistroAuditoriaDTO auditoria
) implements Serializable {
}
