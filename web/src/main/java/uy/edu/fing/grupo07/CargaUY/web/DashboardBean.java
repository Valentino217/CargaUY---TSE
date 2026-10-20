package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;
import uy.edu.fing.grupo07.CargaUY.service.DashboardServiceLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean para presentar métricas y resúmenes ejecutivos en JSF/PrimeFaces.
 */
@Named("dashboardBean")
@RequestScoped
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DashboardBean.class.getName());

    @EJB
    private DashboardServiceLocal dashboardService;

    private DashboardResumenDTO resumen;

    public DashboardBean() {
    }

    public DashboardBean(DashboardServiceLocal dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostConstruct
    public void inicializar() {
        try {
            this.resumen = dashboardService.obtenerResumen();
        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al obtener métricas del dashboard", excepcion);
        }
    }

    public long getTotalPesadas() {
        if (resumen == null) {
            return 0L;
        }
        return resumen.totalPesadas();
    }

    public List<Map.Entry<TipoEstado, Long>> getListaGuiasPorEstado() {
        if (resumen == null) {
            return new ArrayList<>();
        }
        Map<TipoEstado, Long> mapa = resumen.guiasPorEstado();
        if (mapa == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(mapa.entrySet());
    }

    public List<Map.Entry<EstadoInfraccion, Long>> getListaInfraccionesPorEstado() {
        if (resumen == null) {
            return new ArrayList<>();
        }
        Map<EstadoInfraccion, Long> mapa = resumen.infraccionesPorEstado();
        if (mapa == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(mapa.entrySet());
    }

    public List<Map.Entry<TipoInfraccion, Long>> getListaInfraccionesPorTipo() {
        if (resumen == null) {
            return new ArrayList<>();
        }
        Map<TipoInfraccion, Long> mapa = resumen.infraccionesPorTipo();
        if (mapa == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(mapa.entrySet());
    }

    public DashboardResumenDTO getResumen() {
        return resumen;
    }

    public void setResumen(DashboardResumenDTO resumen) {
        this.resumen = resumen;
    }
}
