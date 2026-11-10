package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJBLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Named("choferViajesBean")
@ViewScoped
public class ChoferViajesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    @EJB
    private VehiculoEJBLocal vehiculoEJB;

    private List<Empresa> empresasAsignadas = new ArrayList<>();
    private List<GuiaDeViaje> guiasAsignadas = new ArrayList<>();

    @PostConstruct
    public void init() {
        recargar();
    }

    public void recargar() {
        try {
            String token = SesionActual.token();
            this.empresasAsignadas = new ArrayList<>(vehiculoEJB.listarEmpresasParaActor(token));
            this.guiasAsignadas = new ArrayList<>(guiaDeViajeEJB.listarParaActor(token));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar sus viajes: " + e.getMessage()));
        }
    }

    public List<Empresa> getEmpresasAsignadas() {
        return empresasAsignadas;
    }

    public List<GuiaDeViaje> getGuiasAsignadas() {
        return guiasAsignadas;
    }
}
