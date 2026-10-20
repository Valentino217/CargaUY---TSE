package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Managed Bean de PrimeFaces para la consulta y seguimiento integral de un viaje (P3: Tracking & Balanza),
 * incluyendo la línea temporal cronológica de eventos de ruta y los pesajes en balanzas periféricas.
 */
@Named("detalleViajeBean")
@ViewScoped
public class DetalleViajeBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DetalleViajeBean.class.getName());

    @EJB
    private GestionEventosServiceLocal eventosService;

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    // Estado de selección y datos cargados
    private Long guiaIdSeleccionada;
    private List<GuiaDeViaje> guiasDisponibles;
    private GuiaDeViaje guiaActual;
    private List<EventoViajeDTO> eventos;
    private List<PesadaBalanzaDTO> pesadas;

    // Campos para reporte rápido de incidentes en ruta
    private String nuevoIncidenteDescripcion;
    private String nuevoIncidenteFoto;
    private Double nuevoIncidenteLatitud;
    private Double nuevoIncidenteLongitud;

    public DetalleViajeBean() {
        this.guiasDisponibles = new ArrayList<>();
        this.eventos = new ArrayList<>();
        this.pesadas = new ArrayList<>();
    }

    public DetalleViajeBean(
            GestionEventosServiceLocal eventosService,
            GestionBalanzaServiceLocal balanzaService,
            GuiaDeViajeEJBLocal guiaDeViajeEJB
    ) {
        this.eventosService = eventosService;
        this.balanzaService = balanzaService;
        this.guiaDeViajeEJB = guiaDeViajeEJB;
        this.guiasDisponibles = new ArrayList<>();
        this.eventos = new ArrayList<>();
        this.pesadas = new ArrayList<>();
    }

    @PostConstruct
    public void init() {
        cargarGuiasDisponibles();

        // Verificar si se pasó el parámetro 'guiaId' por URL (GET request)
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            if (context != null && context.getExternalContext() != null) {
                Map<String, String> params = context.getExternalContext().getRequestParameterMap();
                String guiaIdParam = params.get("guiaId");
                if (guiaIdParam != null && !guiaIdParam.isBlank()) {
                    this.guiaIdSeleccionada = Long.parseLong(guiaIdParam.trim());
                }
            }
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "No se pudo leer el parámetro de request guiaId: {0}", e.getMessage());
        }

        if (this.guiaIdSeleccionada != null) {
            consultarViaje();
        } else if (!this.guiasDisponibles.isEmpty()) {
            seleccionarGuiaPorDefecto();
            consultarViaje();
        }
    }

    /**
     * Acción invocada por f:viewAction para inicializar la vista con el parámetro de vista.
     */
    public void inicializar() {
        if (guiaIdSeleccionada != null) {
            consultarViaje();
        }
    }

    /**
     * Carga el listado de todas las guías existentes en el sistema para permitir su selección.
     */
    public void cargarGuiasDisponibles() {
        try {
            List<GuiaDeViaje> lista = guiaDeViajeEJB.listarGuias();
            this.guiasDisponibles = (lista != null) ? lista : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al cargar guías de viaje disponibles", e);
            this.guiasDisponibles = new ArrayList<>();
        }
    }

    /**
     * Selecciona una guía por defecto (prioriza guías EN_CURSO, o toma la primera).
     */
    private void seleccionarGuiaPorDefecto() {
        for (GuiaDeViaje g : guiasDisponibles) {
            if (g.getEstado() == TipoEstado.EN_CURSO) {
                this.guiaIdSeleccionada = g.getId();
                return;
            }
        }
        if (!guiasDisponibles.isEmpty()) {
            this.guiaIdSeleccionada = guiasDisponibles.get(0).getId();
        }
    }

    /**
     * Listener para el cambio de selección en el dropdown de guías.
     */
    public void onGuiaChange() {
        consultarViaje();
    }

    /**
     * Consulta y carga la guía de viaje, sus eventos cronológicos y sus pesajes de balanza.
     */
    public void consultarViaje() {
        if (guiaIdSeleccionada == null) {
            this.guiaActual = null;
            this.eventos = new ArrayList<>();
            this.pesadas = new ArrayList<>();
            return;
        }

        try {
            this.guiaActual = guiaDeViajeEJB.buscarGuias(guiaIdSeleccionada);
            if (guiaActual == null) {
                mostrarMensaje(FacesMessage.SEVERITY_WARN, "Guía No Encontrada", "No existe guía de viaje con ID #" + guiaIdSeleccionada);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al buscar la guía de viaje #" + guiaIdSeleccionada, e);
            this.guiaActual = null;
        }

        try {
            List<EventoViajeDTO> evList = eventosService.listarEventosPorGuia(guiaIdSeleccionada);
            this.eventos = (evList != null) ? evList : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al obtener eventos de la guía #" + guiaIdSeleccionada, e);
            this.eventos = new ArrayList<>();
        }

        try {
            List<PesadaBalanzaDTO> pesList = balanzaService.listarPesadasPorGuia(guiaIdSeleccionada);
            this.pesadas = (pesList != null) ? pesList : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al obtener pesadas de balanza para la guía #" + guiaIdSeleccionada, e);
            this.pesadas = new ArrayList<>();
        }
    }

    /**
     * Dispara la consulta al Mock periférico de balanza y registra automáticamente la pesada,
     * actualizando los datos en tiempo real para la guía impactada.
     */
    public void sincronizarMockBalanza() {
        try {
            PesadaBalanzaDTO nuevaPesada = balanzaService.registrarPesadaDesdeMock();
            if (nuevaPesada != null) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Pesaje Sincronizado",
                        String.format("Se registró pesada para la guía #%s con %,d kg (Vehículo: %s).",
                                nuevaPesada.guiaId(),
                                nuevaPesada.pesoRegistrado(),
                                nuevaPesada.matricula() != null ? nuevaPesada.matricula() : "N/A")
                );

                if (nuevaPesada.guiaId() != null) {
                    this.guiaIdSeleccionada = nuevaPesada.guiaId();
                }
                consultarViaje();
            } else {
                mostrarMensaje(FacesMessage.SEVERITY_WARN, "Mock Balanza", "El mock de balanza no devolvió datos de pesaje.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al sincronizar pesada desde mock de balanza", e);
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error en Sincronización", e.getMessage());
        }
    }

    /**
     * Reporta un incidente en ruta asociado a la guía consultada.
     */
    public void reportarIncidente() {
        if (guiaIdSeleccionada == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe tener una guía de viaje seleccionada.");
            return;
        }

        if (nuevoIncidenteDescripcion == null || nuevoIncidenteDescripcion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "La descripción del incidente es obligatoria.");
            return;
        }

        try {
            double lat = (nuevoIncidenteLatitud != null) ? nuevoIncidenteLatitud : -34.9011;
            double lon = (nuevoIncidenteLongitud != null) ? nuevoIncidenteLongitud : -56.1645;

            ReportarIncidenteDTO dto = new ReportarIncidenteDTO(
                    UUID.randomUUID().toString(),
                    guiaIdSeleccionada,
                    LocalDateTime.now(),
                    lat,
                    lon,
                    nuevoIncidenteFoto,
                    nuevoIncidenteDescripcion
            );

            eventosService.reportarIncidente(dto);

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Incidente Reportado",
                    "El incidente fue registrado con éxito en la línea temporal de la guía #" + guiaIdSeleccionada
            );

            // Limpiar formulario y refrescar eventos
            this.nuevoIncidenteDescripcion = null;
            this.nuevoIncidenteFoto = null;
            this.nuevoIncidenteLatitud = null;
            this.nuevoIncidenteLongitud = null;

            this.eventos = eventosService.listarEventosPorGuia(guiaIdSeleccionada);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al reportar incidente", e);
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
        }
    }

    /**
     * Formatea la etiqueta representativa de una guía en el select.
     */
    public String obtenerEtiquetaGuia(GuiaDeViaje guia) {
        if (guia == null) {
            return "";
        }
        String vehiculoInfo = "Sin vehículo";
        try {
            if (guia.getVehiculo() != null && guia.getVehiculo().getMatricula() != null) {
                vehiculoInfo = guia.getVehiculo().getMatricula();
            }
        } catch (Throwable e) {
            vehiculoInfo = "Sin vehículo";
        }
        return String.format("Guía #%d - %s a %s (%s) [%s]",
                guia.getId(),
                guia.getOrigen() != null ? guia.getOrigen() : "-",
                guia.getDestino() != null ? guia.getDestino() : "-",
                vehiculoInfo,
                guia.getEstado() != null ? guia.getEstado().name() : "N/A");
    }

    public String getChoferNombreCompleto() {
        if (guiaActual == null) {
            return "No asignado";
        }
        try {
            if (guiaActual.getChofer() != null && guiaActual.getChofer().getNombre() != null) {
                String ciInfo = (guiaActual.getChofer().getCi() > 0) ? " (CI: " + guiaActual.getChofer().getCi() + ")" : "";
                return guiaActual.getChofer().getNombre() + ciInfo;
            }
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "No se pudo resolver chofer de forma eager: {0}", e.getMessage());
        }
        return "No asignado";
    }

    public String getVehiculoMatricula() {
        if (guiaActual == null) {
            return "No asignado";
        }
        try {
            if (guiaActual.getVehiculo() != null && guiaActual.getVehiculo().getMatricula() != null) {
                return guiaActual.getVehiculo().getMatricula();
            }
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "No se pudo resolver vehiculo de forma eager: {0}", e.getMessage());
        }
        return "No asignado";
    }

    public String getEmpresaRazonSocial() {
        if (guiaActual == null) {
            return "-";
        }
        try {
            if (guiaActual.getEmpresa() != null && guiaActual.getEmpresa().getRazonSocial() != null) {
                return guiaActual.getEmpresa().getRazonSocial();
            }
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "No se pudo resolver empresa de forma eager: {0}", e.getMessage());
        }
        return "-";
    }

    /**
     * Retorna la clase CSS de la badge según el tipo de evento de viaje.
     */
    public String obtenerClaseBadgeEvento(TipoEvento tipo) {
        if (tipo == null) {
            return "badge badge-otro";
        }
        return "badge badge-" + tipo.name();
    }

    /**
     * Retorna la clase CSS de la badge según el estado de la guía de viaje.
     */
    public String obtenerClaseBadgeEstado(TipoEstado estado) {
        if (estado == null) {
            return "badge badge-SIN_INICIAR";
        }
        return "badge badge-" + estado.name();
    }

    public int getTotalEventos() {
        return eventos != null ? eventos.size() : 0;
    }

    public int getTotalPesadas() {
        return pesadas != null ? pesadas.size() : 0;
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
    public Long getGuiaIdSeleccionada() {
        return guiaIdSeleccionada;
    }

    public void setGuiaIdSeleccionada(Long guiaIdSeleccionada) {
        this.guiaIdSeleccionada = guiaIdSeleccionada;
    }

    public List<GuiaDeViaje> getGuiasDisponibles() {
        return guiasDisponibles;
    }

    public void setGuiasDisponibles(List<GuiaDeViaje> guiasDisponibles) {
        this.guiasDisponibles = guiasDisponibles;
    }

    public GuiaDeViaje getGuiaActual() {
        return guiaActual;
    }

    public void setGuiaActual(GuiaDeViaje guiaActual) {
        this.guiaActual = guiaActual;
    }

    public List<EventoViajeDTO> getEventos() {
        return eventos;
    }

    public void setEventos(List<EventoViajeDTO> eventos) {
        this.eventos = eventos;
    }

    public List<PesadaBalanzaDTO> getPesadas() {
        return pesadas;
    }

    public void setPesadas(List<PesadaBalanzaDTO> pesadas) {
        this.pesadas = pesadas;
    }

    public String getNuevoIncidenteDescripcion() {
        return nuevoIncidenteDescripcion;
    }

    public void setNuevoIncidenteDescripcion(String nuevoIncidenteDescripcion) {
        this.nuevoIncidenteDescripcion = nuevoIncidenteDescripcion;
    }

    public String getNuevoIncidenteFoto() {
        return nuevoIncidenteFoto;
    }

    public void setNuevoIncidenteFoto(String nuevoIncidenteFoto) {
        this.nuevoIncidenteFoto = nuevoIncidenteFoto;
    }

    public Double getNuevoIncidenteLatitud() {
        return nuevoIncidenteLatitud;
    }

    public void setNuevoIncidenteLatitud(Double nuevoIncidenteLatitud) {
        this.nuevoIncidenteLatitud = nuevoIncidenteLatitud;
    }

    public Double getNuevoIncidenteLongitud() {
        return nuevoIncidenteLongitud;
    }

    public void setNuevoIncidenteLongitud(Double nuevoIncidenteLongitud) {
        this.nuevoIncidenteLongitud = nuevoIncidenteLongitud;
    }
}
