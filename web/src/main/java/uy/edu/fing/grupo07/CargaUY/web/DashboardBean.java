package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;
import uy.edu.fing.grupo07.CargaUY.service.DashboardServiceLocal;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean para presentar métricas, filtros dinámicos y resúmenes ejecutivos en JSF/PrimeFaces (Sección 3.6.2).
 */
@Named("dashboardBean")
@ViewScoped
public class DashboardBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DashboardBean.class.getName());

    @EJB
    private DashboardServiceLocal dashboardService;

    private DashboardResumenDTO resumen;

    private String filtroRubro;
    private String filtroTipoCarga;
    private String filtroUbicacion;
    private LocalDate filtroFechaDesde;
    private LocalDate filtroFechaHasta;

    private List<String> rubrosDisponibles = new ArrayList<>();
    private List<String> tiposCargaDisponibles = new ArrayList<>();
    private List<String> ubicacionesDisponibles = new ArrayList<>();

    public DashboardBean() {
    }

    public DashboardBean(DashboardServiceLocal dashboardService) {
        this.dashboardService = dashboardService;
    }

    @PostConstruct
    public void inicializar() {
        try {
            String token = SesionActual.token();
            if (dashboardService != null) {
                this.rubrosDisponibles = dashboardService.listarRubrosDisponibles(token);
                this.tiposCargaDisponibles = dashboardService.listarTiposCargaDisponibles(token);
                this.ubicacionesDisponibles = dashboardService.listarUbicacionesDisponibles(token);
            }
            if (tieneFiltrosActivos()) {
                this.resumen = dashboardService.obtenerResumenFiltrado(
                        token,
                        filtroRubro,
                        filtroTipoCarga,
                        filtroUbicacion,
                        filtroFechaDesde,
                        filtroFechaHasta
                );
            } else {
                this.resumen = dashboardService.obtenerResumen(token);
            }
        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al obtener métricas del dashboard", excepcion);
        }
    }

    public void aplicarFiltros() {
        try {
            String token = SesionActual.token();
            if (tieneFiltrosActivos()) {
                this.resumen = dashboardService.obtenerResumenFiltrado(
                        token,
                        filtroRubro,
                        filtroTipoCarga,
                        filtroUbicacion,
                        filtroFechaDesde,
                        filtroFechaHasta
                );
            } else {
                this.resumen = dashboardService.obtenerResumen(token);
            }
        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al aplicar filtros del dashboard", excepcion);
        }
    }

    public void limpiarFiltros() {
        this.filtroRubro = null;
        this.filtroTipoCarga = null;
        this.filtroUbicacion = null;
        this.filtroFechaDesde = null;
        this.filtroFechaHasta = null;
        inicializar();
    }

    private java.util.Map<String, String> filtrosComoTexto() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("Rubro", filtroRubro == null || filtroRubro.isBlank() ? "Todos" : filtroRubro);
        m.put("Tipo de carga", filtroTipoCarga == null || filtroTipoCarga.isBlank() ? "Todos" : filtroTipoCarga);
        m.put("Ubicacion", filtroUbicacion == null || filtroUbicacion.isBlank() ? "Todas" : filtroUbicacion);
        m.put("Desde", filtroFechaDesde == null ? "-" : filtroFechaDesde.toString());
        m.put("Hasta", filtroFechaHasta == null ? "-" : filtroFechaHasta.toString());
        return m;
    }

    private void descargar(byte[] datos, String nombre, String contentType) throws java.io.IOException {
        jakarta.faces.context.FacesContext fc = jakarta.faces.context.FacesContext.getCurrentInstance();
        jakarta.faces.context.ExternalContext ec = fc.getExternalContext();
        ec.responseReset();
        ec.setResponseContentType(contentType);
        ec.setResponseContentLength(datos.length);
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
        ec.getResponseOutputStream().write(datos);
        fc.responseComplete();
    }

    public void exportarCsv() throws java.io.IOException {
        aplicarFiltros();
        descargar(ReporteDashboardExporter.generarCsv(resumen, filtrosComoTexto()),
                "reporte-dashboard-cargauy.csv", "text/csv;charset=UTF-8");
    }

    public void exportarPdf() throws java.io.IOException {
        aplicarFiltros();
        descargar(ReporteDashboardExporter.generarPdf(resumen, filtrosComoTexto()),
                "reporte-dashboard-cargauy.pdf", "application/pdf");
    }

    private boolean tieneFiltrosActivos() {
        return (filtroRubro != null && !filtroRubro.isBlank())
                || (filtroTipoCarga != null && !filtroTipoCarga.isBlank())
                || (filtroUbicacion != null && !filtroUbicacion.isBlank())
                || filtroFechaDesde != null
                || filtroFechaHasta != null;
    }

    public long getTotalPesadas() {
        if (resumen == null) {
            return 0L;
        }
        return resumen.totalPesadas();
    }

    public long getTotalEmpresasHabilitadas() {
        if (resumen == null) {
            return 0L;
        }
        return resumen.totalEmpresasHabilitadas();
    }

    public long getTotalVehiculosHabilitados() {
        if (resumen == null) {
            return 0L;
        }
        return resumen.totalVehiculosHabilitados();
    }

    public double getTasaConfirmadasSobreDetectadas() {
        if (resumen == null) {
            return 0.0;
        }
        return resumen.tasaConfirmadasSobreDetectadas();
    }

    public long getTotalCasosDetectados() {
        if (resumen == null || resumen.infraccionesPorEstado() == null) {
            return 0L;
        }
        return resumen.infraccionesPorEstado().values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    public long getTotalSancionesConfirmadas() {
        if (resumen == null || resumen.infraccionesPorEstado() == null) {
            return 0L;
        }
        return resumen.infraccionesPorEstado().getOrDefault(EstadoInfraccion.CONFIRMADA, 0L);
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

    public List<Map.Entry<String, Long>> getListaViajesPorPeriodo() {
        if (resumen == null || resumen.viajesPorPeriodo() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(resumen.viajesPorPeriodo().entrySet());
    }

    public List<Map.Entry<String, Double>> getListaVolumenPorRubro() {
        if (resumen == null || resumen.volumenPorRubro() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(resumen.volumenPorRubro().entrySet());
    }

    public DashboardResumenDTO getResumen() {
        return resumen;
    }

    public void setResumen(DashboardResumenDTO resumen) {
        this.resumen = resumen;
    }

    public String getFiltroRubro() {
        return filtroRubro;
    }

    public void setFiltroRubro(String filtroRubro) {
        this.filtroRubro = filtroRubro;
    }

    public String getFiltroTipoCarga() {
        return filtroTipoCarga;
    }

    public void setFiltroTipoCarga(String filtroTipoCarga) {
        this.filtroTipoCarga = filtroTipoCarga;
    }

    public String getFiltroUbicacion() {
        return filtroUbicacion;
    }

    public void setFiltroUbicacion(String filtroUbicacion) {
        this.filtroUbicacion = filtroUbicacion;
    }

    public LocalDate getFiltroFechaDesde() {
        return filtroFechaDesde;
    }

    public void setFiltroFechaDesde(LocalDate filtroFechaDesde) {
        this.filtroFechaDesde = filtroFechaDesde;
    }

    public LocalDate getFiltroFechaHasta() {
        return filtroFechaHasta;
    }

    public void setFiltroFechaHasta(LocalDate filtroFechaHasta) {
        this.filtroFechaHasta = filtroFechaHasta;
    }

    public List<String> getRubrosDisponibles() {
        return rubrosDisponibles;
    }

    public void setRubrosDisponibles(List<String> rubrosDisponibles) {
        this.rubrosDisponibles = rubrosDisponibles;
    }

    public List<String> getTiposCargaDisponibles() {
        return tiposCargaDisponibles;
    }

    public void setTiposCargaDisponibles(List<String> tiposCargaDisponibles) {
        this.tiposCargaDisponibles = tiposCargaDisponibles;
    }

    public List<String> getUbicacionesDisponibles() {
        return ubicacionesDisponibles;
    }

    public void setUbicacionesDisponibles(List<String> ubicacionesDisponibles) {
        this.ubicacionesDisponibles = ubicacionesDisponibles;
    }
}
