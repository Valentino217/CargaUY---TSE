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
            TextMessage message = jmsContext.createTextMessage(String.valueOf(idCaso));
            message.setIntProperty("idCaso", idCaso);

            JMSProducer producer = jmsContext.createProducer();
            producer.send(notificacionQueue, message);

            LOGGER.log(
                    Level.INFO,
                    "Notificación automática encolada en NotificacionQueue para el caso #{0}.",
                    idCaso
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico al encolar notificación en NotificacionQueue para el caso #" + idCaso, e);
            throw new RuntimeException("Error al enviar mensaje a NotificacionQueue", e);
        }
    }
}
