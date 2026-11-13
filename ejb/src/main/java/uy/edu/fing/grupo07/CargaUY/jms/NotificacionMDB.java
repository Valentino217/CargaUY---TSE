package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJB;
import jakarta.ejb.MessageDriven;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumidor asíncrono Message-Driven Bean de {@code NotificacionQueue} (Punto E.c).
 * Procesa la notificación a la empresa transportista involucrada, actualiza el estado
 * del caso de {@code DETECTADA} a {@code NOTIFICADA} y deja constancia en la pista de auditoría.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/NotificacionQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "5"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class NotificacionMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(NotificacionMDB.class.getName());

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    public NotificacionMDB() {
    }

    public NotificacionMDB(GestionInfraccionesServiceLocal infraccionesService) {
        this.infraccionesService = infraccionesService;
    }

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage textMsg)) {
            LOGGER.log(Level.WARNING, "Mensaje ignorado en NotificacionQueue: no es TextMessage ({0})", message);
            return;
        }

        uy.edu.fing.grupo07.CargaUY.integration.TraceContext.Estado previo =
                uy.edu.fing.grupo07.CargaUY.integration.TraceContext.capturar();
        String correlacion = null;
        String traceparent = null;
        try {
            try {
                correlacion = textMsg.getJMSCorrelationID();
                if (correlacion == null || correlacion.isBlank()) {
                    correlacion = textMsg.getStringProperty("X_Correlation_ID");
                }
                traceparent = textMsg.getStringProperty("traceparent");
            } catch (Exception ignored) {
            }
            correlacion = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.normalizarCorrelacion(correlacion);
            traceparent = uy.edu.fing.grupo07.CargaUY.integration.TraceContext.normalizarTraceparent(traceparent, correlacion);
            uy.edu.fing.grupo07.CargaUY.integration.TraceContext.fijar(correlacion, traceparent);

            String cuerpo = textMsg.getText();
            if (cuerpo == null || cuerpo.isBlank()) {
                LOGGER.log(Level.WARNING, "Mensaje vacío recibido en NotificacionQueue.");
                return;
            }

            int idCaso;
            try {
                idCaso = Integer.parseInt(cuerpo.trim());
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.WARNING, "Contenido no numérico recibido en NotificacionQueue: {0}", cuerpo);
                return;
            }

            InfraccionDTO notificada = infraccionesService.procesarNotificacionAutomatica(idCaso);
            if (notificada != null) {
                LOGGER.log(
                        Level.INFO,
                        "Empresa transportista \"{0}\" notificada exitosamente sobre el caso #{1} ({2}, vehículo {3}) [correlacion={4}, traceparent={5}].",
                        new Object[]{
                                notificada.nombreEmpresa() != null ? notificada.nombreEmpresa() : "Sin empresa registrada",
                                notificada.idCaso(),
                                notificada.tipo(),
                                notificada.matricula() != null ? notificada.matricula() : "N/D",
                                correlacion,
                                traceparent
                        }
                );
            }
        } catch (BusinessException be) {
            LOGGER.log(Level.WARNING, "Validación de negocio en NotificacionMDB: {0}", be.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico imprevisto en NotificacionMDB al procesar mensaje", e);
            throw new RuntimeException("Error técnico en NotificacionMDB", e);
        } finally {
            uy.edu.fing.grupo07.CargaUY.integration.TraceContext.restaurar(previo);
        }
    }
}
