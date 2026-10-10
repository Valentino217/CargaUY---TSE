package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.PNCEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB; // <-- Asegúrate de importar tu EJB de vehículos

@Named("PNCBean")
@ViewScoped 
public class PNCBean implements Serializable { 
    
    private static final long serialVersionUID = 1L;

    @EJB
    private PNCEJB pncEJB;

    @EJB // <-- Inyectamos el servicio para poder rellenar la lista de vehículos disponibles
    private VehiculoEJB vehiculoEJB; 

    private List<PNC> pncs;
    private List<Vehiculo> vehiculos;
    private PNC pnc;
    private boolean editando;
    
    private String matricula;

    @PostConstruct
    public void init() {
        pncs = pncEJB.listarPNCs();
        vehiculos = vehiculoEJB.listarVehiculos(); // <-- CORRECCIÓN: Cargamos los vehículos de la BD
        nuevo();
    }

    public void nuevo() {
        pnc = new PNC();
        matricula = null; // Limpiamos la selección del combo
        editando = false;
    }
    
    public void guardar() {
        try {
            if (vehiculos == null || matricula == null || matricula.trim().isEmpty()) {
                throw new IllegalArgumentException("Debe seleccionar un vehículo válido");
            }

            Vehiculo vehiculoSeleccionado = null;

            for (Vehiculo vehiculo : vehiculos) {
                // CORRECCIÓN: Uso estricto de .equals() para comparar cadenas de texto
                if (vehiculo.getMatricula() != null && vehiculo.getMatricula().equals(matricula)) {
                    vehiculoSeleccionado = vehiculo;
                    break;
                }
            }

            if (vehiculoSeleccionado == null) {
                throw new IllegalArgumentException("Vehículo no encontrado en el sistema");
            }

            pnc.setVehiculo(vehiculoSeleccionado);
            
            if (editando) {
                pncEJB.modificar(pnc);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "PNC modificado correctamente");
            } else {
                pncEJB.crear(pnc);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "PNC registrado correctamente");
            }

            pncs = pncEJB.listarPNCs();
            nuevo(); // Limpia el formulario y la matrícula para el siguiente registro

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo guardar el PNC: " + e.getMessage()
            );
        }
    }
    
    public void editar(PNC pncSeleccionado) {
        this.pnc = pncSeleccionado;
        this.editando = true;
        
        // CORRECCIÓN: Precargamos la matrícula en el formulario para que el combo se posicione correctamente
        if (pncSeleccionado.getVehiculo() != null) {
            this.matricula = pncSeleccionado.getVehiculo().getMatricula();
        } else {
            this.matricula = null;
        }
    }

    public void eliminar(PNC pncEliminar) {
        try {
            pncEJB.eliminar(pncEliminar);
            pncs = pncEJB.listarPNCs();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "PNC eliminado correctamente"
            );

            if (this.pnc != null && this.pnc.getNro() == pncEliminar.getNro()) {
                nuevo();
            }

        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar el PNC: " + e.getMessage()
            );
        }
    }
    
    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(severity, resumen, detalle)
        );
    }

    // GETTERS Y SETTERS
    public List<PNC> getPncs() { return pncs; }
    public void setPncs(List<PNC> pncs) { this.pncs = pncs; }
    
    public List<Vehiculo> getVehiculos() { return vehiculos; }
    public void setVehiculos(List<Vehiculo> vehiculos) { this.vehiculos = vehiculos; }

    public PNC getPnc() { return pnc; }
    public void setPnc(PNC pnc) { this.pnc = pnc; }

    public boolean isEditando() { return editando; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }
}
