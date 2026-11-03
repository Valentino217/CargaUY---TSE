package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * DTO inmutable para transferir el estado, detalle y pista completa de auditoría de un caso de infracción.
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
        RegistroAuditoriaDTO auditoria,
        List<RegistroAuditoriaDTO> historialAuditoria
) implements Serializable {

    public InfraccionDTO {
        historialAuditoria = historialAuditoria != null
                ? List.copyOf(historialAuditoria)
                : (auditoria != null ? List.of(auditoria) : Collections.emptyList());
    }

    public InfraccionDTO(
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
    ) {
        this(
                idCaso,
                fecha,
                tipo,
                estado,
                evidencia,
                fundamento,
                fechaResolucion,
                guiaId,
                matricula,
                nombreEmpresa,
                textoDescargo,
                fechaDescargo,
                auditoria,
                auditoria != null ? List.of(auditoria) : Collections.emptyList()
        );
    }
}
