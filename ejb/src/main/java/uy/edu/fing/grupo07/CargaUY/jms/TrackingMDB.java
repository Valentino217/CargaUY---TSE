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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Message-Driven Bean que consume eventos de TrackingQueue concurrentemente (maxSession=10).
 * Garantiza idempotencia por UUID (AC012) y tolerancia a fallos.
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

    @EJB
    private GestionEventosServiceLocal eventosService;

    public TrackingMDB() {
    }

    public TrackingMDB(GestionEventosServiceLocal eventosService) {
        this.eventosService = eventosService;
    }

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage textMsg)) {
            LOGGER.log(Level.WARNING, "Mensaje ignorado en TrackingQueue: no es TextMessage ({0})", message);
            return;
        }

        try {
            String json = textMsg.getText();
            EventoViajeDTO evento = jsonb.fromJson(json, EventoViajeDTO.class);

            if (evento == null || evento.uuid() == null) {
                LOGGER.log(Level.WARNING, "Mensaje inválido recibido en TrackingQueue: payload o uuid nulo");
                return;
            }

            // AC012: Idempotencia estricta por UUID
            if (eventosService.existeEvento(evento.uuid())) {
                LOGGER.log(Level.INFO, "Evento con UUID {0} ya existe en BD. Descartando silenciosamente.", evento.uuid());
                return;
            }

            // Persistir evento
            eventosService.guardarEvento(evento);
            LOGGER.log(Level.INFO, "Evento con UUID {0} persistido exitosamente por TrackingMDB.", evento.uuid());

        } catch (BusinessException e) {
            // Error de negocio (duplicado en carrera concurrente o validación): descartar sin reintentar
            LOGGER.log(Level.WARNING, "Error de negocio al procesar evento en TrackingMDB: {0}", e.getMessage());
        } catch (Exception e) {
            // Error inesperado de infraestructura (BD caída, etc.): relanzar para activar reintentos y DLQ
            LOGGER.log(Level.SEVERE, "Fallo técnico en TrackingMDB al procesar mensaje", e);
            throw new RuntimeException("Error técnico en TrackingMDB", e);
        }
    }
}
