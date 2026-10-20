package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean para visualizar el expediente de una infracción y procesar
 * sus transiciones de estado mediante PrimeFaces.
 */
@Named("detalleInfraccionBean")
@ViewScoped
public class DetalleInfraccionBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DetalleInfraccionBean.class.getName());

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;
    @jakarta.inject.Inject
    private jakarta.servlet.http.HttpServletRequest request;

    private Integer idCaso;
    private InfraccionDTO infraccion;

    private String textoDescargo;
    private Integer funcionarioId;
    private String fundamento;

    public DetalleInfraccionBean() {
    }

    public DetalleInfraccionBean(GestionInfraccionesServiceLocal infraccionesService) {
        this.infraccionesService = infraccionesService;
    }

    public void inicializar() {
        if (idCaso == null) {
            return;
        }
        cargarInfraccion();
    }

    public void cargarInfraccion() {
        if (idCaso == null) {
            return;
        }

        try {
            this.infraccion = infraccionesService.obtenerInfraccion(idCaso);
        } catch (BusinessException excepcionNegocio) {
            LOGGER.log(Level.WARNING, "Infracción no encontrada: {0}", excepcionNegocio.getMessage());
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "No Encontrada",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            LOGGER.log(Level.SEVERE, "Error al cargar la infracción con ID: " + idCaso, excepcionGeneral);
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Técnico",
                    "No se pudo cargar la información de la infracción."
            );
        }
    }

    public void notificar() {
        if (idCaso == null) {
            return;
        }

        try {
            this.infraccion = infraccionesService.notificarInfraccion(idCaso);
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Notificación Exitosa",
                    "La infracción #" + idCaso + " ha sido notificada a la empresa."
            );
        } catch (BusinessException excepcionNegocio) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error al Notificar",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Inesperado",
                    excepcionGeneral.getMessage()
            );
        }
    }

    public void presentarDescargo() {
        if (idCaso == null) {
            return;
        }

        if (textoDescargo == null || textoDescargo.isBlank()) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Campo Requerido",
                    "El texto del descargo no puede estar vacío."
            );
            return;
        }

        try {
            DescargoRequestDTO peticion = new DescargoRequestDTO(textoDescargo);
            this.infraccion = infraccionesService.presentarDescargo(idCaso, peticion);
            this.textoDescargo = "";
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Descargo Registrado",
                    "Se ha registrado el descargo de la empresa transportista."
            );
        } catch (BusinessException excepcionNegocio) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error en Descargo",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Inesperado",
                    excepcionGeneral.getMessage()
            );
        }
    }

    public void confirmar() {
        if (idCaso == null) {
            return;
        }

        if (fundamento == null || fundamento.isBlank()) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Campo Requerido",
                    "El fundamento de la resolución condenatoria es obligatorio."
            );
            return;
        }

        try {
            ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(getFuncionarioId(), fundamento);
            this.infraccion = infraccionesService.confirmarInfraccion(idCaso, resolucion);
            this.fundamento = "";
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Infracción Confirmada",
                    "La infracción #" + idCaso + " fue CONFIRMADA y registrada en auditoría inmutable."
            );
        } catch (BusinessException excepcionNegocio) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error al Confirmar",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Inesperado",
                    excepcionGeneral.getMessage()
            );
        }
    }

    public void desestimar() {
        if (idCaso == null) {
            return;
        }

        if (fundamento == null || fundamento.isBlank()) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Campo Requerido",
                    "El motivo o fundamento para desestimar el caso es obligatorio."
            );
            return;
        }

        try {
            ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(getFuncionarioId(), fundamento);
            this.infraccion = infraccionesService.desestimarInfraccion(idCaso, resolucion);
            this.fundamento = "";
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Infracción Desestimada",
                    "El caso #" + idCaso + " fue DESESTIMADO y registrado en auditoría inmutable."
            );
        } catch (BusinessException excepcionNegocio) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error al Desestimar",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Inesperado",
                    excepcionGeneral.getMessage()
            );
        }
    }

    public boolean isDetectada() {
        if (infraccion == null) {
            return false;
        }
        return infraccion.estado() == EstadoInfraccion.DETECTADA;
    }

    public boolean isPuedePresentarDescargo() {
        if (infraccion == null) {
            return false;
        }
        boolean esNotificada = infraccion.estado() == EstadoInfraccion.NOTIFICADA;
        boolean esEnDescargo = infraccion.estado() == EstadoInfraccion.EN_DESCARGO;
        return esNotificada || esEnDescargo;
    }

    public boolean isPuedeConfirmar() {
        if (infraccion == null) {
            return false;
        }
        boolean esNotificada = infraccion.estado() == EstadoInfraccion.NOTIFICADA;
        boolean esEnDescargo = infraccion.estado() == EstadoInfraccion.EN_DESCARGO;
        return esNotificada || esEnDescargo;
    }

    public boolean isPuedeDesestimar() {
        if (infraccion == null) {
            return false;
        }
        boolean yaResuelta = isResuelta();
        return !yaResuelta;
    }

    public boolean isResuelta() {
        if (infraccion == null) {
            return false;
        }
        boolean confirmada = infraccion.estado() == EstadoInfraccion.CONFIRMADA;
        boolean desestimada = infraccion.estado() == EstadoInfraccion.DESESTIMADA;
        return confirmada || desestimada;
    }

    protected void mostrarMensaje(FacesMessage.Severity severidad, String resumen, String detalle) {
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            if (context != null) {
                context.addMessage(null, new FacesMessage(severidad, resumen, detalle));
            }
        } catch (Throwable errorEntorno) {
            LOGGER.log(Level.FINE, "FacesMessage omitido fuera de ciclo JSF: {0}", detalle);
        }
    }

    public Integer getIdCaso() {
        return idCaso;
    }

    public void setIdCaso(Integer idCaso) {
        this.idCaso = idCaso;
    }

    public InfraccionDTO getInfraccion() {
        return infraccion;
    }

    public void setInfraccion(InfraccionDTO infraccion) {
        this.infraccion = infraccion;
    }

    public String getTextoDescargo() {
        return textoDescargo;
    }

    public void setTextoDescargo(String textoDescargo) {
        this.textoDescargo = textoDescargo;
    }

    public Integer getFuncionarioId() {
        if(request!=null) {
            if(request.getAttribute("actor") instanceof uy.edu.fing.grupo07.CargaUY.security.Acceso actor)
                return actor.id();
            throw new uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso(401,"Sesión no autenticada");
        }
        return funcionarioId;
    }

    public void setFuncionarioId(Integer funcionarioId) {
        this.funcionarioId = funcionarioId;
    }

    public String getFundamento() {
        return fundamento;
    }

    public void setFundamento(String fundamento) {
        this.fundamento = fundamento;
    }
}
