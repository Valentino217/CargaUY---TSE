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
 * Productor EJB encargado de publicar solicitudes de fiscalización en FiscalizacionQueue.
 */
@Stateless
public class FiscalizacionMessageProducer implements FiscalizacionProducerLocal {

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
            TextMessage message = jmsContext.createTextMessage(String.valueOf(guiaId));
            message.setLongProperty("guiaId", guiaId);

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
}
