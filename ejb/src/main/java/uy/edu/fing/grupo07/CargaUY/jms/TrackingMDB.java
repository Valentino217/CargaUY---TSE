package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJB;
import jakarta.ejb.MessageDriven;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Message-Driven Bean que consume eventos de TrackingQueue concurrentemente (maxSession=10).
 * Garantiza idempotencia por UUID (AC012), trazabilidad distribuida (X-Correlation-ID), métricas y tolerancia a fallos.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/TrackingQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "10"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class TrackingMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(TrackingMDB.class.getName());
    private static final Jsonb jsonb = JsonbBuilder.create();

    public static final AtomicLong eventosProcesados = new AtomicLong();
    public static final AtomicLong eventosDuplicados = new AtomicLong();
    public static final AtomicLong erroresProcesamiento = new AtomicLong();
    public static final AtomicLong latenciaTotalMs = new AtomicLong();

    @EJB
    private GestionEventosServiceLocal eventosService;

    @EJB
    private FiscalizacionProducerLocal fiscalizacionProducer;

    public TrackingMDB() {
    }

    public TrackingMDB(GestionEventosServiceLocal eventosService) {
        this.eventosService = eventosService;
    }

    public TrackingMDB(
            GestionEventosServiceLocal eventosService,
            FiscalizacionProducerLocal fiscalizacionProducer
    ) {
        this.eventosService = eventosService;
        this.fiscalizacionProducer = fiscalizacionProducer;
    }

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage textMsg)) {
            LOGGER.log(Level.WARNING, "Mensaje ignorado en TrackingQueue: no es TextMessage ({0})", message);
            return;
        }

        long inicio = System.nanoTime();
        String correlacion = "n/a";
        try {
            String propCorr = null;
            try {
                propCorr = textMsg.getStringProperty("X-Correlation-ID");
            } catch (Exception ignored) {
            }

            String json = textMsg.getText();
            EventoViajeDTO evento = jsonb.fromJson(json, EventoViajeDTO.class);

            if (evento == null || evento.uuid() == null) {
                LOGGER.log(Level.WARNING, "Mensaje inválido recibido en TrackingQueue: payload o uuid nulo");
                return;
            }

            correlacion = (propCorr != null && !propCorr.isBlank()) ? propCorr : evento.uuid();

            // AC012: Idempotencia estricta por UUID
            if (eventosService.existeEvento(evento.uuid())) {
                eventosDuplicados.incrementAndGet();
                LOGGER.log(Level.INFO,
                        "{\"evento\":\"jms.tracking.duplicado\",\"correlacion\":\"{0}\",\"uuid\":\"{1}\"}",
                        new Object[]{correlacion, evento.uuid()});
                return;
            }

            // Persistir evento (GestionEventosServiceImpl ya encola la evaluación en FiscalizacionQueue;
            // el respaldo aplica únicamente cuando un doble de prueba retorna null).
            uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje persistido = eventosService.guardarEvento(evento);
            long duracionMs = Math.max(0L, (System.nanoTime() - inicio) / 1_000_000L);
            eventosProcesados.incrementAndGet();
            latenciaTotalMs.addAndGet(duracionMs);

            LOGGER.log(Level.INFO,
                    "{\"evento\":\"jms.tracking.procesado\",\"correlacion\":\"{0}\",\"uuid\":\"{1}\",\"duracionMs\":{2}}",
                    new Object[]{correlacion, evento.uuid(), duracionMs});

            if (persistido == null && fiscalizacionProducer != null && evento.guiaId() != null) {
                fiscalizacionProducer.solicitarEvaluacion(evento.guiaId());
            }

        } catch (BusinessException e) {
            // Error de negocio (duplicado en carrera concurrente o validación): descartar sin reintentar
            erroresProcesamiento.incrementAndGet();
            LOGGER.log(Level.WARNING,
                    "{\"evento\":\"jms.tracking.error_negocio\",\"correlacion\":\"{0}\",\"detalle\":\"{1}\"}",
                    new Object[]{correlacion, e.getMessage()});
        } catch (Exception e) {
            // Error inesperado de infraestructura (BD caída, etc.): relanzar para activar reintentos y DLQ
            erroresProcesamiento.incrementAndGet();
            LOGGER.log(Level.SEVERE, "Fallo técnico en TrackingMDB al procesar mensaje [correlacion=" + correlacion + "]", e);
            throw new RuntimeException("Error técnico en TrackingMDB", e);
        }
    }
}
