package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.ITVEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("ITVBean")
@ViewScoped
public class ITVBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private ITVEJB itvEJB;

    @EJB
    private VehiculoEJB vehiculoEJB;

    private List<ITV> itvs;
    private List<Vehiculo> vehiculos;
    private ITV itv;
    private boolean editando;

    private String matricula;

    @PostConstruct
    public void init() {
        vehiculos = vehiculoEJB.listarParaActor(SesionActual.token());
        cargarItvsAutorizadas();
        nuevo();
    }

    private void cargarItvsAutorizadas() {
        Set<String> mats = vehiculos == null ? Set.of()
                : vehiculos.stream().map(Vehiculo::getMatricula).filter(Objects::nonNull).collect(Collectors.toSet());
        itvs = itvEJB.listarITVs(SesionActual.token()).stream()
                .filter(i -> i.getVehiculo() != null && mats.contains(i.getVehiculo().getMatricula()))
                .toList();
    }

    public void nuevo() {
        itv = new ITV();
        matricula = null;
        editando = false;
    }

    public void guardar() {
        try {
            if (vehiculos == null || matricula == null || matricula.trim().isEmpty()) {
                throw new IllegalArgumentException("Debe seleccionar un vehículo válido");
            }

            Vehiculo vehiculoSeleccionado = null;
            for (Vehiculo v : vehiculos) {
                if (v.getMatricula() != null && v.getMatricula().equals(matricula)) {
                    vehiculoSeleccionado = v;
                    break;
                }
            }

            if (vehiculoSeleccionado == null) {
                throw new IllegalArgumentException("Vehículo no encontrado en el sistema");
            }

            itv.setVehiculo(vehiculoSeleccionado);

            if (editando) {
                itvEJB.modificar(SesionActual.token(), itv);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "ITV modificada correctamente");
            } else {
                itvEJB.crear(SesionActual.token(), itv);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "ITV registrada correctamente");
            }

            cargarItvsAutorizadas();
            nuevo();

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo guardar la ITV: " + e.getMessage()
            );
        }
    }

    public void editar(ITV itvSeleccionada) {
        this.itv = itvSeleccionada;
        this.editando = true;

        if (itvSeleccionada.getVehiculo() != null) {
            this.matricula = itvSeleccionada.getVehiculo().getMatricula();
        } else {
            this.matricula = null;
        }
    }

    public void eliminar(ITV itvEliminar) {
        try {
            if (itvEliminar == null || itvEliminar.getVehiculo() == null || vehiculos == null
                    || vehiculos.stream().noneMatch(v -> Objects.equals(v.getMatricula(), itvEliminar.getVehiculo().getMatricula()))) {
                throw new SecurityException("No tiene permisos para eliminar la ITV seleccionada.");
            }
            itvEJB.eliminar(SesionActual.token(), itvEliminar);
            cargarItvsAutorizadas();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "ITV eliminada correctamente"
            );

            if (this.itv != null && this.itv.getNroCertificado() == itvEliminar.getNroCertificado()) {
                nuevo();
            }

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar la ITV: " + e.getMessage()
            );
        }
    }

    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(severity, resumen, detalle)
        );
    }

    public List<ITV> getItvs() { return itvs; }
    public void setItvs(List<ITV> itvs) { this.itvs = itvs; }

    public List<Vehiculo> getVehiculos() { return vehiculos; }
    public void setVehiculos(List<Vehiculo> vehiculos) { this.vehiculos = vehiculos; }

    public ITV getItv() { return itv; }
    public void setItv(ITV itv) { this.itv = itv; }

    public boolean isEditando() { return editando; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
}
