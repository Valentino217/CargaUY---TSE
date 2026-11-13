package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.ITVEJB;
import uy.edu.fing.grupo07.CargaUY.service.PNCEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("VehiculoBean")
@ViewScoped
public class VehiculoBean implements Serializable {

    private static final long serialVersionUID = 1L;

    public record EspecificacionModelo(String modelo, int taraKg, int capacidadCargaKg) implements Serializable {}

    private static final Map<String, List<EspecificacionModelo>> CATALOGO_CAMIONES = new LinkedHashMap<>();

    static {
        CATALOGO_CAMIONES.put("Scania", List.of(
                new EspecificacionModelo("R450 6x2 Highline", 9600, 35400),
                new EspecificacionModelo("R540 6x4 Bitrén Forestal", 10800, 46200),
                new EspecificacionModelo("G410 6x2 Semirremolque", 9300, 35700),
                new EspecificacionModelo("P320 4x2 Distribución", 7800, 21200)
        ));
        CATALOGO_CAMIONES.put("Volvo", List.of(
                new EspecificacionModelo("FH 540 6x4 Globetrotter", 10500, 46500),
                new EspecificacionModelo("FH 460 6x2 Tractor", 9400, 35600),
                new EspecificacionModelo("FMX 460 6x4 Forestal / Vial", 10900, 34100),
                new EspecificacionModelo("VM 270 6x2 Rígido", 7600, 16400)
        ));
        CATALOGO_CAMIONES.put("Mercedes-Benz", List.of(
                new EspecificacionModelo("Actros 2548 LS 6x2", 9500, 35500),
                new EspecificacionModelo("Actros 2651 LS 6x4", 10400, 46600),
                new EspecificacionModelo("Axor 2544 6x2", 9100, 35900),
                new EspecificacionModelo("Atego 1726 4x2", 6200, 10800)
        ));
        CATALOGO_CAMIONES.put("Volkswagen", List.of(
                new EspecificacionModelo("Constellation 25.360 6x2", 8900, 36100),
                new EspecificacionModelo("Constellation 19.360 4x2", 7900, 32100),
                new EspecificacionModelo("Meteor 29.520 6x4", 10300, 46700),
                new EspecificacionModelo("Delivery 11.180", 3800, 7200)
        ));
        CATALOGO_CAMIONES.put("Iveco", List.of(
                new EspecificacionModelo("S-Way 480 6x2", 9400, 35600),
                new EspecificacionModelo("Stralis Hi-Way 480 6x2", 9300, 35700),
                new EspecificacionModelo("Tector 170E28", 5900, 11100)
        ));
        CATALOGO_CAMIONES.put("JAC / Sinotruk", List.of(
                new EspecificacionModelo("Sinotruk Howo A7 420 6x4", 9800, 35200),
                new EspecificacionModelo("Sinotruk Howo T7H 440 6x2", 9500, 35500),
                new EspecificacionModelo("JAC K7 430 6x2", 9200, 35800),
                new EspecificacionModelo("JAC HFC 1061", 3400, 6000)
        ));
    }

    @EJB
    private VehiculoEJB vehiculoEJB;

    @EJB
    private ITVEJB itvEJB;

    @EJB
    private PNCEJB pncEJB;

    private List<Vehiculo> vehiculos;
    private List<Vehiculo> vehiculosFiltrados;
    private List<Empresa> empresas;
    private List<ITV> itvs;
    private List<PNC> pncs;

    private Vehiculo vehiculo;
    private boolean editando;
    private int empresaId;
    private String numeroPermisoPnc;
    private LocalDate fechaVencimientoPnc;
    private LocalDate fechaVencimientoItv;
    private String origNumeroPermisoPnc;
    private LocalDate origFechaVencimientoPnc;
    private LocalDate origFechaVencimientoItv;

    @PostConstruct
    public void init() {
        String token = SesionActual.token();
        try {
            empresas = vehiculoEJB.listarEmpresasParaActor(token);
        } catch (Exception e) {
            empresas = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar las empresas: " + extraerMensaje(e));
        }
        cargarFlota();
        nuevo();
    }

    public void cargarFlota() {
        String token = SesionActual.token();
        try {
            vehiculos = vehiculoEJB.listarParaActor(token);
            itvs = itvEJB != null ? itvEJB.listarITVs(token) : new ArrayList<>();
            pncs = pncEJB != null ? pncEJB.listarPNCs(token) : new ArrayList<>();
        } catch (Exception e) {
            vehiculos = new ArrayList<>();
            itvs = new ArrayList<>();
            pncs = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo inicializar la flota: " + extraerMensaje(e));
        }
    }

    public void nuevo() {
        vehiculo = new Vehiculo();
        vehiculo.setEstadoHabilitacion(false);
        editando = false;
        numeroPermisoPnc = null;
        fechaVencimientoPnc = null;
        fechaVencimientoItv = null;
        origNumeroPermisoPnc = null;
        origFechaVencimientoPnc = null;
        origFechaVencimientoItv = null;

        if (empresas != null && !empresas.isEmpty()) {
            empresaId = empresas.get(0).getNroEmpresa();
        } else {
            empresaId = 0;
        }

        String primeraMarca = getMarcasDisponibles().get(0);
        vehiculo.setMarca(primeraMarca);
        onMarcaChange();
    }

    public List<String> getMarcasDisponibles() {
        return new ArrayList<>(CATALOGO_CAMIONES.keySet());
    }

    public List<String> getModelosDisponibles() {
        if (vehiculo == null || vehiculo.getMarca() == null) {
            return List.of();
        }
        List<EspecificacionModelo> specs = CATALOGO_CAMIONES.get(vehiculo.getMarca());
        if (specs == null) {
            // Si al editar tenía una marca personalizada previa
            if (vehiculo.getModelo() != null && !vehiculo.getModelo().isBlank()) {
                return List.of(vehiculo.getModelo());
            }
            return List.of();
        }
        List<String> nombres = new ArrayList<>();
        for (EspecificacionModelo s : specs) {
            nombres.add(s.modelo());
        }
        if (vehiculo.getModelo() != null && !vehiculo.getModelo().isBlank() && !nombres.contains(vehiculo.getModelo())) {
            nombres.add(vehiculo.getModelo());
        }
        return nombres;
    }

    public void onMarcaChange() {
        if (vehiculo == null || vehiculo.getMarca() == null) {
            return;
        }
        List<EspecificacionModelo> specs = CATALOGO_CAMIONES.get(vehiculo.getMarca());
        if (specs != null && !specs.isEmpty()) {
            EspecificacionModelo def = specs.get(0);
            vehiculo.setModelo(def.modelo());
            vehiculo.setPeso(def.taraKg());
            vehiculo.setCapacidadCarga(def.capacidadCargaKg());
        }
    }

    public void onModeloChange() {
        if (vehiculo == null || vehiculo.getMarca() == null || vehiculo.getModelo() == null) {
            return;
        }
        List<EspecificacionModelo> specs = CATALOGO_CAMIONES.get(vehiculo.getMarca());
        if (specs != null) {
            for (EspecificacionModelo s : specs) {
                if (s.modelo().equals(vehiculo.getModelo())) {
                    vehiculo.setPeso(s.taraKg());
                    vehiculo.setCapacidadCarga(s.capacidadCargaKg());
                    break;
                }
            }
        }
    }

    public void guardar() {
        try {
            Empresa empresaSeleccionada = null;
            if (empresas != null) {
                for (Empresa empresa : empresas) {
                    if (empresa.getNroEmpresa() == empresaId) {
                        empresaSeleccionada = empresa;
                        break;
                    }
                }
            }

            if (empresaSeleccionada == null) {
                throw new IllegalArgumentException("Debe seleccionar una empresa válida.");
            }

            vehiculo.setEmpresa(empresaSeleccionada);

            if (editando) {
                vehiculoEJB.modificar(SesionActual.token(), vehiculo);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Unidad Actualizada", "Los datos del vehículo " + vehiculo.getMatricula() + " fueron modificados.");
            } else {
                vehiculo.setEstadoHabilitacion(false);
                vehiculoEJB.crear(SesionActual.token(), vehiculo);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Unidad Registrada", "El vehículo " + vehiculo.getMatricula() + " fue dado de alta sin permisos (pendiente de habilitación por la autoridad).");
            }

            cargarFlota();
            nuevo();

        } catch (Exception e) {
            FacesContext.getCurrentInstance().validationFailed();
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "No se pudo guardar el vehículo",
                    extraerMensaje(e)
            );
        }
    }

    public LocalDate obtenerVencimientoItv(String matricula) {
        if (matricula == null) return null;
        if (vehiculos != null) {
            for (Vehiculo v : vehiculos) {
                if (v != null && matricula.equalsIgnoreCase(v.getMatricula()) && v.getItv() != null && !v.getItv().isEmpty()) {
                    LocalDate max = v.getItv().stream()
                            .filter(i -> i != null && i.getFechaVencimiento() != null)
                            .map(ITV::getFechaVencimiento)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    if (max != null) return max;
                }
            }
        }
        if (itvs == null) return null;
        return itvs.stream()
                .filter(i -> i.getVehiculo() != null && matricula.equalsIgnoreCase(i.getVehiculo().getMatricula()) && i.getFechaVencimiento() != null)
                .map(ITV::getFechaVencimiento)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public LocalDate obtenerVencimientoPnc(String matricula) {
        PNC ultimo = obtenerUltimoPnc(matricula);
        return ultimo != null ? ultimo.getFechaVencimiento() : null;
    }

    public String obtenerNumeroPermisoPnc(String matricula) {
        PNC ultimo = obtenerUltimoPnc(matricula);
        return ultimo != null ? trimOrNull(ultimo.getNumeroPermiso()) : null;
    }

    private PNC obtenerUltimoPnc(String matricula) {
        if (matricula == null) return null;
        if (vehiculos != null) {
            for (Vehiculo v : vehiculos) {
                if (v != null && matricula.equalsIgnoreCase(v.getMatricula()) && v.getPncs() != null && !v.getPncs().isEmpty()) {
                    PNC ultimo = v.getPncs().stream()
                            .filter(p -> p != null && p.getFechaVencimiento() != null)
                            .max(Comparator.comparing(PNC::getFechaVencimiento))
                            .orElse(null);
                    if (ultimo != null) return ultimo;
                }
            }
        }
        if (pncs == null) return null;
        return pncs.stream()
                .filter(p -> p.getVehiculo() != null && matricula.equalsIgnoreCase(p.getVehiculo().getMatricula()) && p.getFechaVencimiento() != null)
                .max(Comparator.comparing(PNC::getFechaVencimiento))
                .orElse(null);
    }

    private static String trimOrNull(String val) {
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }

    public String obtenerEstadoVigencia(LocalDate fechaVenc) {
        if (fechaVenc == null) {
            return "SIN CERTIFICADO";
        }
        LocalDate hoy = LocalDate.now();
        if (fechaVenc.isBefore(hoy)) {
            return "VENCIDO";
        }
        if (!fechaVenc.isAfter(hoy.plusDays(30))) {
            return "POR VENCER";
        }
        return "VIGENTE";
    }

    public String obtenerSeveridadVigencia(LocalDate fechaVenc) {
        String est = obtenerEstadoVigencia(fechaVenc);
        return switch (est) {
            case "VIGENTE" -> "success";
            case "POR VENCER" -> "warning";
            default -> "danger";
        };
    }

    public boolean tieneDocumentacionEnRegla(Vehiculo v) {
        if (v == null || !v.isEstadoHabilitacion()) return false;
        LocalDate itv = obtenerVencimientoItv(v.getMatricula());
        LocalDate pnc = obtenerVencimientoPnc(v.getMatricula());
        LocalDate hoy = LocalDate.now();
        return itv != null && !itv.isBefore(hoy) && pnc != null && !pnc.isBefore(hoy);
    }

    public long getTotalUnidades() {
        return vehiculos != null ? vehiculos.size() : 0;
    }

    public long getTotalEnRegla() {
        if (vehiculos == null) return 0;
        return vehiculos.stream().filter(this::tieneDocumentacionEnRegla).count();
    }

    public long getTotalConAlerta() {
        return getTotalUnidades() - getTotalEnRegla();
    }

    public boolean estaApto(Vehiculo v) {
        if (v == null || v.getMatricula() == null) return false;
        try {
            return vehiculoEJB.estaAptoParaOperar(SesionActual.token(), v.getMatricula());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean tieneGuiaEnCurso(Vehiculo v) {
        if (v == null || v.getMatricula() == null) return false;
        try {
            return vehiculoEJB.tieneGuiaEnCurso(SesionActual.token(), v.getMatricula());
        } catch (Exception e) {
            return false;
        }
    }

    public void editar(Vehiculo v) {
        if (v == null) return;
        if (tieneGuiaEnCurso(v)) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Unidad en Viaje",
                    "No se puede editar el vehículo " + v.getMatricula() + " porque tiene una guía de viaje EN_CURSO.");
            FacesContext.getCurrentInstance().validationFailed();
            return;
        }
        // Clonamos la instancia para no mutar la fila en vivo si el usuario cancela
        Vehiculo copia = new Vehiculo();
        copia.setMatricula(v.getMatricula());
        copia.setMarca(v.getMarca());
        copia.setModelo(v.getModelo());
        copia.setPeso(v.getPeso());
        copia.setCapacidadCarga(v.getCapacidadCarga());
        copia.setEstadoHabilitacion(v.isEstadoHabilitacion());
        copia.setEmpresa(v.getEmpresa());

        this.vehiculo = copia;
        this.editando = true;
        if (v.getEmpresa() != null) {
            this.empresaId = v.getEmpresa().getNroEmpresa();
        }
        this.fechaVencimientoItv = obtenerVencimientoItv(v.getMatricula());
        this.origFechaVencimientoItv = this.fechaVencimientoItv;
        PNC ultimoPnc = obtenerUltimoPnc(v.getMatricula());
        this.fechaVencimientoPnc = ultimoPnc != null ? ultimoPnc.getFechaVencimiento() : null;
        this.numeroPermisoPnc = ultimoPnc != null ? ultimoPnc.getNumeroPermiso() : null;
        this.origFechaVencimientoPnc = this.fechaVencimientoPnc;
        this.origNumeroPermisoPnc = this.numeroPermisoPnc;
    }

    public void eliminar(Vehiculo v) {
        try {
            vehiculoEJB.eliminar(SesionActual.token(), v);
            cargarFlota();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Unidad Eliminada",
                    "El vehículo " + v.getMatricula() + " fue eliminado del padrón."
            );

            if (this.vehiculo != null && this.vehiculo.getMatricula() != null
                    && this.vehiculo.getMatricula().equals(v.getMatricula())) {
                nuevo();
            }

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "No se puede eliminar la unidad",
                    extraerMensaje(e)
            );
        }
    }

    private String extraerMensaje(Throwable t) {
        Throwable curr = t;
        while (curr.getCause() != null && curr.getCause() != curr) {
            curr = curr.getCause();
        }
        return curr.getMessage() != null ? curr.getMessage() : t.getMessage();
    }

    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(severity, resumen, detalle));
        }
    }

    public int getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(int empresaId) {
        this.empresaId = empresaId;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }

    public List<Vehiculo> getVehiculosFiltrados() {
        return vehiculosFiltrados;
    }

    public void setVehiculosFiltrados(List<Vehiculo> vehiculosFiltrados) {
        this.vehiculosFiltrados = vehiculosFiltrados;
    }

    public List<Empresa> getEmpresas() {
        return empresas;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public boolean isEditando() {
        return editando;
    }

    public String getNumeroPermisoPnc() {
        return numeroPermisoPnc;
    }

    public void setNumeroPermisoPnc(String numeroPermisoPnc) {
        this.numeroPermisoPnc = numeroPermisoPnc;
    }

    public LocalDate getFechaVencimientoPnc() {
        return fechaVencimientoPnc;
    }

    public void setFechaVencimientoPnc(LocalDate fechaVencimientoPnc) {
        this.fechaVencimientoPnc = fechaVencimientoPnc;
    }

    public LocalDate getFechaVencimientoItv() {
        return fechaVencimientoItv;
    }

    public void setFechaVencimientoItv(LocalDate fechaVencimientoItv) {
        this.fechaVencimientoItv = fechaVencimientoItv;
    }
}
