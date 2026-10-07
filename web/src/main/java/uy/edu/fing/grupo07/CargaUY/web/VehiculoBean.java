package uy.edu.fing.grupo07.CargaUY.web;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("VehiculoBean")
@RequestScoped
public class VehiculoBean {
	
	@EJB
    private VehiculoEJB vehiculoEJB;

    private List<Vehiculo> vehiculos;
    private List<Empresa> empresas;

    private Vehiculo vehiculo;

    private boolean editando;

    @PostConstruct
    public void init() {
        vehiculos = vehiculoEJB.listarVehiculos();
        empresas = vehiculoEJB.listarEmpresas();
    }
    // =========================
    // ALTA vehiculo
    // =========================

    public void nuevo() {
    	//lo llama el usuario desde la pagina
        vehiculo = new Vehiculo();
        editando = false;
    }
    
    public void guardar() {
        try {
            vehiculoEJB.crear(vehiculo);
            
            //se llama al EJB con los datos que el usuario cargo en el formulario

            vehiculos = vehiculoEJB.listarVehiculos();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "Vehículo registrado correctamente"
            );

            vehiculo = new Vehiculo();
            //queda el sistema listo para agregar un nuevo vehiculo

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo registrar el vehículo"
            );
        }
    }
    
    private void mostrarMensaje(FacesMessage.Severity severity,String resumen, String detalle) 
    {

        FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(severity, resumen, detalle)
        );
    }
    
 // =========================
    // MODIFICACIÓN
    // =========================

    public void editar(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
        this.editando = true;
    }

    public void modificar() {
        try {
            vehiculoEJB.modificar(vehiculo);

            vehiculos = vehiculoEJB.listarVehiculos();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "Vehículo modificado correctamente"
            );

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo modificar el vehículo"
            );
        }
    }
    
    // =========================
    // BAJA
    // =========================

    public void eliminar(Vehiculo vehiculo) {
        try {
            vehiculoEJB.eliminar(vehiculo);

            vehiculos = vehiculoEJB.listarVehiculos();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "Vehículo eliminado correctamente"
            );

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar el vehículo"
            );
        }
    }

    
    // =========================
    // GETTERS Y SETTERS
    // =========================

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
