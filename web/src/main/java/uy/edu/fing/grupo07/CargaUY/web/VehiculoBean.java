package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable; // <--- OBLIGATORIO para ViewScoped
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.view.ViewScoped; // <--- CAMBIAR DE RequestScoped A ViewScoped
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("VehiculoBean")
@ViewScoped // <--- Permite mantener el estado del formulario entre AJAX requests
public class VehiculoBean implements Serializable { // <--- Implementar Serializable
	
    private static final long serialVersionUID = 1L;

    @EJB
    private VehiculoEJB vehiculoEJB;

    private List<Vehiculo> vehiculos;
    private List<Empresa> empresas;

    private Vehiculo vehiculo;

    private boolean editando;
    
    private int empresaId;

    public int getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(int empresaId) {
        this.empresaId = empresaId;
    }

    @PostConstruct
    public void init() {
        vehiculos = vehiculoEJB.listarVehiculos();
        empresas = vehiculoEJB.listarEmpresas();
        nuevo();
    }

    public void nuevo() {
        vehiculo = new Vehiculo();
        editando = false;
        empresaId = 0;
    }
    
    public void guardar() {
        try {
            Empresa empresaSeleccionada = null;

            for (Empresa empresa : empresas) {
                if (empresa.getNroEmpresa() == empresaId) {
                    empresaSeleccionada = empresa;
                    break;
                }
            }

            if (empresaSeleccionada == null) {
                throw new IllegalArgumentException("Empresa no encontrada");
            }

            vehiculo.setEmpresa(empresaSeleccionada);
            
            if (editando) {
                // Si estaba editando, llama a modificar
                vehiculoEJB.modificar(vehiculo);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Vehículo modificado correctamente");
            } else {
                // Si era nuevo, llama a crear
                vehiculoEJB.crear(vehiculo);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Vehículo registrado correctamente");
            }

            vehiculos = vehiculoEJB.listarVehiculos();
            nuevo(); // Limpia el formulario para el siguiente registro

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo guardar el vehículo: " + e.getMessage()
            );
        }
    }
    
    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
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
        // Cargar también la empresa asociada en el combo
        if (vehiculo.getEmpresa() != null) {
            this.empresaId = vehiculo.getEmpresa().getNroEmpresa();
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

            if (this.vehiculo != null && this.vehiculo.getMatricula() != null 
                    && this.vehiculo.getMatricula().equals(vehiculo.getMatricula())) {
                nuevo();
            }

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar el vehículo"
            );
        }
    }

    // GETTERS Y SETTERS
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