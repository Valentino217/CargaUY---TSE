package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.jms.TrackingProducerLocal;
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
 * incluyendo la línea temporal cronológica de eventos de ruta, simulación de telemetría vía FaaS
 * y los pesajes en balanzas periféricas.
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

    @EJB
    private TrackingProducerLocal trackingProducer;

    @EJB
    private MotorFiscalizacionLocal motorFiscalizacion;

    @Inject
    private FaasTrackingClient faasTrackingClient;

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

    // Campos para el simulador de telemetría GPS vía FaaS (Cloud)
    private String faasEndpointUrl;
    private Double telemetriaLatitud;
    private Double telemetriaLongitud;
    private String telemetriaDescripcion;
    private boolean evaluarDesvioInmediato = true;
    private String ultimoResultadoFaas;

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
        if (faasTrackingClient != null) {
            this.faasEndpointUrl = faasTrackingClient.getEndpointUrl();
        }
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
            if (lista != null) {
                this.guiasDisponibles = lista;
            } else {
                this.guiasDisponibles = new ArrayList<>();
            }
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
            if (evList != null) {
                this.eventos = evList;
            } else {
                this.eventos = new ArrayList<>();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al obtener eventos de la guía #" + guiaIdSeleccionada, e);
            this.eventos = new ArrayList<>();
        }

        try {
            List<PesadaBalanzaDTO> pesList = balanzaService.listarPesadasPorGuia(guiaIdSeleccionada);
            if (pesList != null) {
                this.pesadas = pesList;
            } else {
                this.pesadas = new ArrayList<>();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al obtener pesadas de balanza para la guía #" + guiaIdSeleccionada, e);
            this.pesadas = new ArrayList<>();
        }
    }

    /**
     * Precarga en el simulador FaaS unas coordenadas sobre el corredor Origen -> Destino (sin desvío).
     */
    public void cargarCoordenadasEnRuta() {
        FaasTrackingClient cliente = obtenerClienteFaas();
        String origen = "Montevideo";
        String destino = "Rivera";
        if (guiaActual != null) {
            if (guiaActual.getOrigen() != null) {
                origen = guiaActual.getOrigen();
            }
            if (guiaActual.getDestino() != null) {
                destino = guiaActual.getDestino();
            }
        }
        double[] coords = cliente.obtenerPuntoMedioEnRuta(origen, destino);
        this.telemetriaLatitud = coords[0];
        this.telemetriaLongitud = coords[1];
        this.telemetriaDescripcion = "Posición GPS en corredor normal (" + origen + " -> " + destino + ")";
    }

    /**
     * Precarga en el simulador FaaS unas coordenadas desviadas del corredor Origen -> Destino (genera infracción).
     */
    public void cargarCoordenadasDesviadas() {
        FaasTrackingClient cliente = obtenerClienteFaas();
        String origen = "Montevideo";
        String destino = "Rivera";
        if (guiaActual != null) {
            if (guiaActual.getOrigen() != null) {
                origen = guiaActual.getOrigen();
            }
            if (guiaActual.getDestino() != null) {
                destino = guiaActual.getDestino();
            }
        }
        double[] coords = cliente.obtenerPuntoDesviadoDeRuta(origen, destino);
        this.telemetriaLatitud = coords[0];
        this.telemetriaLongitud = coords[1];
        this.telemetriaDescripcion = "Desvío detectado por telemetría GPS fuera del corredor (" + origen + " -> " + destino + ")";
    }

    /**
     * Invoca la función FaaS (Cloud HTTPS) para procesar las coordenadas del evento, calcular la distancia
     * de desvío en kilómetros y enviarlo al flujo de TrackingQueue y MotorFiscalizacion.
     */
    public void enviarTelemetriaFaas() {
        if (guiaIdSeleccionada == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe seleccionar una guía de viaje.");
            return;
        }
        if (telemetriaLatitud == null || telemetriaLongitud == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe indicar latitud y longitud (o usar los botones de simulación).");
            return;
        }

        try {
            FaasTrackingClient cliente = obtenerClienteFaas();
            if (faasEndpointUrl != null) {
                cliente.setEndpointUrl(faasEndpointUrl);
            }

            String desc = telemetriaDescripcion;
            if (desc == null || desc.isBlank()) {
                desc = "Telemetría GPS procesada vía FaaS";
            }

            EventoViajeDTO eventoBase = new EventoViajeDTO(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    TipoEvento.INCIDENTE,
                    telemetriaLatitud,
                    telemetriaLongitud,
                    guiaIdSeleccionada,
                    new IncidenteDTO(null, desc),
                    null
            );

            String origen = "Montevideo";
            String destino = "Rivera";
            if (guiaActual != null) {
                if (guiaActual.getOrigen() != null) {
                    origen = guiaActual.getOrigen();
                }
                if (guiaActual.getDestino() != null) {
                    destino = guiaActual.getDestino();
                }
            }

            FaasTrackingClient.ResultadoFaas resultadoFaas = cliente.procesarEvento(eventoBase, origen, destino);
            EventoViajeDTO eventoEnriquecido = resultadoFaas.eventoEnriquecido();

            if (evaluarDesvioInmediato) {
                eventosService.guardarEvento(eventoEnriquecido);
                List<TipoInfraccion> nuevasInfracciones = new ArrayList<>();
                if (motorFiscalizacion != null) {
                    nuevasInfracciones = motorFiscalizacion.evaluarGuia(guiaIdSeleccionada);
                }

                this.ultimoResultadoFaas = "Desvío calculado por FaaS: " + resultadoFaas.distanciaDesvioKm()
                        + " km (" + resultadoFaas.detalleEjecucion() + ").";

                if (nuevasInfracciones.contains(TipoInfraccion.DESVIO_DE_RUTA)) {
                    mostrarMensaje(
                            FacesMessage.SEVERITY_WARN,
                            "Infracción por Desvío Detectada",
                            "FaaS calculó un desvío de " + resultadoFaas.distanciaDesvioKm()
                                    + " km. Se generó infracción automática: " + nuevasInfracciones
                    );
                } else {
                    mostrarMensaje(
                            FacesMessage.SEVERITY_INFO,
                            "Telemetría FaaS Procesada",
                            "FaaS calculó un desvío de " + resultadoFaas.distanciaDesvioKm()
                                    + " km. No se generaron nuevas infracciones en esta evaluación."
                    );
                }
            } else {
                if (trackingProducer != null) {
                    trackingProducer.enviarEvento(eventoEnriquecido);
                } else {
                    eventosService.guardarEvento(eventoEnriquecido);
                }
                this.ultimoResultadoFaas = "Desvío calculado por FaaS: " + resultadoFaas.distanciaDesvioKm()
                        + " km. Evento encolado asíncronamente en TrackingQueue.";
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Encolado en TrackingQueue",
                        this.ultimoResultadoFaas
                );
            }

            consultarViaje();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al procesar telemetría vía FaaS", e);
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error en FaaS / Tracking", e.getMessage());
        }
    }

    private FaasTrackingClient obtenerClienteFaas() {
        if (faasTrackingClient == null) {
            faasTrackingClient = new FaasTrackingClient();
        }
        return faasTrackingClient;
    }

    /**
     * Dispara la consulta al Mock periférico de balanza y registra automáticamente la pesada,
     * actualizando los datos en tiempo real para la guía impactada.
     */
    public void sincronizarMockBalanza() {
        try {
            PesadaBalanzaDTO nuevaPesada = balanzaService.registrarPesadaDesdeMock();
            if (nuevaPesada != null) {
                String matricula = "N/A";
                if (nuevaPesada.matricula() != null) {
                    matricula = nuevaPesada.matricula();
                }
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Pesaje Sincronizado",
                        String.format("Se registró pesada para la guía #%s con %,d kg (Vehículo: %s).",
                                nuevaPesada.guiaId(),
                                nuevaPesada.pesoRegistrado(),
                                matricula)
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
            double lat = -34.9011;
            if (nuevoIncidenteLatitud != null) {
                lat = nuevoIncidenteLatitud;
            }
            double lon = -56.1645;
            if (nuevoIncidenteLongitud != null) {
                lon = nuevoIncidenteLongitud;
            }

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
            if (motorFiscalizacion != null) {
                motorFiscalizacion.evaluarGuia(guiaIdSeleccionada);
            }

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
        String origenStr = "-";
        if (guia.getOrigen() != null) {
            origenStr = guia.getOrigen();
        }
        String destinoStr = "-";
        if (guia.getDestino() != null) {
            destinoStr = guia.getDestino();
        }
        String estadoStr = "N/A";
        if (guia.getEstado() != null) {
            estadoStr = guia.getEstado().name();
        }
        return String.format("Guía #%d - %s a %s (%s) [%s]",
                guia.getId(),
                origenStr,
                destinoStr,
                vehiculoInfo,
                estadoStr);
    }

    public String getChoferNombreCompleto() {
        if (guiaActual == null) {
            return "No asignado";
        }
        try {
            if (guiaActual.getChofer() != null && guiaActual.getChofer().getNombre() != null) {
                String ciInfo = "";
                if (guiaActual.getChofer().getCi() > 0) {
                    ciInfo = " (CI: " + guiaActual.getChofer().getCi() + ")";
                }
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
        if (eventos != null) {
            return eventos.size();
        }
        return 0;
    }

    public int getTotalPesadas() {
        if (pesadas != null) {
            return pesadas.size();
        }
        return 0;
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

    public String getFaasEndpointUrl() {
        return faasEndpointUrl;
    }

    public void setFaasEndpointUrl(String faasEndpointUrl) {
        this.faasEndpointUrl = faasEndpointUrl;
    }

    public Double getTelemetriaLatitud() {
        return telemetriaLatitud;
    }

    public void setTelemetriaLatitud(Double telemetriaLatitud) {
        this.telemetriaLatitud = telemetriaLatitud;
    }

    public Double getTelemetriaLongitud() {
        return telemetriaLongitud;
    }

    public void setTelemetriaLongitud(Double telemetriaLongitud) {
        this.telemetriaLongitud = telemetriaLongitud;
    }

    public String getTelemetriaDescripcion() {
        return telemetriaDescripcion;
    }

    public void setTelemetriaDescripcion(String telemetriaDescripcion) {
        this.telemetriaDescripcion = telemetriaDescripcion;
    }

    public boolean isEvaluarDesvioInmediato() {
        return evaluarDesvioInmediato;
    }

    public void setEvaluarDesvioInmediato(boolean evaluarDesvioInmediato) {
        this.evaluarDesvioInmediato = evaluarDesvioInmediato;
    }

    public String getUltimoResultadoFaas() {
        return ultimoResultadoFaas;
    }
}
