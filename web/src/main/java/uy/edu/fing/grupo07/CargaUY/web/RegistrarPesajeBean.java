package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;

/**
 * Managed Bean para registrar pesajes en balanza y simular la fiscalización
 * automática sobre guías de viaje en curso.
 */
@Named("registrarPesajeBean")
@ViewScoped
public class RegistrarPesajeBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RegistrarPesajeBean.class.getName());

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    @EJB
    private MotorFiscalizacionLocal motorFiscalizacion;

    private List<GuiaDeViaje> guiasDisponibles;
    private Long guiaIdSeleccionada;
    private Integer pesoRegistrado;
    private LocalDate fecha;
    private LocalTime hora;
    private boolean evaluarInmediato;
    private List<PesadaBalanzaDTO> pesadasDeLaGuia;
    private List<TipoInfraccion> ultimasInfraccionesDetectadas;

    public RegistrarPesajeBean() {
        this.guiasDisponibles = new ArrayList<>();
        this.pesadasDeLaGuia = new ArrayList<>();
        this.ultimasInfraccionesDetectadas = new ArrayList<>();
        this.evaluarInmediato = true;
    }

    public RegistrarPesajeBean(
            GestionBalanzaServiceLocal balanzaService,
            GuiaDeViajeEJBLocal guiaDeViajeEJB,
            MotorFiscalizacionLocal motorFiscalizacion
    ) {
        this.balanzaService = balanzaService;
        this.guiaDeViajeEJB = guiaDeViajeEJB;
        this.motorFiscalizacion = motorFiscalizacion;
        this.guiasDisponibles = new ArrayList<>();
        this.pesadasDeLaGuia = new ArrayList<>();
        this.ultimasInfraccionesDetectadas = new ArrayList<>();
        this.evaluarInmediato = true;
    }

    @PostConstruct
    public void init() {
        this.fecha = LocalDate.now();
        this.hora = LocalTime.now().withNano(0);
        this.pesoRegistrado = 42500;

        cargarGuias();

        if (!guiasDisponibles.isEmpty()) {
            seleccionarGuiaPorDefecto();
            cargarPesadasDeGuia();
        }
    }

    /**
     * Carga el listado de guías disponibles para pesaje.
     */
    public void cargarGuias() {
        try {
            List<GuiaDeViaje> todasLasGuias = guiaDeViajeEJB.listarGuias();
            if (todasLasGuias != null) {
                this.guiasDisponibles = todasLasGuias.stream()
                        .filter(g -> g.getEstado() == TipoEstado.EN_CURSO)
                        .toList();
            } else {
                this.guiasDisponibles = new ArrayList<>();
            }
        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al listar guías para pesaje", excepcion);
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudieron cargar las guías: " + excepcion.getMessage()
            );
            this.guiasDisponibles = new ArrayList<>();
        }
    }

    /**
     * Selecciona la primera guía en curso o la primera existente como valor predeterminado.
     */
    private void seleccionarGuiaPorDefecto() {
        for (GuiaDeViaje guia : guiasDisponibles) {
            if (guia.getEstado() == TipoEstado.EN_CURSO) {
                this.guiaIdSeleccionada = guia.getId();
                return;
            }
        }

        if (!guiasDisponibles.isEmpty()) {
            this.guiaIdSeleccionada = guiasDisponibles.get(0).getId();
        }
    }

    /**
     * Evento al cambiar la guía seleccionada en el combo box.
     */
    public void onGuiaChange() {
        cargarPesadasDeGuia();
    }

    /**
     * Consulta las pesadas registradas para la guía actualmente seleccionada.
     */
    public void cargarPesadasDeGuia() {
        if (guiaIdSeleccionada == null) {
            this.pesadasDeLaGuia = new ArrayList<>();
            return;
        }

        try {
            this.pesadasDeLaGuia = balanzaService.listarPesadasPorGuia(guiaIdSeleccionada);
        } catch (Exception excepcion) {
            LOGGER.log(Level.WARNING, "Error al consultar pesadas de la guía {0}: {1}",
                    new Object[]{guiaIdSeleccionada, excepcion.getMessage()});
            this.pesadasDeLaGuia = new ArrayList<>();
        }
    }

    /**
     * Da de alta una nueva pesada en balanza y ejecuta la fiscalización.
     */
    public void registrarPesaje() {
        if (guiaIdSeleccionada == null) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Validación",
                    "Debe seleccionar una guía de viaje."
            );
            return;
        }

        if (pesoRegistrado == null || pesoRegistrado <= 0) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Validación",
                    "El peso registrado debe ser un valor numérico mayor a 0 kg."
            );
            return;
        }

        LocalDate fechaEfectiva = this.fecha;
        if (fechaEfectiva == null) {
            fechaEfectiva = LocalDate.now();
        }

        LocalTime horaEfectiva = this.hora;
        if (horaEfectiva == null) {
            horaEfectiva = LocalTime.now();
        }

        PesadaBalanzaDTO pesadaDTO = new PesadaBalanzaDTO(
                null,
                fechaEfectiva,
                horaEfectiva,
                pesoRegistrado,
                null,
                guiaIdSeleccionada
        );

        try {
            PesadaBalanzaDTO pesadaGuardada = balanzaService.registrarPesada(pesadaDTO);
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Pesaje Registrado",
                    "Se registró la pesada #" + pesadaGuardada.idPesada()
                            + " de " + pesoRegistrado + " kg. Evaluación encolada en FiscalizacionQueue (JMS)."
            );
            cargarPesadasDeGuia();

        } catch (Exception excepcion) {
            LOGGER.log(Level.SEVERE, "Error al registrar pesada", excepcion);
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo registrar la pesada: " + excepcion.getMessage()
            );
        }
    }

    /**
     * Construye una etiqueta clara y descriptiva para cada opción de guía en el menú.
     */
    public String obtenerEtiquetaGuia(GuiaDeViaje guia) {
        if (guia == null) {
            return "";
        }

        String matricula = "Sin vehículo";
        if (guia.getVehiculo() != null) {
            matricula = guia.getVehiculo().getMatricula();
        }

        return "Guía #" + guia.getId() + " - " + guia.getOrigen() + " a " + guia.getDestino()
                + " (" + matricula + ") [" + guia.getEstado() + "]";
    }

    private void mostrarMensaje(FacesMessage.Severity severidad, String resumen, String detalle) {
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            if (context != null) {
                context.addMessage(null, new FacesMessage(severidad, resumen, detalle));
            }
        } catch (Throwable errorEntorno) {
            LOGGER.log(Level.FINE, "FacesMessage omitido fuera de JSF: {0}", detalle);
        }
    }

    // Getters y Setters
    public List<GuiaDeViaje> getGuiasDisponibles() {
        return guiasDisponibles;
    }

    public void setGuiasDisponibles(List<GuiaDeViaje> guiasDisponibles) {
        this.guiasDisponibles = guiasDisponibles;
    }

    public Long getGuiaIdSeleccionada() {
        return guiaIdSeleccionada;
    }

    public void setGuiaIdSeleccionada(Long guiaIdSeleccionada) {
        this.guiaIdSeleccionada = guiaIdSeleccionada;
    }

    public Integer getPesoRegistrado() {
        return pesoRegistrado;
    }

    public void setPesoRegistrado(Integer pesoRegistrado) {
        this.pesoRegistrado = pesoRegistrado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public boolean isEvaluarInmediato() {
        return evaluarInmediato;
    }

    public void setEvaluarInmediato(boolean evaluarInmediato) {
        this.evaluarInmediato = evaluarInmediato;
    }

    public List<PesadaBalanzaDTO> getPesadasDeLaGuia() {
        return pesadasDeLaGuia;
    }

    public void setPesadasDeLaGuia(List<PesadaBalanzaDTO> pesadasDeLaGuia) {
        this.pesadasDeLaGuia = pesadasDeLaGuia;
    }

    public List<TipoInfraccion> getUltimasInfraccionesDetectadas() {
        return ultimasInfraccionesDetectadas;
    }

    public void setUltimasInfraccionesDetectadas(List<TipoInfraccion> ultimasInfraccionesDetectadas) {
        this.ultimasInfraccionesDetectadas = ultimasInfraccionesDetectadas;
    }
}
