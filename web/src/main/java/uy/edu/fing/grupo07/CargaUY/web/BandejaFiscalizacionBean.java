package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Managed Bean para /funcionario/bandeja-fiscalizacion.xhtml.
 * Bandeja interactiva de evaluación de evidencia, revisión de descargos y resolución administrativa.
 */
@Named("bandejaFiscalizacionBean")
@ViewScoped
public class BandejaFiscalizacionBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    private List<InfraccionDTO> casos;
    private List<InfraccionDTO> casosFiltrados;
    private InfraccionDTO casoSeleccionado;

    private String fundamentosResolucion;

    @PostConstruct
    public void init() {
        cargarCasos();
    }

    public void cargarCasos() {
        try {
            this.casos = infraccionesService.listarInfracciones(null, null);
        } catch (Exception e) {
            this.casos = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar los casos: " + e.getMessage());
        }
    }

    public void seleccionarCaso(InfraccionDTO caso) {
        this.casoSeleccionado = caso;
        this.fundamentosResolucion = caso != null && caso.fundamento() != null ? caso.fundamento() : "";
    }

    public boolean isCasoResuelto() {
        if (casoSeleccionado == null) return true;
        return casoSeleccionado.estado() == EstadoInfraccion.CONFIRMADA
                || casoSeleccionado.estado() == EstadoInfraccion.DESESTIMADA;
    }

    public void notificarEmpresa() {
        if (casoSeleccionado == null) return;
        try {
            casoSeleccionado = infraccionesService.notificarInfraccion(casoSeleccionado.idCaso());
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Empresa Notificada",
                    "Se envió notificación oficial a la empresa para el caso #" + casoSeleccionado.idCaso());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al notificar", e.getMessage());
        }
    }

    public void confirmarSancion() {
        if (casoSeleccionado == null) return;
        if (fundamentosResolucion == null || fundamentosResolucion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Fundamento Obligatorio",
                    "Debe ingresar los fundamentos técnicos/legales para confirmar la sanción.");
            FacesContext.getCurrentInstance().validationFailed();
            return;
        }
        if (casoSeleccionado.estado() == EstadoInfraccion.DETECTADA) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Notificación Requerida",
                    "Debe notificar formalmente a la empresa antes de poder confirmar una sanción.");
            FacesContext.getCurrentInstance().validationFailed();
            return;
        }
        try {
            int funcId = obtenerFuncionarioActivoId();
            ResolucionInfraccionDTO dto = new ResolucionInfraccionDTO(funcId, fundamentosResolucion.trim());
            casoSeleccionado = infraccionesService.confirmarInfraccion(casoSeleccionado.idCaso(), dto);
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Sanción Confirmada",
                    "El caso #" + casoSeleccionado.idCaso() + " quedó CONFIRMADO y sellado en auditoría inmutable.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al confirmar sanción", e.getMessage());
            FacesContext.getCurrentInstance().validationFailed();
        }
    }

    public void desestimarCaso() {
        if (casoSeleccionado == null) return;
        if (fundamentosResolucion == null || fundamentosResolucion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Fundamento Obligatorio",
                    "Debe ingresar el motivo de desestimación para el registro de auditoría.");
            FacesContext.getCurrentInstance().validationFailed();
            return;
        }
        try {
            int funcId = obtenerFuncionarioActivoId();
            ResolucionInfraccionDTO dto = new ResolucionInfraccionDTO(funcId, fundamentosResolucion.trim());
            casoSeleccionado = infraccionesService.desestimarInfraccion(casoSeleccionado.idCaso(), dto);
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Caso Desestimado",
                    "El expediente #" + casoSeleccionado.idCaso() + " fue DESESTIMADO y archivado.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al desestimar", e.getMessage());
            FacesContext.getCurrentInstance().validationFailed();
        }
    }

    private int obtenerFuncionarioActivoId() {
        try {
            return SesionActual.actor().id();
        } catch (Exception e) {
            return 1;
        }
    }

    public int getTotalCasos() {
        return casos != null ? casos.size() : 0;
    }

    public long getTotalPendientesRevision() {
        if (casos == null) return 0;
        return casos.stream()
                .filter(c -> c.estado() == EstadoInfraccion.DETECTADA
                        || c.estado() == EstadoInfraccion.NOTIFICADA
                        || c.estado() == EstadoInfraccion.EN_DESCARGO)
                .count();
    }

    public long getTotalConfirmadas() {
        if (casos == null) return 0;
        return casos.stream().filter(c -> c.estado() == EstadoInfraccion.CONFIRMADA).count();
    }

    public long getTotalDesestimadas() {
        if (casos == null) return 0;
        return casos.stream().filter(c -> c.estado() == EstadoInfraccion.DESESTIMADA).count();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<InfraccionDTO> getCasos() {
        return casos;
    }

    public List<InfraccionDTO> getCasosFiltrados() {
        return casosFiltrados;
    }

    public void setCasosFiltrados(List<InfraccionDTO> casosFiltrados) {
        this.casosFiltrados = casosFiltrados;
    }

    public InfraccionDTO getCasoSeleccionado() {
        return casoSeleccionado;
    }

    public void setCasoSeleccionado(InfraccionDTO casoSeleccionado) {
        this.casoSeleccionado = casoSeleccionado;
    }

    public String getFundamentosResolucion() {
        return fundamentosResolucion;
    }

    public void setFundamentosResolucion(String fundamentosResolucion) {
        this.fundamentosResolucion = fundamentosResolucion;
    }

    public EstadoInfraccion[] getEstadosInfraccion() {
        return EstadoInfraccion.values();
    }

    public TipoInfraccion[] getTiposInfraccion() {
        return TipoInfraccion.values();
    }
}
