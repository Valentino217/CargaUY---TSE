package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.DependsOn;
import jakarta.ejb.EJB;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.integration.RedVialUruguay;
import uy.edu.fing.grupo07.CargaUY.integration.TrackingClient;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio coordinador del Nodo Periférico Descentralizado de Tracking GPS (mock-tracking)
 * para guías de viaje activas (EN_CURSO).
 * <p>
 * Se comunica vía HTTP REST con el contenedor periférico {@code mock-tracking} para activar,
 * pausar, reiniciar o avanzar la simulación de recorridos viales y drenar de forma idempotente
 * los puntos emitidos por el nodo periférico hacia la persistencia (MongoDB/PostgreSQL) y el
 * motor de fiscalización. Incluye respaldo local transparente para entornos de pruebas unitarias
 * sin contenedor activo.
 * </p>
 */
@Singleton
@Startup
@DependsOn("GestionNodosPerifericosServiceImpl")
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class TrackingMockServiceImpl implements TrackingMockServiceLocal {

    private static final Logger LOGGER = Logger.getLogger(TrackingMockServiceImpl.class.getName());

    private static final int EVENTOS_POR_MINUTO_DEFECTO = 12;
    private static final int MIN_EVENTOS_POR_MINUTO = 1;
    private static final int MAX_EVENTOS_POR_MINUTO = 120;
    private static final int PASOS_RUTA_BASE = 10;

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @EJB
    private GestionEventosServiceLocal eventosService;

    @EJB
    private MotorFiscalizacionLocal motorFiscalizacion;

    @EJB
    private GestionNodosPerifericosServiceLocal nodosPerifericosService;

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private ControlAcceso control;

    private void verificarControl() {
        if (control == null) {
            throw new IllegalStateException("ControlAcceso no disponible");
        }
    }

    @Override
    public void iniciar(String token, int eventosPorMinuto, int cantidadDesvios) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        iniciar(eventosPorMinuto, cantidadDesvios);
    }

    @Override
    public void detener(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        detener();
    }

    @Override
    public void reiniciarRecorridos(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        reiniciarRecorridos();
    }

    @Override
    public int emitirPasoGuiasActivas(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        return emitirPasoGuiasActivas();
    }

    @Override
    public void sincronizarConNodoPeriferico(String token) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO, TipoUsuario.AUTORIDAD);
        sincronizarConNodoPeriferico();
    }

    @Override
    public void notificarCambioConfiguracionNodos(String token) {
        verificarControl();
        control.admin(token);
        notificarCambioConfiguracionNodos();
    }

    @Override
    public void notificarCambioBalanza(String token) {
        verificarControl();
        control.admin(token);
        notificarCambioBalanza();
    }

    @Inject
    private FaasTrackingClient faasTrackingClient;

    @Inject
    private TrackingClient trackingClient;

    @Resource
    private SessionContext sessionContext;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tareaProgramada;
    private ScheduledFuture<?> tareaAutomatica;

    private volatile boolean activo = false;
    private volatile boolean nodoPerifericoOnline = false;
    private volatile int eventosPorMinuto = EVENTOS_POR_MINUTO_DEFECTO;
    private volatile int cantidadDesvios = 0;
    private volatile String ultimoResultado = "Mock detenido.";
    private final AtomicLong contadorTicksAutomaticos = new AtomicLong(0L);
    private final java.util.concurrent.atomic.AtomicBoolean ejecutandoCicloAuto = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean ejecutandoBalanzaAuto = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final AtomicLong totalEventosEmitidos = new AtomicLong();
    private final AtomicLong totalDesviosEmitidos = new AtomicLong();
    private final Map<Long, EstadoSimulacionGuia> estadosPorGuia = new ConcurrentHashMap<>();
    private final Set<Long> guiasInicializadasEnSesion = ConcurrentHashMap.newKeySet();
    private final Set<Long> guiasConMockPersistido = ConcurrentHashMap.newKeySet();

    public record PasoSimulado(
            double latitud,
            double longitud,
            boolean esInicioEpisodioDesvio,
            boolean esDesvio,
            boolean enDestino,
            String descripcion
    ) {
    }

    static final class EstadoSimulacionGuia {
        private final Long guiaId;
        private final int desviosConfigurados;
        private final List<PasoSimulado> planPasos;
        private int indiceActual = 0;

        EstadoSimulacionGuia(Long guiaId, int desviosConfigurados, List<PasoSimulado> planPasos) {
            this.guiaId = guiaId;
            this.desviosConfigurados = desviosConfigurados;
            this.planPasos = planPasos;
        }

        synchronized PasoSimulado siguientePaso() {
            if (planPasos.isEmpty()) {
                return new PasoSimulado(-34.9011, -56.1645, false, false, true, "Mock Tracking GPS - Posición en destino");
            }
            if (indiceActual < planPasos.size() - 1) {
                PasoSimulado paso = planPasos.get(indiceActual);
                indiceActual++;
                return paso;
            }
            // Una vez alcanzado el último paso (destino), se queda tirando puntos en el lugar de finalización
            return planPasos.get(planPasos.size() - 1);
        }

        int getDesviosConfigurados() {
            return desviosConfigurados;
        }
    }

    public TrackingMockServiceImpl() {
    }

    public TrackingMockServiceImpl(
            EntityManager em,
            GestionEventosServiceLocal eventosService,
            MotorFiscalizacionLocal motorFiscalizacion,
            FaasTrackingClient faasTrackingClient
    ) {
        this.em = em;
        this.eventosService = eventosService;
        this.motorFiscalizacion = motorFiscalizacion;
        this.faasTrackingClient = faasTrackingClient;
    }

    public TrackingMockServiceImpl(
            EntityManager em,
            GestionEventosServiceLocal eventosService,
            MotorFiscalizacionLocal motorFiscalizacion,
            FaasTrackingClient faasTrackingClient,
            TrackingClient trackingClient
    ) {
        this.em = em;
        this.eventosService = eventosService;
        this.motorFiscalizacion = motorFiscalizacion;
        this.faasTrackingClient = faasTrackingClient;
        this.trackingClient = trackingClient;
    }

    void setNodosPerifericosService(GestionNodosPerifericosServiceLocal nodosPerifericosService) {
        this.nodosPerifericosService = nodosPerifericosService;
    }

    void setBalanzaService(GestionBalanzaServiceLocal balanzaService) {
        this.balanzaService = balanzaService;
    }

    @PostConstruct
    public void iniciarSchedulerAutomatico() {
        if (nodosPerifericosService != null) {
            this.activo = nodosPerifericosService.isTrackingHabilitado();
            this.cantidadDesvios = (nodosPerifericosService.getModoTrackingActivo() == ModoNodoPeriferico.CON_DESVIO) ? 1 : 0;
            if (trackingClient != null) {
                trackingClient.setEndpointUrl(nodosPerifericosService.getEndpointTrackingActivo());
            }
        }
        asegurarScheduler();
        if (tareaAutomatica == null || tareaAutomatica.isCancelled()) {
            tareaAutomatica = scheduler.scheduleWithFixedDelay(
                    this::ejecutarCicloAutomaticoSeguro,
                    6000L,
                    5000L,
                    TimeUnit.MILLISECONDS
            );
        }
        LOGGER.info("Coordinador periódico automático de Nodos Periféricos (Tracking + Balanzas) inicializado.");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public synchronized void iniciar(int nuevoEventosPorMinuto, int nuevaCantidadDesvios) {
        int epmNormalizado = Math.max(MIN_EVENTOS_POR_MINUTO, Math.min(MAX_EVENTOS_POR_MINUTO, nuevoEventosPorMinuto));
        int desviosNormalizados = Math.max(0, nuevaCantidadDesvios);

        boolean cambioDesvios = this.cantidadDesvios != desviosNormalizados;
        this.eventosPorMinuto = epmNormalizado;
        this.cantidadDesvios = desviosNormalizados;

        if (cambioDesvios) {
            estadosPorGuia.clear();
        }

        long intervaloMs = Math.max(500L, Math.round(60_000.0 / this.eventosPorMinuto));

        if (trackingClient != null) {
            try {
                List<GuiaDeViaje> guiasActivas = buscarGuiasEnCurso();
                String faasUrl = faasTrackingClient != null ? faasTrackingClient.getEndpointUrl() : "";
                TrackingClient.EstadoNodoTracking estadoRemoto = trackingClient.iniciar(
                        this.eventosPorMinuto,
                        this.cantidadDesvios,
                        construirPayloadGuias(guiasActivas),
                        faasUrl,
                        UUID.randomUUID().toString()
                );
                this.nodoPerifericoOnline = true;
                procesarEventosDePeriferico(estadoRemoto.eventosEmitidos());
                actualizarEstadoDesdePeriferico(estadoRemoto);

                cancelarTareaActual();
                asegurarScheduler();
                long syncIntervalMs = Math.min(2500L, intervaloMs);
                this.tareaProgramada = scheduler.scheduleAtFixedRate(
                        this::ejecutarTickSeguro,
                        syncIntervalMs,
                        syncIntervalMs,
                        TimeUnit.MILLISECONDS
                );
                LOGGER.log(
                        Level.INFO,
                        "Nodo periférico de Tracking iniciado en {0}: {1} eventos/min, desvíos={2}",
                        new Object[]{trackingClient.getEndpointUrl(), this.eventosPorMinuto, this.cantidadDesvios}
                );
                return;
            } catch (Exception e) {
                this.nodoPerifericoOnline = false;
                LOGGER.log(
                        Level.WARNING,
                        "Nodo periférico de Tracking no disponible en {0} ({1}); activando motor de respaldo.",
                        new Object[]{trackingClient.getEndpointUrl(), e.getMessage()}
                );
            }
        }

        cancelarTareaActual();
        asegurarScheduler();

        this.activo = true;
        this.tareaProgramada = scheduler.scheduleAtFixedRate(
                this::ejecutarTickSeguro,
                0L,
                intervaloMs,
                TimeUnit.MILLISECONDS
        );

        this.ultimoResultado = String.format(
                "Mock activo (%d eventos/min, %d desvío(s) configurado(s) por viaje).",
                this.eventosPorMinuto,
                this.cantidadDesvios
        );
        LOGGER.log(
                Level.INFO,
                "Mock de Tracking iniciado: {0} eventos/min (intervalo {1} ms), desvíos={2}",
                new Object[]{this.eventosPorMinuto, intervaloMs, this.cantidadDesvios}
        );
    }

    @Override
    public synchronized void detener() {
        this.activo = false;
        cancelarTareaActual();
        if (trackingClient != null) {
            try {
                TrackingClient.EstadoNodoTracking estadoRemoto = trackingClient.detener(UUID.randomUUID().toString());
                this.nodoPerifericoOnline = true;
                actualizarEstadoDesdePeriferico(estadoRemoto);
                this.activo = false;
                LOGGER.info("Nodo periférico de Tracking detenido.");
                return;
            } catch (Exception e) {
                this.nodoPerifericoOnline = false;
                LOGGER.log(Level.FINE, "No se pudo contactar al nodo periférico al detener: {0}", e.getMessage());
            }
        }
        this.ultimoResultado = "Mock detenido por el usuario. Total eventos emitidos: " + totalEventosEmitidos.get();
        LOGGER.info("Mock de Tracking detenido.");
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public synchronized void reiniciarRecorridos() {
        estadosPorGuia.clear();
        guiasInicializadasEnSesion.clear();
        guiasConMockPersistido.clear();
        if (trackingClient != null) {
            try {
                List<GuiaDeViaje> guiasActivas = buscarGuiasEnCurso();
                TrackingClient.EstadoNodoTracking estadoRemoto = trackingClient.reiniciar(
                        construirPayloadGuias(guiasActivas),
                        UUID.randomUUID().toString()
                );
                this.nodoPerifericoOnline = true;
                actualizarEstadoDesdePeriferico(estadoRemoto);
                return;
            } catch (Exception e) {
                this.nodoPerifericoOnline = false;
                LOGGER.log(Level.FINE, "No se pudo contactar al nodo periférico al reiniciar: {0}", e.getMessage());
            }
        }
        this.ultimoResultado = "Recorridos del Mock reiniciados al punto de origen.";
    }

    @PreDestroy
    public synchronized void cerrar() {
        this.activo = false;
        cancelarTareaActual();
        if (tareaAutomatica != null) {
            tareaAutomatica.cancel(false);
            tareaAutomatica = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public void notificarCambioConfiguracionNodos() {
        sincronizarConfigTrackingConRegistro();
        if (scheduler != null && !scheduler.isShutdown()
                && (nodosPerifericosService == null || nodosPerifericosService.isTrackingHabilitado())) {
            scheduler.execute(this::ejecutarPasoTrackingSeguro);
        }
    }

    @Override
    public void notificarCambioBalanza() {
        if (balanzaService == null) {
            return;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(this::sondearBalanzasSeguro);
        } else {
            sondearBalanzasSeguro();
        }
    }

    void ejecutarCicloAutomaticoSeguro() {
        if (!ejecutandoCicloAuto.compareAndSet(false, true)) {
            return;
        }
        try {
            long tick = contadorTicksAutomaticos.incrementAndGet();
            sincronizarConfigTrackingConRegistro();

            // Emitir paso de tracking cada 15 segundos (ticks 1, 4, 7, ... sobre ciclo base de 5s)
            if ((tick - 1L) % 3L == 0L
                    && (nodosPerifericosService == null || nodosPerifericosService.isTrackingHabilitado())) {
                ejecutarPasoTrackingSeguro();
            }

            // Sondear balanzas habilitadas cada 10 segundos (ticks 1, 3, 5, ...)
            if ((tick - 1L) % 2L == 0L) {
                sondearBalanzasSeguro();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante el ciclo automático de Nodos Periféricos: {0}", e.getMessage());
        } finally {
            ejecutandoCicloAuto.set(false);
        }
    }

    private void ejecutarPasoTrackingSeguro() {
        try {
            TrackingMockServiceLocal proxy = sessionContext != null
                    ? sessionContext.getBusinessObject(TrackingMockServiceLocal.class)
                    : this;
            if (nodosPerifericosService == null || nodosPerifericosService.isTrackingHabilitado()) {
                proxy.ejecutarPasoAutomaticoTracking();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante la emisión automática de Tracking: {0}", e.getMessage());
        }
    }

    private void sondearBalanzasSeguro() {
        if (balanzaService == null || !ejecutandoBalanzaAuto.compareAndSet(false, true)) {
            return;
        }
        try {
            balanzaService.sondearBalanzasPeriodico();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante el sondeo automático de Balanzas: {0}", e.getMessage());
        } finally {
            ejecutandoBalanzaAuto.set(false);
        }
    }

    private void sincronizarConfigTrackingConRegistro() {
        if (nodosPerifericosService == null) {
            return;
        }

        String endpointConfig = nodosPerifericosService.getEndpointTrackingActivo();
        if (trackingClient != null && endpointConfig != null && !endpointConfig.isBlank()) {
            if (!endpointConfig.equals(trackingClient.getEndpointUrl())) {
                trackingClient.setEndpointUrl(endpointConfig);
            }
        }

        boolean trackingHabilitado = nodosPerifericosService.isTrackingHabilitado();
        int desviosDeseados = nodosPerifericosService.getModoTrackingActivo() == ModoNodoPeriferico.CON_DESVIO ? 1 : 0;

        if (!trackingHabilitado) {
            if (this.activo) {
                this.activo = false;
                if (trackingClient != null) {
                    try {
                        trackingClient.detener(UUID.randomUUID().toString());
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "No se pudo detener nodo periférico de Tracking remoto: {0}", e.getMessage());
                    }
                }
                this.ultimoResultado = "Nodo de Tracking apagado por el Administrador.";
            }
            return;
        }

        if (this.cantidadDesvios != desviosDeseados) {
            this.cantidadDesvios = desviosDeseados;
            this.estadosPorGuia.clear();
        }
        this.activo = true;
    }

    private void ejecutarTickSeguro() {
        if (!activo) {
            return;
        }
        if (nodosPerifericosService != null && !nodosPerifericosService.isTrackingHabilitado()) {
            this.activo = false;
            return;
        }
        try {
            TrackingMockServiceLocal proxy = sessionContext != null
                    ? sessionContext.getBusinessObject(TrackingMockServiceLocal.class)
                    : this;
            if (trackingClient != null && nodoPerifericoOnline) {
                proxy.sincronizarConNodoPeriferico();
            } else {
                proxy.emitirPasoGuiasActivas();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante el ciclo automático del Mock de Tracking: {0}", e.getMessage());
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void sincronizarConNodoPeriferico() {
        if (nodosPerifericosService != null && !nodosPerifericosService.isTrackingHabilitado()) {
            this.activo = false;
            this.ultimoResultado = "Nodo de Tracking apagado por el Administrador.";
            return;
        }
        if (trackingClient == null) {
            return;
        }
        try {
            List<GuiaDeViaje> guiasActivas = buscarGuiasEnCurso();
            TrackingClient.EstadoNodoTracking estadoRemoto = trackingClient.sincronizarGuiasYDrenar(
                    construirPayloadGuias(guiasActivas),
                    true,
                    UUID.randomUUID().toString()
            );
            this.nodoPerifericoOnline = true;
            procesarEventosDePeriferico(estadoRemoto.eventosEmitidos());
            actualizarEstadoDesdePeriferico(estadoRemoto);
        } catch (Exception e) {
            this.nodoPerifericoOnline = false;
            LOGGER.log(Level.FINE, "No fue posible sincronizar con el nodo periférico de Tracking: {0}", e.getMessage());
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public int emitirPasoGuiasActivas() {
        return ejecutarPasoConRegistro();
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public int ejecutarPasoAutomaticoTracking() {
        return ejecutarPasoConRegistro();
    }

    private int ejecutarPasoConRegistro() {
        if (nodosPerifericosService != null) {
            sincronizarConfigTrackingConRegistro();
            if (!nodosPerifericosService.isTrackingHabilitado()) {
                this.ultimoResultado = "Nodo de Tracking apagado por el Administrador.";
                return 0;
            }
        }

        List<GuiaDeViaje> guiasActivas = buscarGuiasEnCurso();
        if (guiasActivas.isEmpty()) {
            this.ultimoResultado = "No hay guías de viaje en estado EN_CURSO actualmente.";
            estadosPorGuia.clear();
            guiasInicializadasEnSesion.clear();
            guiasConMockPersistido.clear();
            if (trackingClient != null) {
                try {
                    trackingClient.sincronizarGuiasYDrenar(List.of(), true, UUID.randomUUID().toString());
                    this.nodoPerifericoOnline = true;
                } catch (Exception ignored) {
                }
            }
            return 0;
        }

        Set<Long> idsActivos = new HashSet<>();
        List<GuiaDeViaje> guiasParaEmitir = new ArrayList<>();
        for (GuiaDeViaje guia : guiasActivas) {
            if (guia == null || guia.getId() == null) {
                continue;
            }
            idsActivos.add(guia.getId());
            guiasParaEmitir.add(guia);
        }

        estadosPorGuia.keySet().retainAll(idsActivos);
        guiasInicializadasEnSesion.retainAll(idsActivos);
        guiasConMockPersistido.retainAll(idsActivos);

        boolean reiniciarRemoto = requiereReinicioPorEstadoPersistencia(guiasParaEmitir);
        List<TrackingClient.GuiaActivaPayload> payloadGuias = construirPayloadGuias(guiasParaEmitir);

        if (trackingClient != null) {
            try {
                if (reiniciarRemoto) {
                    trackingClient.reiniciar(payloadGuias, UUID.randomUUID().toString());
                }
                String faasUrl = faasTrackingClient != null ? faasTrackingClient.getEndpointUrl() : "";
                TrackingClient.EstadoNodoTracking estadoRemoto = trackingClient.emitirPaso(
                        this.eventosPorMinuto,
                        this.cantidadDesvios,
                        payloadGuias,
                        faasUrl,
                        UUID.randomUUID().toString()
                );
                this.nodoPerifericoOnline = true;
                int procesados = procesarEventosDePeriferico(estadoRemoto.eventosEmitidos());
                actualizarEstadoDesdePeriferico(estadoRemoto);
                return procesados > 0 ? procesados : (estadoRemoto.eventosEmitidos() != null ? estadoRemoto.eventosEmitidos().size() : 0);
            } catch (Exception e) {
                this.nodoPerifericoOnline = false;
                LOGGER.log(
                        Level.WARNING,
                        "Fallo al solicitar paso al nodo periférico de Tracking ({0}); ejecutando paso local de respaldo.",
                        e.getMessage()
                );
            }
        }

        int emitidosEnPaso = 0;
        String detalleUltimo = null;
        FaasTrackingClient clienteFaas = obtenerClienteFaas();

        for (GuiaDeViaje guia : guiasParaEmitir) {
            Long guiaId = guia.getId();

            EstadoSimulacionGuia existente = estadosPorGuia.get(guiaId);
            EstadoSimulacionGuia estadoGuia;
            if (existente == null || existente.getDesviosConfigurados() != this.cantidadDesvios) {
                List<double[]> polilineaSim = resolverPolilineaSimulacion(guia);
                EstadoSimulacionGuia nuevoEstado = construirEstadoSimulacion(guia, this.cantidadDesvios, polilineaSim);
                estadoGuia = estadosPorGuia.compute(guiaId, (id, curr) ->
                        (curr == null || curr.getDesviosConfigurados() != this.cantidadDesvios) ? nuevoEstado : curr);
            } else {
                estadoGuia = existente;
            }

            PasoSimulado paso = estadoGuia.siguientePaso();
            String matricula = guia.getVehiculo() != null ? guia.getVehiculo().getMatricula() : null;
            String origen = (guia.getOrigen() != null && !guia.getOrigen().isBlank()) ? guia.getOrigen() : "Montevideo";
            String destino = (guia.getDestino() != null && !guia.getDestino().isBlank()) ? guia.getDestino() : "Rivera";

            EventoViajeDTO baseDto = new EventoViajeDTO(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    TipoEvento.POSICION,
                    paso.latitud(),
                    paso.longitud(),
                    guiaId,
                    new IncidenteDTO(null, paso.descripcion()),
                    null,
                    null,
                    null,
                    null,
                    matricula
            );

            Double desvioKm = 0.0;
            try {
                FaasTrackingClient.ResultadoFaas resFaas = clienteFaas.procesarEvento(
                        baseDto,
                        origen,
                        destino,
                        guia.getPolilineaRuta()
                );
                desvioKm = resFaas.distanciaDesvioKm();
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Fallback de desvío en Mock de Tracking para guía {0}: {1}",
                        new Object[]{guiaId, ex.getMessage()});
            }

            EventoViajeDTO enriquecido = new EventoViajeDTO(
                    baseDto.uuid(),
                    baseDto.tiempo(),
                    baseDto.tipo(),
                    baseDto.latitud(),
                    baseDto.longitud(),
                    guiaId,
                    baseDto.incidente(),
                    desvioKm,
                    null,
                    null,
                    null,
                    matricula
            );

            try {
                if (eventosService != null) {
                    eventosService.guardarEvento(enriquecido);
                }
                if (motorFiscalizacion != null) {
                    motorFiscalizacion.evaluarGuia(guiaId);
                }
                emitidosEnPaso++;
                totalEventosEmitidos.incrementAndGet();
                if (paso.esInicioEpisodioDesvio()) {
                    totalDesviosEmitidos.incrementAndGet();
                }
                detalleUltimo = String.format(
                        "Guía #%d (%s -> %s): (%.4f, %.4f) | Desvío: %.2f km | %s",
                        guiaId,
                        origen,
                        destino,
                        paso.latitud(),
                        paso.longitud(),
                        desvioKm != null ? desvioKm : 0.0,
                        paso.enDestino() ? "En destino aguardando finalización" : (paso.esDesvio() ? "EN DESVÍO" : "En ruta")
                );
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "No se pudo registrar punto mock para guía #{0}: {1}",
                        new Object[]{guiaId, e.getMessage()});
            }
        }

        if (detalleUltimo != null) {
            this.ultimoResultado = detalleUltimo;
        }
        return emitidosEnPaso;
    }

    private int procesarEventosDePeriferico(List<EventoViajeDTO> eventosRemotos) {
        if (eventosRemotos == null || eventosRemotos.isEmpty()) {
            return 0;
        }
        int persistidos = 0;
        for (EventoViajeDTO ev : eventosRemotos) {
            if (ev == null || ev.uuid() == null || ev.uuid().isBlank()) {
                continue;
            }
            try {
                if (eventosService != null) {
                    if (!eventosService.existeEvento(ev.uuid())) {
                        eventosService.guardarEvento(ev);
                        persistidos++;
                    }
                }
                if (motorFiscalizacion != null && ev.guiaId() != null) {
                    motorFiscalizacion.evaluarGuia(ev.guiaId());
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Evento de nodo periférico {0} omitido o ya procesado: {1}",
                        new Object[]{ev.uuid(), e.getMessage()});
            }
        }
        return persistidos;
    }

    private void actualizarEstadoDesdePeriferico(TrackingClient.EstadoNodoTracking estadoRemoto) {
        if (estadoRemoto == null) {
            return;
        }
        this.activo = estadoRemoto.activo();
        this.eventosPorMinuto = estadoRemoto.eventosPorMinuto();
        this.cantidadDesvios = estadoRemoto.cantidadDesvios();
        this.totalEventosEmitidos.set(estadoRemoto.totalEventosEmitidos());
        this.totalDesviosEmitidos.set(estadoRemoto.totalDesviosEmitidos());
        if (estadoRemoto.ultimoResultado() != null && !estadoRemoto.ultimoResultado().isBlank()) {
            this.ultimoResultado = estadoRemoto.ultimoResultado();
        }
    }

    private boolean requiereReinicioPorEstadoPersistencia(List<GuiaDeViaje> guias) {
        boolean requiereReinicio = false;
        for (GuiaDeViaje guia : guias) {
            Long gid = guia.getId();
            if (tieneEventosMockEnPersistencia(gid)) {
                guiasConMockPersistido.add(gid);
                guiasInicializadasEnSesion.add(gid);
            } else {
                if (!guiasInicializadasEnSesion.contains(gid) || guiasConMockPersistido.contains(gid)) {
                    requiereReinicio = true;
                    guiasConMockPersistido.remove(gid);
                    estadosPorGuia.remove(gid);
                }
                guiasInicializadasEnSesion.add(gid);
            }
        }
        return requiereReinicio;
    }

    private boolean tieneEventosMockEnPersistencia(Long guiaId) {
        if (eventosService == null || guiaId == null) {
            return false;
        }
        List<EventoViajeDTO> eventos = eventosService.listarEventosPorGuia(guiaId);
        if (eventos == null || eventos.isEmpty()) {
            return false;
        }
        for (EventoViajeDTO ev : eventos) {
            if (esEventoMockTracking(ev)) {
                return true;
            }
        }
        return false;
    }

    private static boolean esEventoMockTracking(EventoViajeDTO ev) {
        return ev != null
                && ev.incidente() != null
                && ev.incidente().descripcion() != null
                && ev.incidente().descripcion().startsWith("Mock Tracking GPS");
    }

    private static List<double[]> resolverPolilineaBase(GuiaDeViaje guia, String origen, String destino) {
        List<double[]> polilinea = RedVialUruguay.deserializarPolilinea(guia.getPolilineaRuta());
        try {
            List<double[]> calculada = RedVialUruguay.calcularRuta(origen, destino).polilinea();
            if (calculada != null && (polilinea.isEmpty() || calculada.size() > polilinea.size())) {
                polilinea = calculada;
            }
        } catch (Exception ignored) {
            LOGGER.log(Level.FINE, "No se pudo calcular polilínea detallada para {0} -> {1}",
                    new Object[]{origen, destino});
        }
        if (polilinea.isEmpty()) {
            polilinea = RedVialUruguay.calcularRuta("Montevideo", "Rivera").polilinea();
        }
        return polilinea;
    }

    private List<double[]> resolverPolilineaSimulacion(GuiaDeViaje guia) {
        String origen = (guia.getOrigen() != null && !guia.getOrigen().isBlank()) ? guia.getOrigen().trim() : "Montevideo";
        String destino = (guia.getDestino() != null && !guia.getDestino().isBlank()) ? guia.getDestino().trim() : "Rivera";

        List<double[]> polilinea = resolverPolilineaBase(guia, origen, destino);
        if (eventosService == null || guia.getId() == null) {
            return polilinea;
        }
        List<EventoViajeDTO> eventos = eventosService.listarEventosPorGuia(guia.getId());
        if (eventos == null || eventos.isEmpty()) {
            return polilinea;
        }
        EventoViajeDTO ultimoPreMock = null;
        for (EventoViajeDTO ev : eventos) {
            if (ev != null && ev.tipo() != TipoEvento.FIN && !esEventoMockTracking(ev)) {
                ultimoPreMock = ev;
            }
        }
        if (ultimoPreMock == null) {
            return polilinea;
        }
        int pasos = Math.max(PASOS_RUTA_BASE, this.cantidadDesvios + 4);
        return RedVialUruguay.recortarPolilineaDesdePosicion(
                polilinea,
                ultimoPreMock.latitud(),
                ultimoPreMock.longitud(),
                pasos
        );
    }

    private List<TrackingClient.GuiaActivaPayload> construirPayloadGuias(List<GuiaDeViaje> guias) {
        if (guias == null || guias.isEmpty()) {
            return List.of();
        }
        List<TrackingClient.GuiaActivaPayload> resultado = new ArrayList<>(guias.size());
        for (GuiaDeViaje g : guias) {
            if (g == null || g.getId() == null) {
                continue;
            }
            String matricula = g.getVehiculo() != null ? g.getVehiculo().getMatricula() : null;
            String origen = (g.getOrigen() != null && !g.getOrigen().isBlank()) ? g.getOrigen().trim() : "Montevideo";
            String destino = (g.getDestino() != null && !g.getDestino().isBlank()) ? g.getDestino().trim() : "Rivera";
            String polilineaSerializada = RedVialUruguay.serializarPolilinea(resolverPolilineaSimulacion(g));
            resultado.add(new TrackingClient.GuiaActivaPayload(
                    g.getId(),
                    origen,
                    destino,
                    matricula,
                    g.getCorredorVial(),
                    polilineaSerializada
            ));
        }
        return resultado;
    }

    static EstadoSimulacionGuia construirEstadoSimulacion(GuiaDeViaje guia, int desviosDeseados) {
        return construirEstadoSimulacion(guia, desviosDeseados, null);
    }

    static EstadoSimulacionGuia construirEstadoSimulacion(
            GuiaDeViaje guia,
            int desviosDeseados,
            List<double[]> polilineaPersonalizada
    ) {
        String origen = (guia.getOrigen() != null && !guia.getOrigen().isBlank()) ? guia.getOrigen().trim() : "Montevideo";
        String destino = (guia.getDestino() != null && !guia.getDestino().isBlank()) ? guia.getDestino().trim() : "Rivera";

        List<double[]> polilinea = (polilineaPersonalizada != null && !polilineaPersonalizada.isEmpty())
                ? polilineaPersonalizada
                : resolverPolilineaBase(guia, origen, destino);

        int numDesvios = Math.max(0, desviosDeseados);
        int cantidadPuntosRuta = Math.max(PASOS_RUTA_BASE, numDesvios + 4);
        List<double[]> puntosRuta = RedVialUruguay.generarPuntosRecorridoPolilinea(polilinea, cantidadPuntosRuta);

        List<PasoSimulado> plan = new ArrayList<>();
        int ultimoIdx = puntosRuta.size() - 1;
        int desviosInsertados = 0;

        for (int i = 0; i < ultimoIdx; i++) {
            double[] ptRuta = puntosRuta.get(i);
            plan.add(new PasoSimulado(
                    ptRuta[0],
                    ptRuta[1],
                    false,
                    false,
                    false,
                    String.format("Mock Tracking GPS - En ruta (%s -> %s) tramo %d/%d", origen, destino, i + 1, ultimoIdx)
            ));

            // Si quedan desvíos por simular, insertamos el episodio de desvío (2 puntos en desvío)
            // seguido de un punto de retorno a la ruta esperada para cerrar el episodio antes del siguiente.
            if (desviosInsertados < numDesvios) {
                desviosInsertados++;
                double[] desvio1 = RedVialUruguay.obtenerPuntoDesviadoDesdeCoordenada(ptRuta, polilinea);
                double[] desvio2 = new double[]{
                        Math.round((desvio1[0] + 0.008) * 10000.0) / 10000.0,
                        Math.round((desvio1[1] + 0.008) * 10000.0) / 10000.0
                };

                plan.add(new PasoSimulado(
                        desvio1[0],
                        desvio1[1],
                        true,
                        true,
                        false,
                        String.format("Mock Tracking GPS - Desvío #%d/%d fuera de corredor (%s -> %s)",
                                desviosInsertados, numDesvios, origen, destino)
                ));
                plan.add(new PasoSimulado(
                        desvio2[0],
                        desvio2[1],
                        false,
                        true,
                        false,
                        String.format("Mock Tracking GPS - Continúa en desvío #%d/%d (no genera nueva infracción)",
                                desviosInsertados, numDesvios)
                ));

                // Punto explícito de retorno a la ruta esperada para cerrar el episodio actual
                int idxRetorno = Math.min(i + 1, ultimoIdx - 1);
                double[] ptRetorno = puntosRuta.get(idxRetorno);
                plan.add(new PasoSimulado(
                        ptRetorno[0],
                        ptRetorno[1],
                        false,
                        false,
                        false,
                        String.format("Mock Tracking GPS - Reingreso a ruta esperada tras desvío #%d (%s -> %s)",
                                desviosInsertados, origen, destino)
                ));
            }
        }

        // Último punto: coordenadas exactas del lugar de finalización (destino)
        double[] ptDestino = puntosRuta.get(ultimoIdx);
        plan.add(new PasoSimulado(
                ptDestino[0],
                ptDestino[1],
                false,
                false,
                true,
                String.format("Mock Tracking GPS - En lugar de finalización (%s) aguardando cierre de viaje del chofer", destino)
        ));

        return new EstadoSimulacionGuia(guia.getId(), numDesvios, plan);
    }

    private List<GuiaDeViaje> buscarGuiasEnCurso() {
        if (em == null) {
            return List.of();
        }
        TypedQuery<GuiaDeViaje> q = em.createQuery(
                "SELECT g FROM GuiaDeViaje g LEFT JOIN FETCH g.vehiculo WHERE g.estado = :enCurso ORDER BY g.id ASC",
                GuiaDeViaje.class
        );
        if (q == null) {
            return List.of();
        }
        q.setParameter("enCurso", TipoEstado.EN_CURSO);
        List<GuiaDeViaje> resultado = q.getResultList();
        return resultado != null ? resultado : List.of();
    }

    private FaasTrackingClient obtenerClienteFaas() {
        if (faasTrackingClient == null) {
            faasTrackingClient = new FaasTrackingClient();
        }
        return faasTrackingClient;
    }

    private void asegurarScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cargauy-tracking-mock-scheduler");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private void cancelarTareaActual() {
        if (tareaProgramada != null) {
            tareaProgramada.cancel(false);
            tareaProgramada = null;
        }
    }

    @Override
    public boolean isNodoPerifericoOnline() {
        if (nodoPerifericoOnline) {
            return true;
        }
        if (trackingClient != null && trackingClient.verificarSalud()) {
            this.nodoPerifericoOnline = true;
            return true;
        }
        return false;
    }

    @Override
    public String getEndpointNodoPeriferico() {
        return trackingClient != null ? trackingClient.getEndpointUrl() : "http://localhost:8085";
    }

    @Override
    public boolean estaActivo() {
        return activo;
    }

    @Override
    public int getEventosPorMinuto() {
        return eventosPorMinuto;
    }

    @Override
    public int getCantidadDesvios() {
        return cantidadDesvios;
    }

    @Override
    public long getTotalEventosEmitidos() {
        return totalEventosEmitidos.get();
    }

    @Override
    public long getTotalDesviosEmitidos() {
        return totalDesviosEmitidos.get();
    }

    @Override
    public String getUltimoResultado() {
        return ultimoResultado;
    }
}
