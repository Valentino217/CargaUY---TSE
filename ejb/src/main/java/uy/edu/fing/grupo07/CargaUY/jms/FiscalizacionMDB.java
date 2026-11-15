package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJB;
import jakarta.ejb.MessageDriven;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumidor asíncrono Message-Driven Bean para la cola FiscalizacionQueue.
 * Desacopla la evaluación de reglas del flujo transaccional principal.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/FiscalizacionQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "5"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class FiscalizacionMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(FiscalizacionMDB.class.getName());

    @EJB
    private MotorFiscalizacionLocal motorFiscalizacion;

    public FiscalizacionMDB() {
    }

    public FiscalizacionMDB(MotorFiscalizacionLocal motorFiscalizacion) {
        this.motorFiscalizacion = motorFiscalizacion;
    }

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage textMsg)) {
            LOGGER.log(Level.WARNING, "Mensaje ignorado en FiscalizacionQueue: no es TextMessage ({0})", message);
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
                LOGGER.log(Level.WARNING, "Mensaje vacío recibido en FiscalizacionQueue.");
                return;
            }

            String cuerpoTrim = cuerpo.trim();
            if (cuerpoTrim.startsWith(FiscalizacionMessageProducer.PREFIJO_SIN_GUIA)) {
                String resto = cuerpoTrim.substring(FiscalizacionMessageProducer.PREFIJO_SIN_GUIA.length());
                int sep = resto.indexOf('|');
                String matricula = sep >= 0 ? resto.substring(0, sep).trim() : resto.trim();
                if (matricula.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Mensaje SIN_GUIA ignorado en FiscalizacionQueue: matrícula vacía ({0})", cuerpo);
                    return;
                }
                String detalle = sep >= 0 ? resto.substring(sep + 1).trim() : "";
                boolean generada = motorFiscalizacion.evaluarVehiculoSinGuia(matricula, detalle);
                LOGGER.log(
                        Level.INFO,
                        "Fiscalización VIAJE_SIN_GUIA completada para matrícula {0}. Nueva infracción generada: {1} [correlacion={2}, traceparent={3}]",
                        new Object[]{matricula, generada, correlacion, traceparent}
                );
                return;
            }

            Long guiaId;
            try {
                guiaId = Long.parseLong(cuerpoTrim);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.WARNING, "El contenido del mensaje en FiscalizacionQueue no es numérico: {0}", cuerpo);
                return;
            }

            List<TipoInfraccion> infracciones = motorFiscalizacion.evaluarGuia(guiaId);
            LOGGER.log(
                    Level.INFO,
                    "Fiscalización completada para la guía {0}. Nuevas infracciones: {1} [correlacion={2}, traceparent={3}]",
                    new Object[]{guiaId, infracciones.size(), correlacion, traceparent}
            );

        } catch (BusinessException be) {
            LOGGER.log(Level.WARNING, "Validación de negocio en FiscalizacionMDB: {0}", be.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fallo técnico imprevisto en FiscalizacionMDB al procesar mensaje", e);
            throw new RuntimeException("Error técnico en FiscalizacionMDB", e);
        } finally {
            uy.edu.fing.grupo07.CargaUY.integration.TraceContext.restaurar(previo);
        }
    }
}
