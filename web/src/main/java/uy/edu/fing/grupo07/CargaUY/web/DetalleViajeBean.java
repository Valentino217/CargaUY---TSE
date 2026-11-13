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
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionNodosPerifericosServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;
import uy.edu.fing.grupo07.CargaUY.service.TrackingMockServiceLocal;

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

    @EJB
    private PosicionNoSqlRepositoryLocal posicionNoSqlRepository;

    @EJB
    private TrackingMockServiceLocal trackingMockService;

    @EJB
    private GestionNodosPerifericosServiceLocal nodosPerifericosService;

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
    private String telemetriaMatricula;
    private Double telemetriaLatitud;
    private Double telemetriaLongitud;
    private String telemetriaDescripcion;
    private boolean evaluarDesvioInmediato = true;
    private String ultimoResultadoFaas;

    // Campos de configuración del Mock de Sistema de Tracking
    private Integer mockEventosPorMinuto = 12;
    private Integer mockCantidadDesvios = 0;

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
        if (trackingMockService != null) {
            this.mockEventosPorMinuto = trackingMockService.getEventosPorMinuto();
            this.mockCantidadDesvios = trackingMockService.getCantidadDesvios();
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
     * Sincroniza el estado con el nodo periférico de tracking bajo demanda al abrir el modal.
     */
    public void prepararModalMockTracking() {
        if (trackingMockService != null) {
            trackingMockService.sincronizarConNodoPeriferico(SesionActual.token());
            this.mockEventosPorMinuto = trackingMockService.getEventosPorMinuto();
            this.mockCantidadDesvios = trackingMockService.getCantidadDesvios();
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

    private boolean esVistaEmpresa() {
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            if (context != null && context.getExternalContext() != null) {
                Object reqObj = context.getExternalContext().getRequest();
                if (reqObj instanceof jakarta.servlet.http.HttpServletRequest req) {
                    String uri = req.getRequestURI();
                    if (uri != null && uri.contains("/empresa/")) {
                        return true;
                    }
                    Object actorObj = req.getAttribute("actor");
                    if (actorObj instanceof uy.edu.fing.grupo07.CargaUY.security.Acceso actor) {
                        return actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
                                && !actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO)
                                && !actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD)
                                && !actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Carga el listado de todas las guías existentes en el sistema (o las de las empresas del actor en vista empresa) para permitir su selección.
     */
    public void cargarGuiasDisponibles() {
        try {
            String token = SesionActual.token();
            List<GuiaDeViaje> lista = esVistaEmpresa()
                    ? guiaDeViajeEJB.listarParaActor(token)
                    : guiaDeViajeEJB.listarParaOperador(token);
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

        if (esVistaEmpresa()) {
            if (this.guiasDisponibles == null || this.guiasDisponibles.isEmpty()) {
                cargarGuiasDisponibles();
            }
            boolean permitida = this.guiasDisponibles != null && this.guiasDisponibles.stream()
                    .anyMatch(g -> g != null && g.getId() != null && g.getId().equals(guiaIdSeleccionada));
            if (!permitida) {
                this.guiaActual = null;
                this.eventos = new ArrayList<>();
                this.pesadas = new ArrayList<>();
                mostrarMensaje(FacesMessage.SEVERITY_WARN, "Acceso Restringido", "La guía seleccionada no pertenece a su empresa.");
                return;
            }
        }

        String token = SesionActual.token();
        try {
            this.guiaActual = guiaDeViajeEJB.buscarGuias(token, guiaIdSeleccionada);
            if (guiaActual == null) {
                mostrarMensaje(FacesMessage.SEVERITY_WARN, "Guía No Encontrada", "No existe guía de viaje con ID #" + guiaIdSeleccionada);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al buscar la guía de viaje #" + guiaIdSeleccionada, e);
            this.guiaActual = null;
        }

        try {
            List<EventoViajeDTO> evList = eventosService.listarEventosPorGuia(token, guiaIdSeleccionada);
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
            List<PesadaBalanzaDTO> pesList = balanzaService.listarPesadasPorGuia(token, guiaIdSeleccionada);
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

    private GuiaDeViaje buscarGuiaActivaPorMatricula(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null;
        }
        String buscada = matricula.trim();
        List<GuiaDeViaje> candidatas = this.guiasDisponibles;
        if (guiaDeViajeEJB != null) {
            try {
                String token = SesionActual.token();
                List<GuiaDeViaje> frescas = esVistaEmpresa()
                        ? guiaDeViajeEJB.listarParaActor(token)
                        : guiaDeViajeEJB.listarParaOperador(token);
                if (frescas != null && !frescas.isEmpty()) {
                    this.guiasDisponibles = frescas;
                    candidatas = frescas;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "No se pudo refrescar guías al resolver matrícula {0}: {1}",
                        new Object[]{buscada, e.getMessage()});
            }
        }
        if (candidatas != null) {
            for (GuiaDeViaje g : candidatas) {
                if (g != null
                        && g.getEstado() == TipoEstado.EN_CURSO
                        && g.getVehiculo() != null
                        && g.getVehiculo().getMatricula() != null
                        && g.getVehiculo().getMatricula().trim().equalsIgnoreCase(buscada)) {
                    return g;
                }
            }
        }
        return null;
    }

    /**
     * Precarga en el simulador FaaS unas coordenadas sobre el corredor Origen -> Destino (sin desvío).
     */
    public void cargarCoordenadasEnRuta() {
        FaasTrackingClient cliente = obtenerClienteFaas();
        boolean tieneMatriculaManual = telemetriaMatricula != null && !telemetriaMatricula.isBlank();
        GuiaDeViaje guiaRef = tieneMatriculaManual ? buscarGuiaActivaPorMatricula(telemetriaMatricula) : guiaActual;
        String origen = "Montevideo";
        String destino = "Rivera";
        if (guiaRef != null) {
            if (guiaRef.getOrigen() != null && !guiaRef.getOrigen().isBlank()) {
                origen = guiaRef.getOrigen();
            }
            if (guiaRef.getDestino() != null && !guiaRef.getDestino().isBlank()) {
                destino = guiaRef.getDestino();
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
        boolean tieneMatriculaManual = telemetriaMatricula != null && !telemetriaMatricula.isBlank();
        GuiaDeViaje guiaRef = tieneMatriculaManual ? buscarGuiaActivaPorMatricula(telemetriaMatricula) : guiaActual;
        String origen = "Montevideo";
        String destino = "Rivera";
        if (guiaRef != null) {
            if (guiaRef.getOrigen() != null && !guiaRef.getOrigen().isBlank()) {
                origen = guiaRef.getOrigen();
            }
            if (guiaRef.getDestino() != null && !guiaRef.getDestino().isBlank()) {
                destino = guiaRef.getDestino();
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
        boolean tieneMatriculaManual = telemetriaMatricula != null && !telemetriaMatricula.isBlank();
        if (guiaIdSeleccionada == null && !tieneMatriculaManual) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe seleccionar una guía de viaje o ingresar la matrícula del vehículo.");
            return;
        }
        if (telemetriaLatitud == null || telemetriaLongitud == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe indicar latitud y longitud (o usar los botones de simulación).");
            return;
        }

        String matriculaEfectiva = tieneMatriculaManual
                ? telemetriaMatricula.trim()
                : (guiaActual != null && guiaActual.getVehiculo() != null ? guiaActual.getVehiculo().getMatricula() : null);
        GuiaDeViaje guiaReferencia = tieneMatriculaManual
                ? buscarGuiaActivaPorMatricula(matriculaEfectiva)
                : guiaActual;
        Long guiaIdDto = tieneMatriculaManual ? null : guiaIdSeleccionada;

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
                    TipoEvento.POSICION,
                    telemetriaLatitud,
                    telemetriaLongitud,
                    guiaIdDto,
                    new IncidenteDTO(null, desc),
                    null,
                    null,
                    null,
                    null,
                    matriculaEfectiva
            );

            String origen = "Montevideo";
            String destino = "Rivera";
            if (guiaReferencia != null) {
                if (guiaReferencia.getOrigen() != null && !guiaReferencia.getOrigen().isBlank()) {
                    origen = guiaReferencia.getOrigen();
                }
                if (guiaReferencia.getDestino() != null && !guiaReferencia.getDestino().isBlank()) {
                    destino = guiaReferencia.getDestino();
                }
            }

            FaasTrackingClient.ResultadoFaas resultadoFaas = cliente.procesarEvento(
                    eventoBase,
                    origen,
                    destino,
                    guiaReferencia != null ? guiaReferencia.getPolilineaRuta() : null
            );
            EventoViajeDTO eFaas = resultadoFaas.eventoEnriquecido();
            EventoViajeDTO eventoEnriquecido = new EventoViajeDTO(
                    eFaas.uuid(),
                    eFaas.tiempo(),
                    eFaas.tipo(),
                    eFaas.latitud(),
                    eFaas.longitud(),
                    eFaas.guiaId(),
                    eFaas.incidente(),
                    eFaas.distanciaDesvioKm(),
                    eFaas.cantidadKg(),
                    eFaas.mercaderia(),
                    eFaas.empresaCarga(),
                    eFaas.matricula() != null ? eFaas.matricula() : matriculaEfectiva
            );

            if (evaluarDesvioInmediato) {
                uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje eventoPersistido;
                try {
                    eventoPersistido = eventosService.guardarEvento(SesionActual.token(), eventoEnriquecido);
                } catch (uy.edu.fing.grupo07.CargaUY.exception.BusinessException be) {
                    boolean vehiculoInexistente = be.getMessage() != null
                            && be.getMessage().contains("No existe vehículo registrado");
                    if (!vehiculoInexistente
                            && matriculaEfectiva != null && !matriculaEfectiva.isBlank()
                            && (guiaIdDto == null || guiaActual == null || guiaActual.getEstado() != TipoEstado.EN_CURSO)) {
                        if (motorFiscalizacion != null) {
                            motorFiscalizacion.evaluarVehiculoSinGuia(
                                    matriculaEfectiva,
                                    "Telemetría GPS FaaS (" + telemetriaLatitud + ", " + telemetriaLongitud + ") sin viaje EN_CURSO"
                            );
                        }
                        this.ultimoResultadoFaas = "Actividad GPS detectada para matrícula " + matriculaEfectiva
                                + " sin viaje EN_CURSO. Derivado a fiscalización automática VIAJE_SIN_GUIA.";
                        mostrarMensaje(
                                FacesMessage.SEVERITY_WARN,
                                "Fiscalización VIAJE_SIN_GUIA",
                                this.ultimoResultadoFaas
                        );
                        return;
                    }
                    throw be;
                }

                Long guiaEvaluar = (eventoPersistido != null && eventoPersistido.getGuia() != null)
                        ? eventoPersistido.getGuia().getId()
                        : (eventoEnriquecido.guiaId() != null
                                ? eventoEnriquecido.guiaId()
                                : (guiaReferencia != null ? guiaReferencia.getId() : (!tieneMatriculaManual ? guiaIdSeleccionada : null)));
                if (guiaEvaluar != null) {
                    this.guiaIdSeleccionada = guiaEvaluar;
                }

                List<TipoInfraccion> nuevasInfracciones = new ArrayList<>();
                if (motorFiscalizacion != null && guiaEvaluar != null) {
                    nuevasInfracciones = motorFiscalizacion.evaluarGuia(guiaEvaluar);
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
                    uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje persistido = eventosService.guardarEvento(SesionActual.token(), eventoEnriquecido);
                    if (persistido != null && persistido.getGuia() != null) {
                        this.guiaIdSeleccionada = persistido.getGuia().getId();
                    }
                }
                if (guiaReferencia != null && guiaReferencia.getId() != null) {
                    this.guiaIdSeleccionada = guiaReferencia.getId();
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
     * Consulta (request-response) las pesadas registradas en el nodo periférico de balanzas
     * para la guía seleccionada y refresca los datos en pantalla.
     */
    public void consultarBalanzasGuia() {
        if (guiaIdSeleccionada == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Debe seleccionar una guía de viaje para consultar balanzas.");
            return;
        }
        try {
            String correlacion = UUID.randomUUID().toString();
            List<PesadaBalanzaDTO> importadas = balanzaService.sincronizarPesadasDeGuia(
                    SesionActual.token(), guiaIdSeleccionada, correlacion);
            consultarViaje();
            if (importadas == null || importadas.isEmpty()) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Sin Pesadas Nuevas",
                        "La guía #" + guiaIdSeleccionada + " ya se encuentra sincronizada con el nodo de balanzas."
                );
            } else {
                mostrarMensaje(
                        FacesMessage.SEVERITY_INFO,
                        "Pesadas Sincronizadas",
                        String.format("Se importaron %d pesada(s) desde el nodo periférico para la guía #%d.",
                                importadas.size(), guiaIdSeleccionada)
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al consultar balanzas para guía #" + guiaIdSeleccionada, e);
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error en Balanzas", e.getMessage());
        }
    }

    /**
     * Dispara la consulta al Mock periférico de balanza y registra automáticamente la pesada,
     * actualizando los datos en tiempo real para la guía impactada.
     */
    public void sincronizarMockBalanza() {
        try {
            PesadaBalanzaDTO nuevaPesada = balanzaService.registrarPesadaDesdeMock(
                    SesionActual.token(), guiaIdSeleccionada);
            if (nuevaPesada != null) {
                String matricula = "N/A";
                if (nuevaPesada.matricula() != null) {
                    matricula = nuevaPesada.matricula();
                }
                if (nuevaPesada.guiaId() != null) {
                    mostrarMensaje(
                            FacesMessage.SEVERITY_INFO,
                            "Pesaje Sincronizado",
                            String.format("Se registró pesada para la guía #%d con %,d kg (Vehículo: %s).",
                                    nuevaPesada.guiaId(),
                                    nuevaPesada.pesoRegistrado(),
                                    matricula)
                    );
                    this.guiaIdSeleccionada = nuevaPesada.guiaId();
                } else {
                    mostrarMensaje(
                            FacesMessage.SEVERITY_WARN,
                            "Pesaje sin Guía Activa",
                            String.format("Se registró pesada de %,d kg para el vehículo %s sin guía EN_CURSO. Se encoló fiscalización VIAJE_SIN_GUIA.",
                                    nuevaPesada.pesoRegistrado(),
                                    matricula)
                    );
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

            String token = SesionActual.token();
            eventosService.reportarIncidente(token, dto);
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

            this.eventos = eventosService.listarEventosPorGuia(token, guiaIdSeleccionada);
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

    public boolean isMongoNoSqlOperativo() {
        return posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo();
    }

    public long getTotalEventosNoSqlGuia() {
        if (posicionNoSqlRepository != null && guiaIdSeleccionada != null) {
            return posicionNoSqlRepository.contarEventosPorGuia(guiaIdSeleccionada);
        }
        return 0L;
    }

    public String getDescriptorMongoNoSql() {
        if (posicionNoSqlRepository != null) {
            return posicionNoSqlRepository.obtenerDescriptorEndpoint();
        }
        return "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";
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

    public String getTelemetriaMatricula() {
        return telemetriaMatricula;
    }

    public void setTelemetriaMatricula(String telemetriaMatricula) {
        this.telemetriaMatricula = telemetriaMatricula;
    }

    void setMotorFiscalizacion(MotorFiscalizacionLocal motorFiscalizacion) {
        this.motorFiscalizacion = motorFiscalizacion;
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
    public void iniciarMockTracking() {
        if (trackingMockService == null) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "El servicio Mock de Tracking no está disponible.");
            return;
        }
        int epm = mockEventosPorMinuto != null && mockEventosPorMinuto > 0 ? mockEventosPorMinuto : 12;
        int desvios = mockCantidadDesvios != null && mockCantidadDesvios >= 0 ? mockCantidadDesvios : 0;
        this.mockEventosPorMinuto = epm;
        this.mockCantidadDesvios = desvios;

        trackingMockService.iniciar(SesionActual.token(), epm, desvios);
        consultarViaje();
        mostrarMensaje(
                FacesMessage.SEVERITY_INFO,
                "Mock de Tracking Iniciado",
                String.format("Emitiendo %d evento(s)/minuto para las guías EN_CURSO (%d desvío(s) configurado(s)).", epm, desvios)
        );
    }

    public void detenerMockTracking() {
        if (trackingMockService == null) {
            return;
        }
        trackingMockService.detener(SesionActual.token());
        consultarViaje();
        mostrarMensaje(
                FacesMessage.SEVERITY_INFO,
                "Mock de Tracking Detenido",
                "Se detuvo el envío automático de puntos de posición."
        );
    }

    public void emitirPasoMockAhora() {
        if (trackingMockService == null) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "El servicio Mock de Tracking no está disponible.");
            return;
        }
        int emitidos = trackingMockService.emitirPasoGuiasActivas(SesionActual.token());
        consultarViaje();
        if (emitidos > 0) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Paso de Tracking Emitido",
                    trackingMockService.getUltimoResultado()
            );
        } else {
            mostrarMensaje(
                    FacesMessage.SEVERITY_WARN,
                    "Sin Guías Activas",
                    "No se encontraron guías en estado EN_CURSO para emitir posiciones."
            );
        }
    }

    public void reiniciarRecorridoMock() {
        if (trackingMockService == null) {
            return;
        }
        trackingMockService.reiniciarRecorridos(SesionActual.token());
        mostrarMensaje(
                FacesMessage.SEVERITY_INFO,
                "Recorrido Reiniciado",
                "El progreso de ruta del Mock volvió al punto de origen para las guías activas."
        );
    }

    public void refrescarPorPollMock() {
        if (guiaIdSeleccionada != null) {
            consultarViaje();
        }
    }

    public boolean isPollAutomaticoActivo() {
        return guiaActual != null && guiaActual.getEstado() == TipoEstado.EN_CURSO;
    }

    public boolean isNodoTrackingActivoSistema() {
        if (nodosPerifericosService != null) {
            return nodosPerifericosService.isTrackingHabilitado();
        }
        return isMockTrackingActivo();
    }

    public int getCantidadBalanzasActivas() {
        if (nodosPerifericosService != null) {
            return nodosPerifericosService.listarBalanzasHabilitadas().size();
        }
        return 0;
    }

    public String getResumenModoTracking() {
        if (nodosPerifericosService == null) {
            return "Estándar";
        }
        return nodosPerifericosService.getModoTrackingActivo().getEtiqueta();
    }

    public boolean isNodoTrackingOnline() {
        return trackingMockService != null && trackingMockService.isNodoPerifericoOnline();
    }

    public String getNodoTrackingEndpoint() {
        return trackingMockService != null ? trackingMockService.getEndpointNodoPeriferico() : "http://localhost:8085";
    }

    public boolean isMockTrackingActivo() {
        return trackingMockService != null && trackingMockService.estaActivo();
    }

    public long getMockTotalEventosEmitidos() {
        return trackingMockService != null ? trackingMockService.getTotalEventosEmitidos() : 0L;
    }

    public long getMockTotalDesviosEmitidos() {
        return trackingMockService != null ? trackingMockService.getTotalDesviosEmitidos() : 0L;
    }

    public String getMockUltimoResultado() {
        return trackingMockService != null ? trackingMockService.getUltimoResultado() : "Mock detenido.";
    }

    public Integer getMockEventosPorMinuto() {
        return mockEventosPorMinuto;
    }

    public void setMockEventosPorMinuto(Integer mockEventosPorMinuto) {
        this.mockEventosPorMinuto = mockEventosPorMinuto;
    }

    public Integer getMockCantidadDesvios() {
        return mockCantidadDesvios;
    }

    public void setMockCantidadDesvios(Integer mockCantidadDesvios) {
        this.mockCantidadDesvios = mockCantidadDesvios;
    }

    void setTrackingMockService(TrackingMockServiceLocal trackingMockService) {
        this.trackingMockService = trackingMockService;
    }

    void setNodosPerifericosService(GestionNodosPerifericosServiceLocal nodosPerifericosService) {
        this.nodosPerifericosService = nodosPerifericosService;
    }
}
