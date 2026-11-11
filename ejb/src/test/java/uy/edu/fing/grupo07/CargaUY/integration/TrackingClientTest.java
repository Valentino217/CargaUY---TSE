package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - TrackingClient (Nodo Periférico Tracking)")
class TrackingClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private TrackingClient client;

    @BeforeEach
    void setUp() {
        client = new TrackingClient(httpClient, "http://mock-tracking:8080");
    }

    @Test
    @DisplayName("Debe iniciar simulación en el nodo periférico enviando guías activas y parámetros")
    void testIniciarEnPerifericoExitoso() throws IOException, InterruptedException {
        String jsonPayload = """
                {
                    "activo": true,
                    "eventosPorMinuto": 24,
                    "cantidadDesvios": 2,
                    "totalEventosEmitidos": 1,
                    "totalDesviosEmitidos": 0,
                    "ultimoResultado": "Simulación iniciada a 24 eventos/min (2 desvíos por viaje)",
                    "eventosEmitidos": [
                        {
                            "uuid": "evt-001",
                            "timestamp": "2026-11-11T16:30:00",
                            "tipo": "POSICION",
                            "latitud": -34.9011,
                            "longitud": -56.1645,
                            "guiaId": 101,
                            "matricula": "STP1234",
                            "distanciaDesvioKm": 0.12
                        }
                    ]
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonPayload);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        List<TrackingClient.GuiaActivaPayload> guias = List.of(
                new TrackingClient.GuiaActivaPayload(
                        101L, "Montevideo", "Rivera", "STP1234", "Ruta 5", "-34.9011,-56.1645;-30.9053,-55.5508"
                )
        );

        String corr = "11111111-2222-3333-4444-555555555555";
        TrackingClient.EstadoNodoTracking resp = client.iniciar(24, 2, guias, "", corr);

        assertTrue(resp.activo());
        assertEquals(24, resp.eventosPorMinuto());
        assertEquals(2, resp.cantidadDesvios());
        assertEquals(1, resp.eventosEmitidos().size());

        EventoViajeDTO ev = resp.eventosEmitidos().get(0);
        assertEquals("evt-001", ev.uuid());
        assertEquals(TipoEvento.POSICION, ev.tipo());
        assertEquals(101L, ev.guiaId());
        assertEquals("STP1234", ev.matricula());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        assertEquals("POST", captor.getValue().method());
        assertEquals("/iniciar", captor.getValue().uri().getPath());
        assertEquals(corr, captor.getValue().headers().firstValue("X-Correlation-ID").orElse(null));
    }

    @Test
    @DisplayName("Debe sincronizar guías y drenar eventos pendientes desde /guias")
    void testSincronizarGuiasYDrenar() throws IOException, InterruptedException {
        String jsonPayload = """
                {
                    "activo": true,
                    "eventosPorMinuto": 12,
                    "cantidadDesvios": 1,
                    "totalEventosEmitidos": 5,
                    "totalDesviosEmitidos": 1,
                    "ultimoResultado": "Paso emitido",
                    "eventosEmitidos": [
                        {
                            "uuid": "evt-desvio-01",
                            "timestamp": "2026-11-11T16:35:00",
                            "tipo": "POSICION",
                            "latitud": -33.5000,
                            "longitud": -56.4000,
                            "guiaId": 202,
                            "matricula": "STP9999",
                            "distanciaDesvioKm": 11.4,
                            "incidente": {
                                "descripcion": "GPS mock [STP9999] (FUERA DE RUTA: 11.40 km del corredor esperado)"
                            }
                        }
                    ]
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonPayload);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        TrackingClient.EstadoNodoTracking resp = client.sincronizarGuiasYDrenar(List.of(), true, "corr-sync");

        assertEquals(1, resp.eventosEmitidos().size());
        EventoViajeDTO ev = resp.eventosEmitidos().get(0);
        assertEquals("evt-desvio-01", ev.uuid());
        assertEquals(202L, ev.guiaId());
        assertNotNull(ev.incidente());
        assertTrue(ev.incidente().descripcion().contains("FUERA DE RUTA"));
    }

    @Test
    @DisplayName("Debe lanzar BusinessException cuando el nodo periférico no responde")
    void testFallaConexionConPeriferico() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new ConnectException("Connection refused"));

        assertThrows(BusinessException.class, () -> client.detener("corr-stop"));
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
}
