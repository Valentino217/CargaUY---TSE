package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
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

    public static boolean esEventoDescargo(RegistroAuditoriaDTO reg) {
        return reg != null && ("PRESENTAR_DESCARGO".equals(reg.accion()) || "EDITAR_DESCARGO".equals(reg.accion()));
    }

    private List<Integer> indicesEventosDescargo() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < historialAuditoria.size(); i++) {
            if (esEventoDescargo(historialAuditoria.get(i))) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * Devuelve el historial consolidado mostrando un único registro de descargo (con el último texto),
     * para evitar múltiples filas repetidas cuando el descargo fue editado.
     */
    public List<RegistroAuditoriaDTO> historialAuditoriaConsolidado() {
        List<Integer> indices = indicesEventosDescargo();
        if (indices.size() <= 1) {
            return historialAuditoria;
        }
        int ultimoIndice = indices.get(indices.size() - 1);
        RegistroAuditoriaDTO ultimo = historialAuditoria.get(ultimoIndice);
        List<RegistroAuditoriaDTO> consolidado = new ArrayList<>(historialAuditoria.size() - (indices.size() - 1));
        for (int i = 0; i < historialAuditoria.size(); i++) {
            RegistroAuditoriaDTO actual = historialAuditoria.get(i);
            if (!esEventoDescargo(actual)) {
                consolidado.add(actual);
            } else if (i == ultimoIndice) {
                consolidado.add(new RegistroAuditoriaDTO(
                        ultimo.id(),
                        ultimo.resolucion(),
                        ultimo.fecha(),
                        ultimo.timestampCreacion(),
                        ultimo.usuario(),
                        "PRESENTAR_DESCARGO",
                        ultimo.fundamento()
                ));
            }
        }
        return List.copyOf(consolidado);
    }

    /**
     * Indica si el descargo recibió una o más ediciones posteriores al envío inicial.
     */
    public boolean isDescargoEditado() {
        return indicesEventosDescargo().size() > 1;
    }

    /**
     * Indica si la fila indicada del historial corresponde al descargo consolidado y fue editada.
     */
    public boolean esDescargoEditado(RegistroAuditoriaDTO reg) {
        return esEventoDescargo(reg) && isDescargoEditado();
    }

    /**
     * Devuelve en orden cronológico todas las versiones anteriores del descargo (excluyendo la vigente).
     */
    public List<RegistroAuditoriaDTO> versionesAnterioresDescargo() {
        List<Integer> indices = indicesEventosDescargo();
        if (indices.size() <= 1) {
            return Collections.emptyList();
        }
        List<RegistroAuditoriaDTO> anteriores = new ArrayList<>(indices.size() - 1);
        for (int k = 0; k < indices.size() - 1; k++) {
            anteriores.add(historialAuditoria.get(indices.get(k)));
        }
        return List.copyOf(anteriores);
    }
}
