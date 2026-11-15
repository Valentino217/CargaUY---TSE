package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Productor EJB encargado de publicar solicitudes de fiscalización en FiscalizacionQueue.
 */
@Stateless
public class FiscalizacionMessageProducer implements FiscalizacionProducerLocal {

    public static final String PREFIJO_SIN_GUIA = "SIN_GUIA|";

    private static final Logger LOGGER = Logger.getLogger(FiscalizacionMessageProducer.class.getName());

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/FiscalizacionQueue")
    private Queue fiscalizacionQueue;

    public FiscalizacionMessageProducer() {
    }

    public FiscalizacionMessageProducer(JMSContext jmsContext, Queue fiscalizacionQueue) {
        this.jmsContext = jmsContext;
        this.fiscalizacionQueue = fiscalizacionQueue;
    }

    @Override
    public void solicitarEvaluacion(Long guiaId) {
        if (guiaId == null) {
            LOGGER.log(Level.WARNING, "Solicitud de fiscalización ignorada: guiaId es nulo.");
            return;
        }

        try {
            String correlacion = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.normalizarCorrelacion(null);
            String traceparent = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.siguienteTraceparentHijo(null, correlacion);
            TextMessage message = jmsContext.createTextMessage(String.valueOf(guiaId));
            message.setLongProperty("guiaId", guiaId);
            message.setJMSCorrelationID(correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_CORRELACION, correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_TRACEPARENT, traceparent);

            JMSProducer producer = jmsContext.createProducer();
            producer.send(fiscalizacionQueue, message);

            LOGGER.log(
                    Level.INFO,
                    "Solicitud de fiscalización encolada con éxito en FiscalizacionQueue para la guía ID: {0}",
                    guiaId
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico al encolar solicitud en FiscalizacionQueue para la guía ID: " + guiaId, e);
            throw new RuntimeException("Error al enviar mensaje a FiscalizacionQueue", e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void solicitarEvaluacionSinGuia(String matricula, String detalleActividad) {
        if (matricula == null || matricula.isBlank()) {
            LOGGER.log(Level.WARNING, "Solicitud de fiscalización VIAJE_SIN_GUIA ignorada: matrícula vacía.");
            return;
        }

        String matriculaLimpia = matricula.trim();
        String detalleLimpio = detalleActividad != null
                ? detalleActividad.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim()
                : "";

        try {
            String correlacion = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.normalizarCorrelacion(null);
            String traceparent = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.siguienteTraceparentHijo(null, correlacion);
            String payload = PREFIJO_SIN_GUIA + matriculaLimpia + "|" + detalleLimpio;
            TextMessage message = jmsContext.createTextMessage(payload);
            message.setStringProperty("tipoEvaluacion", "SIN_GUIA");
            message.setStringProperty("matricula", matriculaLimpia);
            message.setJMSCorrelationID(correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_CORRELACION, correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_TRACEPARENT, traceparent);

            JMSProducer producer = jmsContext.createProducer();
            producer.send(fiscalizacionQueue, message);

            LOGGER.log(
                    Level.INFO,
                    "Solicitud de fiscalización VIAJE_SIN_GUIA encolada en FiscalizacionQueue para matrícula: {0}",
                    matriculaLimpia
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico al encolar fiscalización VIAJE_SIN_GUIA para matrícula: " + matriculaLimpia, e);
            throw new RuntimeException("Error al enviar mensaje VIAJE_SIN_GUIA a FiscalizacionQueue", e);
        }
    }
}
