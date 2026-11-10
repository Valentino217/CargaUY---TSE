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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Managed Bean para las vistas públicas del portal (/portal/*):
 * inicio, validación pública de empresas e información pública agregada con anonimización (Sección 4.2.6 - Ley 18.331).
 */
@Named("portalPublicoBean")
@ViewScoped
public class PortalPublicoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Umbral mínimo de k-anonimato (Sección 4.2.6 / Ley Nº 18.331 de Protección de Datos Personales):
     * Ninguna agrupación estadística pública se expone con menos de k = 3 registros para impedir
     * la re-identificación indirecta de una empresa, vehículo o chofer en particular.
     */
    public static final int UMBRAL_MINIMO_AGREGACION_K = 3;

    public record EstadisticaCorredorPublico(
            String corredor,
            String rubro,
            long cantidadViajes,
            double volumenTotal
    ) implements Serializable {
        public String getCorredor() {
            return corredor;
        }

        public String getRubro() {
            return rubro;
        }

        public long getCantidadViajes() {
            return cantidadViajes;
        }

        public double getVolumenTotal() {
            return volumenTotal;
        }
    }

    public record EstadisticaFlotaPublica(
            String categoriaCapacidad,
            long cantidadUnidades,
            long unidadesHabilitadas,
            double capacidadPromedioKg
    ) implements Serializable {
        public String getCategoriaCapacidad() {
            return categoriaCapacidad;
        }

        public long getCantidadUnidades() {
            return cantidadUnidades;
        }

        public long getUnidadesHabilitadas() {
            return unidadesHabilitadas;
        }

        public double getCapacidadPromedioKg() {
            return capacidadPromedioKg;
        }
    }

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
    private transient List<EstadisticaCorredorPublico> actividadOrdenable;
    private transient List<EstadisticaFlotaPublica> flotaOrdenable;
    private transient List<Map.Entry<TipoEstado, Long>> estadosOrdenables;

    @PostConstruct
    public void init() {
        cargarDatosPublicos();
    }

    public void cargarDatosPublicos() {
        actividadOrdenable = null;
        flotaOrdenable = null;
        estadosOrdenables = null;
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

    public int getUmbralMinimoAgregacion() {
        return UMBRAL_MINIMO_AGREGACION_K;
    }

    /**
     * Devuelve la actividad de transporte agregada aplicando k-anonimato (k >= 3)
     * sin exponer identificadores de guía, empresa, matrícula ni chofer.
     */
    public List<EstadisticaCorredorPublico> getEstadisticasActividadAgregada() {
        if (actividadOrdenable == null) {
            actividadOrdenable = calcularEstadisticasActividadAgregada();
        }
        return actividadOrdenable;
    }

    private List<EstadisticaCorredorPublico> calcularEstadisticasActividadAgregada() {
        List<EstadisticaCorredorPublico> resultado = new ArrayList<>();
        if (guiasPublicas == null || guiasPublicas.size() < UMBRAL_MINIMO_AGREGACION_K) {
            return resultado;
        }

        Map<String, List<GuiaDeViaje>> porRubro = new LinkedHashMap<>();
        for (GuiaDeViaje g : guiasPublicas) {
            if (g == null) {
                continue;
            }
            String rubro = (g.getRubroCliente() != null && !g.getRubroCliente().isBlank())
                    ? g.getRubroCliente().trim()
                    : "General";
            porRubro.computeIfAbsent(rubro, k -> new ArrayList<>()).add(g);
        }

        List<GuiaDeViaje> residuales = new ArrayList<>();
        for (Map.Entry<String, List<GuiaDeViaje>> entry : porRubro.entrySet()) {
            List<GuiaDeViaje> grupo = entry.getValue();
            if (grupo.size() >= UMBRAL_MINIMO_AGREGACION_K) {
                double volumen = grupo.stream().mapToDouble(GuiaDeViaje::getVolumenCarga).sum();
                resultado.add(new EstadisticaCorredorPublico(
                        "Red Nacional Consolidada",
                        entry.getKey(),
                        grupo.size(),
                        Math.round(volumen * 100.0) / 100.0
                ));
            } else {
                residuales.addAll(grupo);
            }
        }

        if (residuales.size() >= UMBRAL_MINIMO_AGREGACION_K) {
            double volumenResidual = residuales.stream().mapToDouble(GuiaDeViaje::getVolumenCarga).sum();
            resultado.add(new EstadisticaCorredorPublico(
                    "Red Nacional Consolidada",
                    "Otros Rubros (Agrupado por umbral k < " + UMBRAL_MINIMO_AGREGACION_K + ")",
                    residuales.size(),
                    Math.round(volumenResidual * 100.0) / 100.0
            ));
        } else if (!residuales.isEmpty() && resultado.isEmpty()) {
            double volumenTotal = guiasPublicas.stream().mapToDouble(GuiaDeViaje::getVolumenCarga).sum();
            resultado.add(new EstadisticaCorredorPublico(
                    "Red Nacional Consolidada",
                    "Consolidado Nacional (k ≥ " + UMBRAL_MINIMO_AGREGACION_K + ")",
                    guiasPublicas.size(),
                    Math.round(volumenTotal * 100.0) / 100.0
            ));
        }

        return resultado;
    }

    /**
     * Devuelve estadísticas agregadas del padrón de vehículos por rango de capacidad aplicando k-anonimato (k >= 3),
     * sin exponer matrículas individuales ni razón social titular.
     */
    public List<EstadisticaFlotaPublica> getEstadisticasFlotaAgregada() {
        if (flotaOrdenable == null) {
            flotaOrdenable = calcularEstadisticasFlotaAgregada();
        }
        return flotaOrdenable;
    }

    private List<EstadisticaFlotaPublica> calcularEstadisticasFlotaAgregada() {
        List<EstadisticaFlotaPublica> resultado = new ArrayList<>();
        if (vehiculosPublicos == null || vehiculosPublicos.size() < UMBRAL_MINIMO_AGREGACION_K) {
            return resultado;
        }

        Map<String, List<Vehiculo>> porRango = new LinkedHashMap<>();
        porRango.put("Transporte Liviano (hasta 10 t)", new ArrayList<>());
        porRango.put("Transporte Mediano (10 a 20 t)", new ArrayList<>());
        porRango.put("Transporte Pesado", new ArrayList<>());

        for (Vehiculo v : vehiculosPublicos) {
            if (v == null) {
                continue;
            }
            String categoria;
            if (v.getCapacidadCarga() < 10000) {
                categoria = "Transporte Liviano (hasta 10 t)";
            } else if (v.getCapacidadCarga() <= 20000) {
                categoria = "Transporte Mediano (10 a 20 t)";
            } else {
                categoria = "Transporte Pesado";
            }
            porRango.get(categoria).add(v);
        }

        List<Vehiculo> residuales = new ArrayList<>();
        for (Map.Entry<String, List<Vehiculo>> entry : porRango.entrySet()) {
            List<Vehiculo> grupo = entry.getValue();
            if (grupo.size() >= UMBRAL_MINIMO_AGREGACION_K) {
                long habilitados = grupo.stream().filter(Vehiculo::isEstadoHabilitacion).count();
                double promedio = grupo.stream().mapToInt(Vehiculo::getCapacidadCarga).average().orElse(0.0);
                resultado.add(new EstadisticaFlotaPublica(
                        entry.getKey(),
                        grupo.size(),
                        habilitados,
                        Math.round(promedio * 100.0) / 100.0
                ));
            } else if (!grupo.isEmpty()) {
                residuales.addAll(grupo);
            }
        }

        if (residuales.size() >= UMBRAL_MINIMO_AGREGACION_K) {
            long habilitados = residuales.stream().filter(Vehiculo::isEstadoHabilitacion).count();
            double promedio = residuales.stream().mapToInt(Vehiculo::getCapacidadCarga).average().orElse(0.0);
            resultado.add(new EstadisticaFlotaPublica(
                    "Otras Categorías (Agrupado por umbral k < " + UMBRAL_MINIMO_AGREGACION_K + ")",
                    residuales.size(),
                    habilitados,
                    Math.round(promedio * 100.0) / 100.0
            ));
        } else if (!residuales.isEmpty() && resultado.isEmpty()) {
            long habilitados = vehiculosPublicos.stream().filter(Vehiculo::isEstadoHabilitacion).count();
            double promedio = vehiculosPublicos.stream().mapToInt(Vehiculo::getCapacidadCarga).average().orElse(0.0);
            resultado.add(new EstadisticaFlotaPublica(
                    "Flota Nacional Consolidada (k ≥ " + UMBRAL_MINIMO_AGREGACION_K + ")",
                    vehiculosPublicos.size(),
                    habilitados,
                    Math.round(promedio * 100.0) / 100.0
            ));
        }

        return resultado;
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

    public int getTotalEmpresasRegistradas() {
        return getTotalEmpresasActivas();
    }

    public int getTotalViajes() {
        return getTotalViajesRegistrados();
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
        if (estadosOrdenables == null) {
            estadosOrdenables = new ArrayList<>(resumen.guiasPorEstado().entrySet());
        }
        return estadosOrdenables;
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

    public void setEmpresasPublicas(List<Empresa> empresasPublicas) {
        this.empresasPublicas = empresasPublicas;
    }

    public List<Empresa> getEmpresasResultado() {
        return empresasResultado;
    }

    public void setEmpresasResultado(List<Empresa> empresasResultado) {
        this.empresasResultado = empresasResultado;
    }

    public List<Vehiculo> getVehiculosPublicos() {
        return vehiculosPublicos;
    }

    public void setVehiculosPublicos(List<Vehiculo> vehiculosPublicos) {
        this.vehiculosPublicos = vehiculosPublicos;
        flotaOrdenable = null;
    }

    public List<GuiaDeViaje> getGuiasPublicas() {
        return guiasPublicas;
    }

    public void setGuiasPublicas(List<GuiaDeViaje> guiasPublicas) {
        this.guiasPublicas = guiasPublicas;
        actividadOrdenable = null;
    }

    public Empresa getEmpresaInspeccionada() {
        return empresaInspeccionada;
    }

    public void setEmpresaInspeccionada(Empresa empresaInspeccionada) {
        this.empresaInspeccionada = empresaInspeccionada;
    }

    public DashboardResumenDTO getResumen() {
        return resumen;
    }

    public void setResumen(DashboardResumenDTO resumen) {
        this.resumen = resumen;
        estadosOrdenables = null;
    }
}
