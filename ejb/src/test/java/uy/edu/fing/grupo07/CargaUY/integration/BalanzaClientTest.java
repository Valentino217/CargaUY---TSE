package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - BalanzaClient (Consumo Mock y Tolerancia a Fallos)")
class BalanzaClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private BalanzaClient client;

    @BeforeEach
    void setUp() {
        client = new BalanzaClient(httpClient, "http://mock-balanza:8080");
    }

    @Test
    @DisplayName("Debe parsear correctamente la respuesta JSON del mock de balanza")
    void testConsultarPesadaMockExitoso() throws IOException, InterruptedException {
        String jsonPayload = """
                {
                    "idPesada": 4521,
                    "fecha": "2026-10-10",
                    "hora": "15:45:00",
                    "pesoRegistrado": 36500,
                    "matricula": "123456",
                    "estado": "OK"
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonPayload);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        PesadaBalanzaDTO resultado = client.consultarPesadaMock();

        assertNotNull(resultado);
        assertEquals(4521, resultado.idPesada());
        assertEquals(LocalDate.of(2026, 10, 10), resultado.fecha());
        assertEquals(LocalTime.of(15, 45, 0), resultado.hora());
        assertEquals(36500, resultado.pesoRegistrado());
        assertEquals("123456", resultado.matricula());
    }

    @Test
    @DisplayName("Debe lanzar BusinessException cuando la balanza responde con error HTTP")
    void testConsultarPesadaMockHttpError() throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpResponse.body()).thenReturn("Service Unavailable");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertThrows(BusinessException.class, () -> client.consultarPesadaMock());
    }

    @Test
    @DisplayName("Debe tolerar fallos de conexión sin voltear el servidor, lanzando BusinessException")
    void testConsultarPesadaMockFallaConexion() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new ConnectException("Connection refused"));

        BusinessException thrown = assertThrows(BusinessException.class, () -> client.consultarPesadaMock());
        assertTrue(thrown.getMessage().contains("No fue posible comunicarse con la balanza"));
    }
}
