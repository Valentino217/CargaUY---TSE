package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Productor EJB encargado de publicar eventos de tracking en la cola JMS (TrackingQueue).
 * Garantiza absorción de picos, trazabilidad distribuida (X-Correlation-ID) y desacople con el cliente móvil/REST (AC012).
 */
@Stateless
public class TrackingMessageProducer implements TrackingProducerLocal {

    private static final Logger LOGGER = Logger.getLogger(TrackingMessageProducer.class.getName());
    private static final Jsonb jsonb = JsonbBuilder.create();

    public static final AtomicLong mensajesEncolados = new AtomicLong();

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/TrackingQueue")
    private Queue trackingQueue;

    public TrackingMessageProducer() {
    }

    public TrackingMessageProducer(JMSContext jmsContext, Queue trackingQueue) {
        this.jmsContext = jmsContext;
        this.trackingQueue = trackingQueue;
    }

    @Override
    public void enviarEvento(EventoViajeDTO evento) {
        if (evento == null) {
            throw new BusinessException("El evento no puede ser nulo.");
        }
        if (evento.uuid() == null || evento.uuid().isBlank()) {
            throw new BusinessException("El evento debe tener un UUID válido.");
        }

        try {
            String correlacion = (evento.uuid() != null && !evento.uuid().isBlank())
                    ? evento.uuid().trim()
                    : UUID.randomUUID().toString();
            String json = jsonb.toJson(evento);
            TextMessage message = jmsContext.createTextMessage(json);
            message.setStringProperty("uuid", evento.uuid());
            message.setLongProperty("guiaId", evento.guiaId() != null ? evento.guiaId() : -1L);
            message.setStringProperty("tipo", evento.tipo() != null ? evento.tipo().name() : "");
            message.setJMSCorrelationID(correlacion);
            message.setStringProperty("X_Correlation_ID", correlacion);

            JMSProducer producer = jmsContext.createProducer();
            producer.send(trackingQueue, message);
            mensajesEncolados.incrementAndGet();

            LOGGER.log(Level.INFO,
                    "{\"evento\":\"jms.tracking.encolado\",\"correlacion\":\"{0}\",\"uuid\":\"{1}\",\"guiaId\":{2}}",
                    new Object[]{correlacion, evento.uuid(), evento.guiaId() != null ? evento.guiaId() : -1L});
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al encolar evento en TrackingQueue", e);
            throw new RuntimeException("Error al enviar evento a la cola de tracking", e);
        }
    }

    @Override
    public void enviarEventos(List<EventoViajeDTO> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return;
        }
        for (EventoViajeDTO ev : eventos) {
            enviarEvento(ev);
        }
    }
}
