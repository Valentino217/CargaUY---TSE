package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.util.ArrayList;
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
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
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

    private List<Vehiculo> vehiculos;
    private List<Empresa> empresas;

    private Vehiculo vehiculo;
    private boolean editando;
    private int empresaId;

    @PostConstruct
    public void init() {
        try {
            vehiculos = vehiculoEJB.listarParaActor(SesionActual.token());
            empresas = vehiculoEJB.listarEmpresasParaActor(SesionActual.token());
        } catch (Exception e) {
            vehiculos = new ArrayList<>();
            empresas = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo inicializar la flota: " + extraerMensaje(e));
        }
        nuevo();
    }

    public void nuevo() {
        vehiculo = new Vehiculo();
        vehiculo.setEstadoHabilitacion(true);
        editando = false;

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
                vehiculoEJB.crear(SesionActual.token(), vehiculo);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Unidad Registrada", "El vehículo " + vehiculo.getMatricula() + " fue dado de alta correctamente.");
            }

            vehiculos = vehiculoEJB.listarParaActor(SesionActual.token());
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

    public boolean estaApto(Vehiculo v) {
        if (v == null || v.getMatricula() == null) return false;
        try {
            return vehiculoEJB.estaAptoParaOperar(v.getMatricula());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean tieneGuiaEnCurso(Vehiculo v) {
        if (v == null || v.getMatricula() == null) return false;
        try {
            return vehiculoEJB.tieneGuiaEnCurso(v.getMatricula());
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
    }

    public void eliminar(Vehiculo v) {
        try {
            vehiculoEJB.eliminar(SesionActual.token(), v);
            vehiculos = vehiculoEJB.listarParaActor(SesionActual.token());

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
}
