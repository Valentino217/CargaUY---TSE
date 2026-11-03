package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - NotificacionMessageProducer (Punto E.c)")
class NotificacionMessageProducerTest {

    @Mock
    private JMSContext jmsContext;

    @Mock
    private JMSProducer jmsProducer;

    @Mock
    private Queue notificacionQueue;

    @Mock
    private TextMessage textMessage;

    private NotificacionMessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new NotificacionMessageProducer(jmsContext, notificacionQueue);
    }

    @Test
    @DisplayName("Debe encolar TextMessage con idCaso en NotificacionQueue cuando idCaso es válido")
    void testSolicitarNotificacionExitoso() throws JMSException {
        when(jmsContext.createTextMessage("42")).thenReturn(textMessage);
        when(jmsContext.createProducer()).thenReturn(jmsProducer);

        producer.solicitarNotificacion(42);

        verify(jmsContext).createTextMessage("42");
        verify(textMessage).setIntProperty("idCaso", 42);
        verify(jmsProducer).send(notificacionQueue, textMessage);
    }

    @Test
    @DisplayName("Debe ignorar idCaso menor o igual a cero sin tocar JMS")
    void testSolicitarNotificacionIdInvalidoEsIgnorado() {
        producer.solicitarNotificacion(0);
        producer.solicitarNotificacion(-5);

        verifyNoInteractions(jmsContext);
        verifyNoInteractions(jmsProducer);
    }

    @Test
    @DisplayName("Debe relanzar RuntimeException ante fallo técnico del proveedor JMS")
    void testSolicitarNotificacionFalloTecnicoRelanzaExcepcion() {
        when(jmsContext.createTextMessage(anyString())).thenThrow(new RuntimeException("Broker JMS caído"));

        assertThrows(RuntimeException.class, () -> producer.solicitarNotificacion(15));
        verify(jmsProducer, never()).send(any(Queue.class), any(TextMessage.class));
    }
}
