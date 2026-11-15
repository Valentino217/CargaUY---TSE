package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Productor EJB que publica en {@code NotificacionQueue} la notificación automática
 * a la empresa transportista involucrada al generarse un caso de fiscalización (Punto E.c).
 */
@Stateless
public class NotificacionMessageProducer implements NotificacionProducerLocal {

    private static final Logger LOGGER = Logger.getLogger(NotificacionMessageProducer.class.getName());

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/NotificacionQueue")
    private Queue notificacionQueue;

    public NotificacionMessageProducer() {
    }

    public NotificacionMessageProducer(JMSContext jmsContext, Queue notificacionQueue) {
        this.jmsContext = jmsContext;
        this.notificacionQueue = notificacionQueue;
    }

    @Override
    public void solicitarNotificacion(int idCaso) {
        if (idCaso <= 0) {
            LOGGER.log(Level.WARNING, "Solicitud de notificación ignorada: idCaso inválido ({0}).", idCaso);
            return;
        }

        try {
            String correlacion = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.normalizarCorrelacion(null);
            String traceparent = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.siguienteTraceparentHijo(null, correlacion);
            TextMessage message = jmsContext.createTextMessage(String.valueOf(idCaso));
            message.setIntProperty("idCaso", idCaso);
            message.setJMSCorrelationID(correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_CORRELACION, correlacion);
            message.setStringProperty(uy.edu.fing.grupo07.CargaUY.integration.TraceContext.JMS_PROP_TRACEPARENT, traceparent);

            JMSProducer producer = jmsContext.createProducer();
            producer.send(notificacionQueue, message);

            LOGGER.log(
                    Level.INFO,
                    "Notificación automática encolada en NotificacionQueue para el caso #{0} [correlacion={1}, traceparent={2}].",
                    new Object[]{idCaso, correlacion, traceparent}
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico al encolar notificación en NotificacionQueue para el caso #" + idCaso, e);
            throw new RuntimeException("Error al enviar mensaje a NotificacionQueue", e);
        }
    }
}
