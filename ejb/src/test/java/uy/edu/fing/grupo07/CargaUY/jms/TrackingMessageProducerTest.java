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
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - TrackingMessageProducer")
class TrackingMessageProducerTest {

    @Mock
    private JMSContext jmsContext;

    @Mock
    private JMSProducer jmsProducer;

    @Mock
    private Queue trackingQueue;

    @Mock
    private TextMessage textMessage;

    private TrackingMessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TrackingMessageProducer(jmsContext, trackingQueue);
    }

    @Test
    @DisplayName("Debe serializar el evento a JSON y enviarlo a la TrackingQueue con propiedades JMS")
    void testEnviarEventoExitoso() throws JMSException {
        when(jmsContext.createTextMessage(anyString())).thenReturn(textMessage);
        when(jmsContext.createProducer()).thenReturn(jmsProducer);

        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-jms-1",
                LocalDateTime.now(),
                TipoEvento.CARGA,
                -34.90,
                -56.16,
                5L,
                null
        );

        producer.enviarEvento(dto);

        verify(jmsContext).createTextMessage(anyString());
        verify(textMessage).setStringProperty("uuid", "uuid-jms-1");
        verify(textMessage).setLongProperty("guiaId", 5L);
        verify(textMessage).setStringProperty("tipo", "CARGA");
        verify(jmsProducer).send(trackingQueue, textMessage);
    }

    @Test
    @DisplayName("Debe validar que el evento y su UUID no sean nulos")
    void testEnviarEventoValidacion() {
        assertThrows(BusinessException.class, () -> producer.enviarEvento(null));

        EventoViajeDTO sinUuid = new EventoViajeDTO(
                null, LocalDateTime.now(), TipoEvento.DESCARGA, -34.0, -56.0, 1L, null
        );
        assertThrows(BusinessException.class, () -> producer.enviarEvento(sinUuid));

        EventoViajeDTO uuidVacio = new EventoViajeDTO(
                "   ", LocalDateTime.now(), TipoEvento.DESCARGA, -34.0, -56.0, 1L, null
        );
        assertThrows(BusinessException.class, () -> producer.enviarEvento(uuidVacio));
    }

    @Test
    @DisplayName("Debe encolar múltiples eventos secuencialmente")
    void testEnviarEventosLote() {
        when(jmsContext.createTextMessage(anyString())).thenReturn(textMessage);
        when(jmsContext.createProducer()).thenReturn(jmsProducer);

        EventoViajeDTO e1 = new EventoViajeDTO("u1", LocalDateTime.now(), TipoEvento.INICIO, -34.0, -56.0, 1L, null);
        EventoViajeDTO e2 = new EventoViajeDTO("u2", LocalDateTime.now(), TipoEvento.FIN, -34.0, -56.0, 1L, null);

        producer.enviarEventos(List.of(e1, e2));

        verify(jmsProducer, times(2)).send(eq(trackingQueue), eq(textMessage));
    }

    @Test
    @DisplayName("Debe manejar listas nulas o vacías sin invocar JMS")
    void testEnviarEventosVacio() {
        producer.enviarEventos(null);
        producer.enviarEventos(List.of());

        verifyNoInteractions(jmsContext);
    }

    @Test
    @DisplayName("Debe relanzar RuntimeException si ocurre error en JMSContext")
    void testEnviarEventoErrorJMS() {
        when(jmsContext.createTextMessage(anyString())).thenThrow(new RuntimeException("JMS error"));

        EventoViajeDTO dto = new EventoViajeDTO("u-err", LocalDateTime.now(), TipoEvento.INICIO, -34.0, -56.0, 1L, null);

        assertThrows(RuntimeException.class, () -> producer.enviarEvento(dto));
    }
}
