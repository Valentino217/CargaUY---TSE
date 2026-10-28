package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;
import uy.edu.fing.grupo07.CargaUY.service.DashboardServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJBLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Managed Bean para las vistas públicas del portal (/portal/*):
 * inicio, validación pública de empresas e información pública agregada.
 */
@Named("portalPublicoBean")
@ViewScoped
public class PortalPublicoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private DashboardServiceLocal dashboardService;

    @EJB
    private VehiculoEJBLocal vehiculoEJB;

    @EJB
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    private String criterioBusquedaEmpresa;
    private List<Empresa> empresasPublicas;
    private List<Empresa> empresasResultado;
    private List<Vehiculo> vehiculosPublicos;
    private List<GuiaDeViaje> guiasPublicas;
    private Empresa empresaInspeccionada;
    private DashboardResumenDTO resumen;

    @PostConstruct
    public void init() {
        cargarDatosPublicos();
    }

    public void cargarDatosPublicos() {
        try {
            empresasPublicas = vehiculoEJB != null ? vehiculoEJB.listarEmpresas() : new ArrayList<>();
            vehiculosPublicos = vehiculoEJB != null ? vehiculoEJB.listarVehiculos() : new ArrayList<>();
            guiasPublicas = guiaDeViajeEJB != null ? guiaDeViajeEJB.listarGuias() : new ArrayList<>();
            resumen = dashboardService != null ? dashboardService.obtenerResumen() : null;
            empresasResultado = new ArrayList<>(empresasPublicas);
        } catch (Exception e) {
            empresasPublicas = new ArrayList<>();
            empresasResultado = new ArrayList<>();
            vehiculosPublicos = new ArrayList<>();
            guiasPublicas = new ArrayList<>();
        }
    }

    public void buscarEmpresa() {
        if (criterioBusquedaEmpresa == null || criterioBusquedaEmpresa.isBlank()) {
            empresasResultado = new ArrayList<>(empresasPublicas);
            return;
        }
        String q = criterioBusquedaEmpresa.trim().toLowerCase();
        empresasResultado = empresasPublicas.stream()
                .filter(e -> String.valueOf(e.getNroEmpresa()).contains(q)
                        || (e.getRazonSocial() != null && e.getRazonSocial().toLowerCase().contains(q))
                        || (e.getNombrePublico() != null && e.getNombrePublico().toLowerCase().contains(q)))
                .collect(Collectors.toList());

        if (empresasResultado.isEmpty()) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Sin coincidencias",
                            "No se encontraron empresas registradas con el criterio ingresado."));
        } else {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Consulta completada",
                            "Se encontraron " + empresasResultado.size() + " empresa(s)."));
        }
    }

    public void limpiarBusqueda() {
        this.criterioBusquedaEmpresa = null;
        this.empresasResultado = new ArrayList<>(empresasPublicas);
    }

    public void seleccionarEmpresa(Empresa e) {
        this.empresaInspeccionada = e;
    }

    public long contarVehiculosEmpresa(int nroEmpresa) {
        if (vehiculosPublicos == null) {
            return 0;
        }
        return vehiculosPublicos.stream()
                .filter(v -> v.getEmpresa() != null && v.getEmpresa().getNroEmpresa() == nroEmpresa)
                .count();
    }

    public long contarVehiculosHabilitadosEmpresa(int nroEmpresa) {
        if (vehiculosPublicos == null) {
            return 0;
        }
        return vehiculosPublicos.stream()
                .filter(v -> v.getEmpresa() != null && v.getEmpresa().getNroEmpresa() == nroEmpresa && v.isEstadoHabilitacion())
                .count();
    }

    public int getTotalEmpresasActivas() {
        if (empresasPublicas == null) {
            return 0;
        }
        return (int) empresasPublicas.stream().filter(Empresa::isActiva).count();
    }

    public int getTotalVehiculosRegistrados() {
        return vehiculosPublicos != null ? vehiculosPublicos.size() : 0;
    }

    public int getTotalViajesRegistrados() {
        return guiasPublicas != null ? guiasPublicas.size() : 0;
    }

    public long getTotalViajesEnCurso() {
        if (guiasPublicas == null) {
            return 0;
        }
        return guiasPublicas.stream().filter(g -> g.getEstado() == TipoEstado.EN_CURSO).count();
    }

    public long getTotalPesadas() {
        return resumen != null ? resumen.totalPesadas() : 0L;
    }

    public List<Map.Entry<TipoEstado, Long>> getGuiasPorEstadoEntries() {
        if (resumen == null || resumen.guiasPorEstado() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(resumen.guiasPorEstado().entrySet());
    }

    public String getCriterioBusquedaEmpresa() {
        return criterioBusquedaEmpresa;
    }

    public void setCriterioBusquedaEmpresa(String criterioBusquedaEmpresa) {
        this.criterioBusquedaEmpresa = criterioBusquedaEmpresa;
    }

    public List<Empresa> getEmpresasPublicas() {
        return empresasPublicas;
    }

    public List<Empresa> getEmpresasResultado() {
        return empresasResultado;
    }

    public List<Vehiculo> getVehiculosPublicos() {
        return vehiculosPublicos;
    }

    public List<GuiaDeViaje> getGuiasPublicas() {
        return guiasPublicas;
    }

    public Empresa getEmpresaInspeccionada() {
        return empresaInspeccionada;
    }

    public void setEmpresaInspeccionada(Empresa empresaInspeccionada) {
        this.empresaInspeccionada = empresaInspeccionada;
    }
}
