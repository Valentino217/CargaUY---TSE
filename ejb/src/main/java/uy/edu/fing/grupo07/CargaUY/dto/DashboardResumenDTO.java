package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO que consolida los datos agregados para el dashboard general de la Autoridad (Sección 3.6.2).
 */
public record DashboardResumenDTO(
        Map<TipoEstado, Long> guiasPorEstado,
        Map<EstadoInfraccion, Long> infraccionesPorEstado,
        Map<TipoInfraccion, Long> infraccionesPorTipo,
        long totalPesadas,
        long totalEmpresasHabilitadas,
        long totalVehiculosHabilitados,
        Map<String, Long> viajesPorPeriodo,
        Map<String, Double> volumenPorRubro,
        double tasaConfirmadasSobreDetectadas
) implements Serializable {

    public DashboardResumenDTO(
            Map<TipoEstado, Long> guiasPorEstado,
            Map<EstadoInfraccion, Long> infraccionesPorEstado,
            Map<TipoInfraccion, Long> infraccionesPorTipo,
            long totalPesadas
    ) {
        this(
                guiasPorEstado,
                infraccionesPorEstado,
                infraccionesPorTipo,
                totalPesadas,
                0L,
                0L,
                Map.of(),
                Map.of(),
                0.0
        );
    }

    public Map<TipoEstado, Long> getGuiasPorEstado() {
        return guiasPorEstado;
    }

    public Map<EstadoInfraccion, Long> getInfraccionesPorEstado() {
        return infraccionesPorEstado;
    }

    public Map<TipoInfraccion, Long> getInfraccionesPorTipo() {
        return infraccionesPorTipo;
    }

    public long getTotalPesadas() {
        return totalPesadas;
    }

    public long getTotalEmpresasHabilitadas() {
        return totalEmpresasHabilitadas;
    }

    public long getTotalVehiculosHabilitados() {
        return totalVehiculosHabilitados;
    }

    public Map<String, Long> getViajesPorPeriodo() {
        return viajesPorPeriodo;
    }

    public Map<String, Double> getVolumenPorRubro() {
        return volumenPorRubro;
    }

    public double getTasaConfirmadasSobreDetectadas() {
        return tasaConfirmadasSobreDetectadas;
    }
}
