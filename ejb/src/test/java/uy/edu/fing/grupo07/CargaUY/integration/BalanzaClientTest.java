package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Test
    @DisplayName("Debe consultar /pesadas con matricula, fecha, nroEmpresa y X-Correlation-ID")
    void testConsultarPesadasVehiculoConCorrelacion() throws IOException, InterruptedException {
        String jsonPayload = """
                {
                    "pesadas": [
                        {
                            "idPesada": 12,
                            "fecha": "2026-11-10",
                            "hora": "09:15:00",
                            "pesoRegistrado": 31800,
                            "matricula": "STP 1234",
                            "peaje": "Peaje Pando"
                        }
                    ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonPayload);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        String corr = "11111111-2222-3333-4444-555555555555";
        var lista = client.consultarPesadasVehiculo("STP 1234", LocalDate.of(2026, 11, 10), 101, corr);

        assertEquals(1, lista.size());
        assertEquals(31800, lista.get(0).pesoRegistrado());
        assertEquals("STP 1234", lista.get(0).matricula());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertEquals("GET", req.method());
        assertEquals("/pesadas", req.uri().getPath());
        assertTrue(req.uri().getQuery().contains("matricula=STP+1234"));
        assertTrue(req.uri().getQuery().contains("fecha=2026-11-10"));
        assertTrue(req.uri().getQuery().contains("nroEmpresa=101"));
        assertEquals(corr, req.headers().firstValue("X-Correlation-ID").orElse(null));
    }

    @Test
    @DisplayName("Debe registrar una pesada mediante POST /pesadas propagando X-Correlation-ID")
    void testRegistrarPesadaEnPeriferico() throws IOException, InterruptedException {
        String jsonResp = """
                {
                    "idPesada": 77,
                    "fecha": "2026-11-10",
                    "hora": "14:20:00",
                    "pesoRegistrado": 41200,
                    "matricula": "STP 1234",
                    "peaje": "Peaje Pando"
                }
                """;
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn(jsonResp);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        PesadaBalanzaDTO entrada = new PesadaBalanzaDTO(
                null, LocalDate.of(2026, 11, 10), LocalTime.of(14, 20), 41200, "STP 1234", 1001L
        );
        String corr = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        PesadaBalanzaDTO resp = client.registrarPesadaEnPeriferico(entrada, 101, "Peaje Pando", corr);

        assertEquals(77, resp.idPesada());
        assertEquals(41200, resp.pesoRegistrado());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertEquals("POST", req.method());
        assertEquals("/pesadas", req.uri().getPath());
        assertEquals(corr, req.headers().firstValue("X-Correlation-ID").orElse(null));
    }

    @Test
    @DisplayName("verificarSalud debe consultar /health y reflejar HTTP 200 vs 503")
    void testVerificarSalud() throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(200, 503);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertTrue(client.verificarSalud());
        assertFalse(client.verificarSalud());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals("/health", captor.getValue().uri().getPath());
    }

    @Test
    @DisplayName("Debe soportar endpoints dinámicos por nodo (/pesadas?fecha=... y /health en endpoint personalizado)")
    void testMetodosEnEndpointDinamico() throws IOException, InterruptedException {
        String jsonPorFecha = """
                {
                    "pesadas": [
                        {
                            "idPesada": 99,
                            "fecha": "2026-11-12",
                            "hora": "10:30:00",
                            "pesoRegistrado": 44500,
                            "matricula": "LOR 2024",
                            "peaje": "Peaje Queguay"
                        }
                    ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonPorFecha);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        var lista = client.consultarPesadasPorFechaEnEndpoint("http://balanza-r3:9090/pesadas/", LocalDate.of(2026, 11, 12), "corr-dyn");
        assertEquals(1, lista.size());
        assertEquals(99, lista.get(0).idPesada());
        assertEquals(44500, lista.get(0).pesoRegistrado());

        assertTrue(client.verificarSaludEnEndpoint("http://balanza-r3:9090/"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals("http://balanza-r3:9090/pesadas?fecha=2026-11-12", captor.getAllValues().get(0).uri().toString());
        assertEquals("http://balanza-r3:9090/health", captor.getAllValues().get(1).uri().toString());
    }
}
