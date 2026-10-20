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
@DisplayName("Pruebas Unitarias - FiscalizacionMessageProducer")
class FiscalizacionMessageProducerTest {

    @Mock
    private JMSContext jmsContext;

    @Mock
    private JMSProducer jmsProducer;

    @Mock
    private Queue fiscalizacionQueue;

    @Mock
    private TextMessage textMessage;

    private FiscalizacionMessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new FiscalizacionMessageProducer(jmsContext, fiscalizacionQueue);
    }

    @Test
    @DisplayName("Debe crear TextMessage y enviarlo a FiscalizacionQueue cuando el ID es válido")
    void testSolicitarEvaluacionExitoso() throws JMSException {
        when(jmsContext.createTextMessage(anyString())).thenReturn(textMessage);
        when(jmsContext.createProducer()).thenReturn(jmsProducer);

        Long guiaId = 77L;
        producer.solicitarEvaluacion(guiaId);

        verify(jmsContext).createTextMessage("77");
        verify(textMessage).setLongProperty("guiaId", 77L);
        verify(jmsProducer).send(fiscalizacionQueue, textMessage);
    }

    @Test
    @DisplayName("Debe ignorar la solicitud sin invocar JMS cuando el ID de guía es nulo")
    void testSolicitarEvaluacionGuiaIdNuloEsIgnorado() {
        producer.solicitarEvaluacion(null);

        verifyNoInteractions(jmsContext);
        verifyNoInteractions(jmsProducer);
    }

    @Test
    @DisplayName("Debe relanzar RuntimeException ante un fallo en el contexto JMS")
    void testSolicitarEvaluacionFalloJmsLanzaExcepcion() {
        when(jmsContext.createTextMessage(anyString()))
                .thenThrow(new RuntimeException("Fallo en proveedor de mensajería"));

        assertThrows(RuntimeException.class, () -> producer.solicitarEvaluacion(12L));
        verify(jmsProducer, never()).send(any(Queue.class), any(TextMessage.class));
    }
}
