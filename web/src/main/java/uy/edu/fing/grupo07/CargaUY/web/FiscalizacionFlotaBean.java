package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.ITVEJB;
import uy.edu.fing.grupo07.CargaUY.service.PNCEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Managed Bean para /funcionario/fiscalizacion-vehiculos.xhtml.
 * Vista unificada de flotas, vigencias de ITV y PNC con alertas de vencimiento y renovación.
 */
@Named("fiscalizacionFlotaBean")
@ViewScoped
public class FiscalizacionFlotaBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private VehiculoEJB vehiculoEJB;

    @EJB
    private ITVEJB itvEJB;

    @EJB
    private PNCEJB pncEJB;

    private List<Vehiculo> vehiculos;
    private List<Vehiculo> vehiculosFiltrados;
    private List<ITV> itvs;
    private List<PNC> pncs;

    private Vehiculo vehiculoSeleccionado;
    private LocalDate nuevaFechaItv;
    private LocalDate nuevaFechaPnc;

    @PostConstruct
    public void init() {
        cargarFlota();
    }

    public void cargarFlota() {
        try {
            vehiculos = vehiculoEJB.listarVehiculos();
            itvs = itvEJB.listarITVs();
            pncs = pncEJB.listarPNCs();
        } catch (Exception e) {
            vehiculos = new ArrayList<>();
            itvs = new ArrayList<>();
            pncs = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo cargar la información de flota: " + e.getMessage());
        }
    }

    public LocalDate obtenerVencimientoItv(String matricula) {
        if (itvs == null || matricula == null) return null;
        Optional<ITV> ultimo = itvs.stream()
                .filter(i -> i.getVehiculo() != null && matricula.equals(i.getVehiculo().getMatricula()) && i.getFechaVencimiento() != null)
                .max(Comparator.comparing(ITV::getFechaVencimiento));
        return ultimo.map(ITV::getFechaVencimiento).orElse(null);
    }

    public LocalDate obtenerVencimientoPnc(String matricula) {
        if (pncs == null || matricula == null) return null;
        Optional<PNC> ultimo = pncs.stream()
                .filter(p -> p.getVehiculo() != null && matricula.equals(p.getVehiculo().getMatricula()) && p.getFechaVencimiento() != null)
                .max(Comparator.comparing(PNC::getFechaVencimiento));
        return ultimo.map(PNC::getFechaVencimiento).orElse(null);
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

    public void seleccionarVehiculo(Vehiculo v) {
        this.vehiculoSeleccionado = v;
        this.nuevaFechaItv = LocalDate.now().plusYears(1);
        this.nuevaFechaPnc = LocalDate.now().plusYears(1);
    }

    public void registrarRenovacionItv() {
        if (vehiculoSeleccionado == null || nuevaFechaItv == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Seleccione una fecha de vencimiento válida para la ITV.");
            return;
        }
        try {
            ITV nuevo = new ITV(nuevaFechaItv, vehiculoSeleccionado);
            itvEJB.crear(nuevo);
            cargarFlota();
            boolean reactivado = actualizarHabilitacionSiDocumentacionCompleta(vehiculoSeleccionado.getMatricula());
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "ITV Registrada",
                    "Se emitió nuevo certificado ITV para la unidad " + vehiculoSeleccionado.getMatricula()
                            + (reactivado ? " y se reactivó su habilitación operativa." : "."));
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al registrar ITV", e.getMessage());
        }
    }

    public void registrarRenovacionPnc() {
        if (vehiculoSeleccionado == null || nuevaFechaPnc == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Seleccione una fecha de vencimiento válida para el PNC.");
            return;
        }
        try {
            PNC nuevo = new PNC(nuevaFechaPnc, vehiculoSeleccionado);
            pncEJB.crear(nuevo);
            cargarFlota();
            boolean reactivado = actualizarHabilitacionSiDocumentacionCompleta(vehiculoSeleccionado.getMatricula());
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "PNC Registrado",
                    "Se emitió nuevo Permiso Nacional de Circulación para la unidad " + vehiculoSeleccionado.getMatricula()
                            + (reactivado ? " y se reactivó su habilitación operativa." : "."));
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al registrar PNC", e.getMessage());
        }
    }

    public void alternarHabilitacionBase(Vehiculo v) {
        if (v == null) return;
        try {
            boolean nuevoEstado = !v.isEstadoHabilitacion();
            v.setEstadoHabilitacion(nuevoEstado);
            vehiculoEJB.modificar(v);
            cargarFlota();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Estado de Unidad Actualizado",
                    "La unidad " + v.getMatricula() + " quedó " + (nuevoEstado ? "HABILITADA" : "SUSPENDIDA") + ".");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo cambiar el estado del vehículo: " + e.getMessage());
        }
    }

    private boolean actualizarHabilitacionSiDocumentacionCompleta(String matricula) {
        if (matricula == null) return false;
        LocalDate itv = obtenerVencimientoItv(matricula);
        LocalDate pnc = obtenerVencimientoPnc(matricula);
        LocalDate hoy = LocalDate.now();
        if (itv != null && !itv.isBefore(hoy) && pnc != null && !pnc.isBefore(hoy)) {
            Vehiculo actual = vehiculoEJB.buscar(matricula);
            if (actual != null && !actual.isEstadoHabilitacion()) {
                actual.setEstadoHabilitacion(true);
                vehiculoEJB.modificar(actual);
                this.vehiculoSeleccionado = actual;
                cargarFlota();
                return true;
            }
        }
        return false;
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

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
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

    public List<ITV> getItvs() {
        return itvs;
    }

    public List<PNC> getPncs() {
        return pncs;
    }

    public Vehiculo getVehiculoSeleccionado() {
        return vehiculoSeleccionado;
    }

    public LocalDate getNuevaFechaItv() {
        return nuevaFechaItv;
    }

    public void setNuevaFechaItv(LocalDate nuevaFechaItv) {
        this.nuevaFechaItv = nuevaFechaItv;
    }

    public LocalDate getNuevaFechaPnc() {
        return nuevaFechaPnc;
    }

    public void setNuevaFechaPnc(LocalDate nuevaFechaPnc) {
        this.nuevaFechaPnc = nuevaFechaPnc;
    }
}
