package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.EJB;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Singleton;
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
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.integration.RedVialUruguay;

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
 * Servicio Mock del sistema de tracking GPS para guías de viaje activas (EN_CURSO).
 * <p>
 * Emite periódicamente eventos de posición siguiendo la ruta planificada (origen -> destino),
 * permite intercalar una cantidad configurable de episodios de desvío durante el trayecto y,
 * al llegar al lugar de finalización (destino), continúa emitiendo puntos en el destino
 * hasta que el chofer finalice el viaje.
 * </p>
 */
@Singleton
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

    @Inject
    private FaasTrackingClient faasTrackingClient;

    @Resource
    private SessionContext sessionContext;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tareaProgramada;

    private volatile boolean activo = false;
    private volatile int eventosPorMinuto = EVENTOS_POR_MINUTO_DEFECTO;
    private volatile int cantidadDesvios = 0;
    private volatile String ultimoResultado = "Mock detenido.";

    private final AtomicLong totalEventosEmitidos = new AtomicLong();
    private final AtomicLong totalDesviosEmitidos = new AtomicLong();
    private final Map<Long, EstadoSimulacionGuia> estadosPorGuia = new ConcurrentHashMap<>();

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

    @Override
    public synchronized void iniciar(int nuevoEventosPorMinuto, int nuevaCantidadDesvios) {
        int epmNormalizado = Math.max(MIN_EVENTOS_POR_MINUTO, Math.min(MAX_EVENTOS_POR_MINUTO, nuevoEventosPorMinuto));
        int desviosNormalizados = Math.max(0, nuevaCantidadDesvios);

        boolean cambioDesvios = this.cantidadDesvios != desviosNormalizados;
        this.eventosPorMinuto = epmNormalizado;
        this.cantidadDesvios = desviosNormalizados;

        if (cambioDesvios) {
            estadosPorGuia.clear();
        }

        cancelarTareaActual();
        asegurarScheduler();

        long intervaloMs = Math.max(500L, Math.round(60_000.0 / this.eventosPorMinuto));
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
        this.ultimoResultado = "Mock detenido por el usuario. Total eventos emitidos: " + totalEventosEmitidos.get();
        LOGGER.info("Mock de Tracking detenido.");
    }

    @Override
    public synchronized void reiniciarRecorridos() {
        estadosPorGuia.clear();
        this.ultimoResultado = "Recorridos del Mock reiniciados al punto de origen.";
    }

    @PreDestroy
    public synchronized void cerrar() {
        this.activo = false;
        cancelarTareaActual();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void ejecutarTickSeguro() {
        if (!activo) {
            return;
        }
        try {
            if (sessionContext != null) {
                TrackingMockServiceLocal proxy = sessionContext.getBusinessObject(TrackingMockServiceLocal.class);
                proxy.emitirPasoGuiasActivas();
            } else {
                emitirPasoGuiasActivas();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error durante el ciclo automático del Mock de Tracking: {0}", e.getMessage());
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public int emitirPasoGuiasActivas() {
        List<GuiaDeViaje> guiasActivas = buscarGuiasEnCurso();
        if (guiasActivas.isEmpty()) {
            this.ultimoResultado = "No hay guías de viaje en estado EN_CURSO actualmente.";
            estadosPorGuia.clear();
            return 0;
        }

        Set<Long> idsActivos = new HashSet<>();
        int emitidosEnPaso = 0;
        String detalleUltimo = null;
        FaasTrackingClient clienteFaas = obtenerClienteFaas();

        for (GuiaDeViaje guia : guiasActivas) {
            if (guia == null || guia.getId() == null) {
                continue;
            }
            Long guiaId = guia.getId();
            idsActivos.add(guiaId);

            EstadoSimulacionGuia estadoGuia = estadosPorGuia.compute(guiaId, (id, existente) -> {
                if (existente == null || existente.getDesviosConfigurados() != this.cantidadDesvios) {
                    return construirEstadoSimulacion(guia, this.cantidadDesvios);
                }
                return existente;
            });

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

        // Limpiar el estado de las guías que ya fueron finalizadas por el chofer
        estadosPorGuia.keySet().removeIf(id -> !idsActivos.contains(id));

        if (detalleUltimo != null) {
            this.ultimoResultado = detalleUltimo;
        }
        return emitidosEnPaso;
    }

    static EstadoSimulacionGuia construirEstadoSimulacion(GuiaDeViaje guia, int desviosDeseados) {
        String origen = (guia.getOrigen() != null && !guia.getOrigen().isBlank()) ? guia.getOrigen().trim() : "Montevideo";
        String destino = (guia.getDestino() != null && !guia.getDestino().isBlank()) ? guia.getDestino().trim() : "Rivera";

        List<double[]> polilinea = RedVialUruguay.deserializarPolilinea(guia.getPolilineaRuta());
        if (polilinea.isEmpty()) {
            try {
                polilinea = RedVialUruguay.calcularRuta(origen, destino).polilinea();
            } catch (Exception e) {
                polilinea = RedVialUruguay.calcularRuta("Montevideo", "Rivera").polilinea();
            }
        }

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
