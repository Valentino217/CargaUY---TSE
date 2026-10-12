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
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - FiscalizacionMDB (Consumo Asíncrono de FiscalizacionQueue)")
class FiscalizacionMDBTest {

    @Mock
    private MotorFiscalizacionLocal motorFiscalizacion;

    @Mock
    private TextMessage textMessage;

    @Mock
    private Message genericMessage;

    private FiscalizacionMDB mdb;

    @BeforeEach
    void setUp() {
        mdb = new FiscalizacionMDB(motorFiscalizacion);
    }

    @Test
    @DisplayName("Debe invocar al motor de fiscalización cuando recibe un TextMessage válido con ID de guía")
    void testOnMessageMensajeValidoInvocaMotor() throws JMSException {
        when(textMessage.getText()).thenReturn("42");
        when(motorFiscalizacion.evaluarGuia(42L)).thenReturn(List.of(TipoInfraccion.EXCESO_DE_CARGA));

        mdb.onMessage(textMessage);

        verify(motorFiscalizacion).evaluarGuia(42L);
    }

    @Test
    @DisplayName("Debe descartar el mensaje sin invocar al motor si no es instancia de TextMessage")
    void testOnMessageTipoMensajeInvalidoEsIgnorado() {
        mdb.onMessage(genericMessage);

        verifyNoInteractions(motorFiscalizacion);
    }

    @Test
    @DisplayName("Debe ignorar el mensaje silenciosamente si el cuerpo de texto es nulo o vacío")
    void testOnMessageCuerpoVacioEsIgnorado() throws JMSException {
        when(textMessage.getText()).thenReturn("   ");

        mdb.onMessage(textMessage);

        verifyNoInteractions(motorFiscalizacion);
    }

    @Test
    @DisplayName("Debe ignorar el mensaje si el contenido no corresponde a un formato numérico")
    void testOnMessageCuerpoNoNumericoEsIgnorado() throws JMSException {
        when(textMessage.getText()).thenReturn("no-es-un-numero");

        mdb.onMessage(textMessage);

        verifyNoInteractions(motorFiscalizacion);
    }

    @Test
    @DisplayName("Debe capturar BusinessException sin relanzarla para evitar reintentos innecesarios")
    void testOnMessageBusinessExceptionEsCapturada() throws JMSException {
        when(textMessage.getText()).thenReturn("10");
        when(motorFiscalizacion.evaluarGuia(10L))
                .thenThrow(new BusinessException("Error de regla de negocio"));

        mdb.onMessage(textMessage);

        verify(motorFiscalizacion).evaluarGuia(10L);
    }

    @Test
    @DisplayName("Debe relanzar RuntimeException ante errores técnicos imprevistos para activar DLQ")
    void testOnMessageFalloTecnicoEsRelanzado() throws JMSException {
        when(textMessage.getText()).thenThrow(new JMSException("Fallo en la conexión JMS"));

        assertThrows(RuntimeException.class, () -> mdb.onMessage(textMessage));
        verifyNoInteractions(motorFiscalizacion);
    }
}
