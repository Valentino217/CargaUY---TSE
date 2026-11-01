package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - FaasTrackingClient (Cloud FaaS & Cálculo Haversine)")
class FaasTrackingClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Test
    @DisplayName("Debe invocar la función FaaS en la nube por HTTPS y extraer distanciaDesvioKm")
    @SuppressWarnings("unchecked")
    void testProcesarEventoEnCloudFaasExitoso() throws Exception {
        String urlFaas = "https://demo-faas.lambda-url.us-east-1.on.aws/";
        FaasTrackingClient client = new FaasTrackingClient(httpClient, urlFaas, "secret-123");

        String respuestaJson = """
                {
                  "uuid": "evt-cloud-1",
                  "guiaId": 10,
                  "distanciaDesvioKm": 47.85,
                  "procesadoPorFaas": true,
                  "faasTimestamp": "2026-10-25T16:00:00Z"
                }
                """;

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(respuestaJson);

        EventoViajeDTO entrada = new EventoViajeDTO(
                "evt-cloud-1",
                LocalDateTime.of(2026, 10, 25, 13, 0),
                TipoEvento.INCIDENTE,
                -33.2333,
                -54.3833,
                10L,
                null
        );

        FaasTrackingClient.ResultadoFaas resultado = client.procesarEvento(entrada, "Montevideo", "Rivera");

        assertNotNull(resultado);
        assertTrue(resultado.invocacionCloudExitosa());
        assertEquals(47.85, resultado.distanciaDesvioKm());
        assertEquals(47.85, resultado.eventoEnriquecido().distanciaDesvioKm());
    }

    @Test
    @DisplayName("Debe aplicar respaldo Haversine cuando la URL FaaS no está configurada o da error")
    void testFallbackHaversinePuntoEnRutaVsDesviado() {
        FaasTrackingClient client = new FaasTrackingClient(httpClient, "", "");

        double[] puntoEnRuta = client.obtenerPuntoMedioEnRuta("Montevideo", "Rivera");
        EventoViajeDTO evEnRuta = new EventoViajeDTO(
                "evt-ruta",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                puntoEnRuta[0],
                puntoEnRuta[1],
                1L,
                null
        );

        FaasTrackingClient.ResultadoFaas resEnRuta = client.procesarEvento(evEnRuta, "Montevideo", "Rivera");
        assertFalse(resEnRuta.invocacionCloudExitosa());
        assertTrue(resEnRuta.distanciaDesvioKm() < 1.0, "El punto medio debe tener desvío cercano a 0 km");

        double[] puntoDesviado = client.obtenerPuntoDesviadoDeRuta("Montevideo", "Rivera");
        EventoViajeDTO evDesviado = new EventoViajeDTO(
                "evt-desviado",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                puntoDesviado[0],
                puntoDesviado[1],
                1L,
                null
        );

        FaasTrackingClient.ResultadoFaas resDesviado = client.procesarEvento(evDesviado, "Montevideo", "Rivera");
        assertTrue(resDesviado.distanciaDesvioKm() > 20.0, "El punto desviado debe superar holgadamente el umbral");
    }
}
