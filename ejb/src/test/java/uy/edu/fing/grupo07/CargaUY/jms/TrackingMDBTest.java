package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - TrackingMDB (Consumidor Asíncrono e Idempotencia)")
class TrackingMDBTest {

    @Mock
    private GestionEventosServiceLocal eventosService;

    @Mock
    private TextMessage textMessage;

    @Mock
    private Message genericMessage;

    private TrackingMDB mdb;
    private static final Jsonb jsonb = JsonbBuilder.create();

    @BeforeEach
    void setUp() {
        mdb = new TrackingMDB(eventosService);
    }

    @Test
    @DisplayName("Debe persistir el evento cuando no es duplicado")
    void testOnMessageEventoNuevo() throws JMSException {
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-async-1",
                LocalDateTime.now(),
                TipoEvento.CARGA,
                -34.9,
                -56.1,
                1L,
                null
        );
        String json = jsonb.toJson(dto);

        when(textMessage.getText()).thenReturn(json);
        when(eventosService.existeEvento("uuid-async-1")).thenReturn(false);

        mdb.onMessage(textMessage);

        verify(eventosService).existeEvento("uuid-async-1");
        verify(eventosService).guardarEvento(any(EventoViajeDTO.class));
    }

    @Test
    @DisplayName("Debe descartar silenciosamente el mensaje si el UUID ya existe (AC012)")
    void testOnMessageEventoDuplicado() throws JMSException {
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-async-dup",
                LocalDateTime.now(),
                TipoEvento.DESCARGA,
                -34.9,
                -56.1,
                1L,
                null
        );
        String json = jsonb.toJson(dto);

        when(textMessage.getText()).thenReturn(json);
        when(eventosService.existeEvento("uuid-async-dup")).thenReturn(true);

        mdb.onMessage(textMessage);

        verify(eventosService).existeEvento("uuid-async-dup");
        verify(eventosService, never()).guardarEvento(any(EventoViajeDTO.class));
    }

    @Test
    @DisplayName("Debe ignorar mensajes que no sean TextMessage sin lanzar excepción")
    void testOnMessageNoTextMessage() {
        mdb.onMessage(genericMessage);

        verifyNoInteractions(eventosService);
    }

    @Test
    @DisplayName("Debe ignorar mensajes con contenido JSON inválido")
    void testOnMessageJsonInvalido() throws JMSException {
        when(textMessage.getText()).thenReturn("{}");

        mdb.onMessage(textMessage);

        verifyNoInteractions(eventosService);
    }

    @Test
    @DisplayName("Debe atrapar BusinessException de guardarEvento sin propagarla")
    void testOnMessageBusinessException() throws JMSException {
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-be",
                LocalDateTime.now(),
                TipoEvento.CARGA,
                -34.9,
                -56.1,
                1L,
                null
        );
        when(textMessage.getText()).thenReturn(jsonb.toJson(dto));
        when(eventosService.existeEvento("uuid-be")).thenReturn(false);
        doThrow(new BusinessException("Error de negocio controlado")).when(eventosService).guardarEvento(any());

        mdb.onMessage(textMessage);

        verify(eventosService).guardarEvento(any());
    }

    @Test
    @DisplayName("Debe relanzar RuntimeException en caso de error técnico para activar DLQ")
    void testOnMessageErrorTecnicoLanzaRuntimeException() throws JMSException {
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-tech-err",
                LocalDateTime.now(),
                TipoEvento.CARGA,
                -34.9,
                -56.1,
                1L,
                null
        );
        when(textMessage.getText()).thenReturn(jsonb.toJson(dto));
        when(eventosService.existeEvento("uuid-tech-err")).thenReturn(false);
        doThrow(new RuntimeException("Base de datos inalcanzable")).when(eventosService).guardarEvento(any());

        assertThrows(RuntimeException.class, () -> mdb.onMessage(textMessage));
    }
}
