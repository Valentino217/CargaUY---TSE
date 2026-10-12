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
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean para la bandeja de fiscalización e infracciones en JSF/PrimeFaces.
 */
@Named("bandejaInfraccionesBean")
@ViewScoped
public class BandejaInfraccionesBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(BandejaInfraccionesBean.class.getName());

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    @EJB
    private MotorFiscalizacionLocal motorFiscalizacion;

    @EJB
    private FiscalizacionProducerLocal fiscalizacionProducer;

    private List<InfraccionDTO> infracciones;
    private EstadoInfraccion filtroEstado;
    private TipoInfraccion filtroTipo;

    private Long guiaIdFiscalizar;
    private String modoFiscalizacion = "sync";

    public BandejaInfraccionesBean() {
        this.infracciones = new ArrayList<>();
    }

    public BandejaInfraccionesBean(
            GestionInfraccionesServiceLocal infraccionesService,
            MotorFiscalizacionLocal motorFiscalizacion,
            FiscalizacionProducerLocal fiscalizacionProducer
    ) {
        this.infraccionesService = infraccionesService;
        this.motorFiscalizacion = motorFiscalizacion;
        this.fiscalizacionProducer = fiscalizacionProducer;
        this.infracciones = new ArrayList<>();
    }

    @PostConstruct
    public void inicializar() {
        cargarInfracciones();
    }

    public void cargarInfracciones() {
        try {
            this.infracciones = infraccionesService.listarInfracciones(filtroEstado, filtroTipo);
        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al listar infracciones", excepcion);
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudieron cargar las infracciones: " + excepcion.getMessage()
            );
            this.infracciones = new ArrayList<>();
        }
    }

    public void filtrar() {
        cargarInfracciones();
    }

    public void limpiarFiltros() {
        this.filtroEstado = null;
        this.filtroTipo = null;
        cargarInfracciones();
    }

    public void ejecutarFiscalizacion() {
        if (guiaIdFiscalizar == null) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Validación",
                    "Debe indicar el identificador de la guía a fiscalizar."
            );
            return;
        }

        try {
            if ("async".equalsIgnoreCase(modoFiscalizacion)) {
                fiscalizacionProducer.solicitarEvaluacion(guiaIdFiscalizar);
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Encolado JMS",
                        "Se encoló la evaluación en FiscalizacionQueue para la guía #" + guiaIdFiscalizar
                );
                return;
            }

            List<TipoInfraccion> detectadas = motorFiscalizacion.evaluarGuia(guiaIdFiscalizar);
            if (detectadas.isEmpty()) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Fiscalización Correcta",
                        "Evaluación finalizada para guía #" + guiaIdFiscalizar + ". No se detectaron infracciones."
                );
            } else {
                mostrarMensaje(
                        FacesMessage.SEVERITY_WARN,
                        "Infracciones Detectadas",
                        "Se generaron las siguientes infracciones para la guía #" + guiaIdFiscalizar + ": " + detectadas
                );
            }

            cargarInfracciones();

        } catch (BusinessException excepcionNegocio) {
            LOGGER.log(Level.WARNING, "Error de negocio al fiscalizar guía: {0}", excepcionNegocio.getMessage());
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error de Negocio",
                    excepcionNegocio.getMessage()
            );
        } catch (Exception excepcionGeneral) {
            LOGGER.log(Level.SEVERE, "Error técnico al fiscalizar", excepcionGeneral);
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error Técnico",
                    "Error inesperado al fiscalizar: " + excepcionGeneral.getMessage()
            );
        }
    }

    public EstadoInfraccion[] getEstadosDisponibles() {
        return EstadoInfraccion.values();
    }

    public TipoInfraccion[] getTiposDisponibles() {
        return TipoInfraccion.values();
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

    public List<InfraccionDTO> getInfracciones() {
        return infracciones;
    }

    public void setInfracciones(List<InfraccionDTO> infracciones) {
        this.infracciones = infracciones;
    }

    public EstadoInfraccion getFiltroEstado() {
        return filtroEstado;
    }

    public void setFiltroEstado(EstadoInfraccion filtroEstado) {
        this.filtroEstado = filtroEstado;
    }

    public TipoInfraccion getFiltroTipo() {
        return filtroTipo;
    }

    public void setFiltroTipo(TipoInfraccion filtroTipo) {
        this.filtroTipo = filtroTipo;
    }

    public Long getGuiaIdFiscalizar() {
        return guiaIdFiscalizar;
    }

    public void setGuiaIdFiscalizar(Long guiaIdFiscalizar) {
        this.guiaIdFiscalizar = guiaIdFiscalizar;
    }

    public String getModoFiscalizacion() {
        return modoFiscalizacion;
    }

    public void setModoFiscalizacion(String modoFiscalizacion) {
        this.modoFiscalizacion = modoFiscalizacion;
    }
}
