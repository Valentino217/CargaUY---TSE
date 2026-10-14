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
import uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer;
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
    private List<Chofer> choferes;

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
    private final int ID_CIUDADANO_SIMULADO = 1;
    
	public GuiaDeViajeBean() {
		// TODO Auto-generated constructor stub
	}

	@PostConstruct
    public void init() {
        try {
          
        	guias = GuiaDeViajeEJB.listarGuiasPorCiudadano(ID_CIUDADANO_SIMULADO);
            // 2. Filtramos el combo de empresas disponibles para el ciudadano
            empresas = vehiculoEJB.listarEmpresasPorCiudadano(ID_CIUDADANO_SIMULADO);
            
            //3. Filtramos el combo de guias creadas por el ciudadano de la empresa
            
            // Al inicio no hay una empresa seleccionada
            vehiculos = new ArrayList<>();
            choferes = new ArrayList<>();
            
           
        } catch (Exception e) {
         
        	  mostrarMensaje(
                      FacesMessage.SEVERITY_ERROR,
                      "Error",
                      "No se pudo ejecutar procedimiento init: " + e.getMessage()
              );
        }
        nuevo();
    }
	
	 public void nuevo() {
	        guia = new GuiaDeViaje();
	        editando = false;
	        empresaId = 0;
	        choferId = 0;
	        vehiculos = new ArrayList<>();
	        choferes = new ArrayList<>();
	        matriculaVehiculo = null;
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

	            guia.setEmpresa(empresaSeleccionada);
	            
	            
	            Vehiculo vehiculoSeleccionado = null;

	            for (Vehiculo v : vehiculos) {
	                if (v.getMatricula().equals(matriculaVehiculo)) {
	                    vehiculoSeleccionado = v;
	                    break;
	                }
	            }

	            if (vehiculoSeleccionado == null) {
	                throw new IllegalArgumentException("Debe seleccionar un vehículo válido");
	            }

	            guia.setVehiculo(vehiculoSeleccionado);
	            
	            /*chofer*/
	            
	            Chofer choferSeleccionado = null;

	            for (Chofer c : choferes) {
	                if (c.getId() == choferId) {
	                    choferSeleccionado = c;
	                    break;
	                }
	            }

	            if (choferSeleccionado == null) {
	                throw new IllegalArgumentException("Debe seleccionar un chofer válido");
	            }

	            guia.setChofer(choferSeleccionado);
	            
	            if (editando) {
	                // Si estaba editando, llama a modificar
	                GuiaDeViajeEJB.modificar(guia);
	                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guia de Viaje modificada correctamente");
	            } else {
	                // Si era nuevo, llama a crear
	            	GuiaDeViajeEJB.crear(guia);
	                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Éxito", "Guia de Viaje registrada correctamente");
	            }

	            // CORRECCIÓN: Al refrescar el listado de la tabla tras guardar, volvemos a filtrar por el ciudadano
	            guias = GuiaDeViajeEJB.listarGuiasPorCiudadano(ID_CIUDADANO_SIMULADO);

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

	    public void editar(GuiaDeViaje guia) {
	        this.guia = guia;
	        this.editando = true;
	        // Cargar también la empresa asociada en el combo
	        if (guia.getEmpresa() != null) {
	            this.empresaId = guia.getEmpresa().getNroEmpresa();
	        }
	        
	        if (guia.getVehiculo() != null) {
	            matriculaVehiculo = guia.getVehiculo().getMatricula();
	        }
	        
	        if (guia.getChofer() != null) {
	            choferId = guia.getChofer().getId();
	        }
	        
	        // Cargar los vehículos y choferes de esa empresa
	        vehiculos = vehiculoEJB.listarVehiculosPorEmpresa(empresaId);
	        choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(empresaId);
	    }

	    // =========================
	    // BAJA
	    // =========================

	    public void eliminar(GuiaDeViaje guia) {
	        try {
	            GuiaDeViajeEJB.eliminar(guia);
	            guias = GuiaDeViajeEJB.listarGuiasPorCiudadano(ID_CIUDADANO_SIMULADO);

	            mostrarMensaje(
	                    FacesMessage.SEVERITY_INFO,
	                    "Éxito",
	                    "Guia de Viaje eliminada correctamente"
	            );

	          

	        } catch (Exception e) {
	            mostrarMensaje(
	                    FacesMessage.SEVERITY_ERROR,
	                    "Error",
	                    "No se pudo eliminar la Guia de Viaje"
	            );
	        }
	    }

	    
	    public void seleccionarEmpresa() {
	        // Limpiar los datos de la selección anterior
	        vehiculos = new ArrayList<>();
	        choferes = new ArrayList<>();

	        guia.setVehiculo(null);
	        guia.setChofer(null);

	        if (empresaId == 0) {
	            return;
	        }
	        try {
	            vehiculos = vehiculoEJB.listarVehiculosPorEmpresa(empresaId);
	            choferes = GuiaDeViajeEJB.listarChoferesPorEmpresa(empresaId);

	        } catch (Exception e) {
	            mostrarMensaje(
	                FacesMessage.SEVERITY_ERROR,
	                "Error",
	                "No se pudieron cargar los vehículos y choferes: "
	                    + e.getMessage()
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
        
	    public List<Chofer> getChoferes() {
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
