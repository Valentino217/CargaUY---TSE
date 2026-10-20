package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO que consolida los datos agregados para el dashboard general de la Autoridad.
 */
public record DashboardResumenDTO(
        Map<TipoEstado, Long> guiasPorEstado,
        Map<EstadoInfraccion, Long> infraccionesPorEstado,
        Map<TipoInfraccion, Long> infraccionesPorTipo,
        long totalPesadas
) implements Serializable {
}
