package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJB;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;

@Named("GuiaDeViajeBean")
@ViewScoped // <--- Permite mantener el estado del formulario entre AJAX requests
public class GuiaDeViajeBean implements Serializable {
	private static final long serialVersionUID = 1L;

	@EJB
    private VehiculoEJB vehiculoEJB;
	
	@EJB
	private GuiaDeViajeEJB GuiaDeViajeEJB;

    private List<Vehiculo> vehiculos;
    private List<Empresa> empresas;
    private List<GuiaDeViaje> guias;
    private List<Usuario> choferes;

    private GuiaDeViaje guia;
    
    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
    }
    
    private String matriculaVehiculo;

    public String getMatriculaVehiculo() {
        return matriculaVehiculo;
    }

    public void setMatriculaVehiculo(String matriculaVehiculo) {
        this.matriculaVehiculo = matriculaVehiculo;
    }
    
    private int choferId;

    public int getChoferId() {
        return choferId;
    }

    public void setChoferId(int choferId) {
        this.choferId = choferId;
    }
    
    
    private boolean editando;
	
    private int empresaId;

    public int getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(int empresaId) {
        this.empresaId = empresaId;
    }
	
 // Creamos una constante o variable de clase temporal para la simulación
    private int ciudadanoActual() { return SesionActual.actor().id(); }
    
	public GuiaDeViajeBean() {
		// TODO Auto-generated constructor stub
	}

	@PostConstruct
    public void init() {
        try {
            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());


            empresas = vehiculoEJB.listarEmpresasParaActor(SesionActual.token());


        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo inicializar los datos de Guía de Viaje: " + excepcion.getMessage()
            );
        }

        nuevo();
    }
	
    public void nuevo() {
        guia = new GuiaDeViaje();
        guia.setFecha(java.time.LocalDate.now());
        editando = false;

        if (empresas != null && !empresas.isEmpty()) {
            Empresa empresaPorDefecto = empresas.get(0);
            this.empresaId = empresaPorDefecto.getNroEmpresa();
            this.vehiculos = vehiculoEJB.listarVehiculosPorEmpresa(SesionActual.token(), empresaId);
            this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);

            if (vehiculos != null && !vehiculos.isEmpty()) {
                this.matriculaVehiculo = vehiculos.get(0).getMatricula();
            } else {
                this.matriculaVehiculo = null;
            }

            if (choferes != null && !choferes.isEmpty()) {
                this.choferId = choferes.get(0).getId();
            } else {
                this.choferId = 0;
            }
        } else {
            this.empresaId = 0;
            this.choferId = 0;
            this.matriculaVehiculo = null;
            this.vehiculos = new ArrayList<>();
            this.choferes = new ArrayList<>();
        }
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
                throw new IllegalArgumentException("Debe seleccionar una empresa válida.");
            }

            guia.setEmpresa(empresaSeleccionada);
            
            Vehiculo vehiculoSeleccionado = null;

            for (Vehiculo vehiculo : vehiculos) {
                if (vehiculo.getMatricula().equals(matriculaVehiculo)) {
                    vehiculoSeleccionado = vehiculo;
                    break;
                }
            }

            if (vehiculoSeleccionado == null) {
                throw new IllegalArgumentException("Debe seleccionar un vehículo válido.");
            }

            guia.setVehiculo(vehiculoSeleccionado);
            
            Usuario choferSeleccionado = null;

            for (Usuario chofer : choferes) {
                if (chofer.getId() == choferId) {
                    choferSeleccionado = chofer;
                    break;
                }
            }

            if (choferSeleccionado == null) {
                throw new IllegalArgumentException("Debe seleccionar un chofer válido.");
            }

            guia.setChofer(choferSeleccionado);
            
            if (editando) {
                GuiaDeViajeEJB.modificar(SesionActual.token(), guia);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guía de viaje modificada correctamente.");
            } else {
                GuiaDeViajeEJB.crear(SesionActual.token(), guia);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guía de viaje registrada correctamente.");
            }

            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());


            nuevo();

        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo guardar la guía de viaje: " + excepcion.getMessage()
            );
        }
    }
	 
    private void mostrarMensaje(FacesMessage.Severity severity, String resumen, String detalle) {
        FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(severity, resumen, detalle)
        );
    }
	 
    public void editar(GuiaDeViaje guiaSeleccionada) {
        this.guia = guiaSeleccionada;
        this.editando = true;

        if (guiaSeleccionada.getEmpresa() != null) {
            this.empresaId = guiaSeleccionada.getEmpresa().getNroEmpresa();
        }
        
        if (guiaSeleccionada.getVehiculo() != null) {
            this.matriculaVehiculo = guiaSeleccionada.getVehiculo().getMatricula();
        }
        
        if (guiaSeleccionada.getChofer() != null) {
            this.choferId = guiaSeleccionada.getChofer().getId();
        }
        
        this.vehiculos = vehiculoEJB.listarVehiculosPorEmpresa(SesionActual.token(), empresaId);
        this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);
    }

    public void eliminar(GuiaDeViaje guiaAEliminar) {
        try {
            GuiaDeViajeEJB.eliminar(SesionActual.token(), guiaAEliminar);
            guias = GuiaDeViajeEJB.listarParaActor(SesionActual.token());


            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Éxito",
                    "Guía de viaje eliminada correctamente."
            );

            nuevo();

        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo eliminar la guía de viaje: " + excepcion.getMessage()
            );
        }
    }

    public void seleccionarEmpresa() {
        this.vehiculos = new ArrayList<>();
        this.choferes = new ArrayList<>();
        this.matriculaVehiculo = null;
        this.choferId = 0;

        if (this.empresaId == 0) {
            return;
        }

        try {
            this.vehiculos = vehiculoEJB.listarVehiculosPorEmpresa(SesionActual.token(), empresaId);
            this.choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(SesionActual.token(), empresaId);

            if (vehiculos != null && !vehiculos.isEmpty()) {
                this.matriculaVehiculo = vehiculos.get(0).getMatricula();
            }

            if (choferes != null && !choferes.isEmpty()) {
                this.choferId = choferes.get(0).getId();
            }

        } catch (Exception excepcion) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudieron cargar los vehículos y choferes: " + excepcion.getMessage()
            );
        }
    }
	 
	 /*//////////////////////////////////////////////////////////////////////////////////////////////////////////*/
	 // GETTERS Y SETTERS
	    public List<Vehiculo> getVehiculos() {
	        return vehiculos;
	    }

	    public List<Empresa> getEmpresas() {
	        return empresas;
	    }
        
	    public List<Usuario> getChoferes() {
	        return choferes;
	    }
	    
	    public List<GuiaDeViaje> getGuiaDeViajes() {
	        return guias;
	    }
	    
	    public GuiaDeViaje getGuiaDeViaje() {
	        return guia;
	    }

	    public void setGuiaDeViaje(GuiaDeViaje guia) {
	        this.guia = guia;
	    }

	    public boolean isEditando() {
	        return editando;
	    }
}
