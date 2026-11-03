package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - NotificacionMDB (Punto E.c)")
class NotificacionMDBTest {

    @Mock
    private GestionInfraccionesServiceLocal infraccionesService;

    @Mock
    private TextMessage textMessage;

    @Mock
    private Message genericMessage;

    private NotificacionMDB mdb;

    @BeforeEach
    void setUp() {
        mdb = new NotificacionMDB(infraccionesService);
    }

    @Test
    @DisplayName("Debe procesar la notificación automática de la infracción al recibir un idCaso válido")
    void testOnMessageProcesaNotificacionAutomatica() throws JMSException {
        when(textMessage.getText()).thenReturn(" 105 ");
        InfraccionDTO dto = new InfraccionDTO(
                105,
                LocalDate.now(),
                TipoInfraccion.VIAJE_SIN_GUIA,
                EstadoInfraccion.NOTIFICADA,
                "Evidencia",
                null,
                null,
                null,
                "123456",
                "LogiSur",
                null,
                null,
                null
        );
        when(infraccionesService.procesarNotificacionAutomatica(105)).thenReturn(dto);

        mdb.onMessage(textMessage);

        verify(infraccionesService).procesarNotificacionAutomatica(105);
    }

    @Test
    @DisplayName("Debe ignorar mensajes no TextMessage, vacíos o no numéricos")
    void testOnMessageMensajesInvalidosIgnorados() throws JMSException {
        mdb.onMessage(genericMessage);

        when(textMessage.getText()).thenReturn("   ");
        mdb.onMessage(textMessage);

        when(textMessage.getText()).thenReturn("abc");
        mdb.onMessage(textMessage);

        verifyNoInteractions(infraccionesService);
    }

    @Test
    @DisplayName("Debe capturar BusinessException sin relanzar y propagar RuntimeException en fallos técnicos")
    void testOnMessageManejoExcepciones() throws JMSException {
        when(textMessage.getText()).thenReturn("20");
        when(infraccionesService.procesarNotificacionAutomatica(20))
                .thenThrow(new BusinessException("Regla de negocio"));

        mdb.onMessage(textMessage);
        verify(infraccionesService).procesarNotificacionAutomatica(20);

        when(textMessage.getText()).thenThrow(new JMSException("Error red JMS"));
        assertThrows(RuntimeException.class, () -> mdb.onMessage(textMessage));
    }
}
