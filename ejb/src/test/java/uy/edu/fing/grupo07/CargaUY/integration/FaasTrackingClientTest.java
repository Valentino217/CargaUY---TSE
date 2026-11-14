package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - FaasTrackingClient & RedVialUruguay (Polilíneas Reales MTOP)")
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
    @DisplayName("Debe calcular desvío sobre polilínea vial real cuando la URL FaaS no está configurada o da error")
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
        assertTrue(resEnRuta.distanciaDesvioKm() < 1.0, "El punto sobre la polilínea Ruta 5 debe tener desvío cercano a 0 km");

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

    @Test
    @DisplayName("Debe evaluar correctamente la polilínea real Fray Bentos -> Montevideo por Ruta 2 y Ruta 1 pasando por Cardona y Rosario")
    void testPolilineaRealFrayBentosMontevideoPorRuta2YRuta1() {
        FaasTrackingClient client = new FaasTrackingClient(httpClient, "", "");

        RedVialUruguay.RutaPlanificada ruta = RedVialUruguay.calcularRuta(
                "Fray Bentos - Planta UPM I (Río Negro)",
                "Puerto de Montevideo (Montevideo)"
        );
        assertEquals("Ruta 2 -> Ruta 1", ruta.corredorVial());
        assertTrue(ruta.polilinea().size() >= 6, "La polilínea debe incluir los tramos reales de Ruta 2 y Ruta 1");

        // Un camión circulando por Rosario (empalme Ruta 2 y Ruta 1: -34.3167, -57.3500) está exactamente en ruta (0 km de desvío)
        double desvioEnRosario = client.calcularDesvioPuntoASegmentoKm(
                -34.3167,
                -57.3500,
                "Fray Bentos - Planta UPM I (Río Negro)",
                "Puerto de Montevideo (Montevideo)"
        );
        assertEquals(0.0, desvioEnRosario, 0.5, "Rosario es parte del corredor real Ruta 2 -> Ruta 1");

        // Un camión en Minas (-34.3759, -55.2377) está completamente fuera del corredor Fray Bentos -> Montevideo
        double desvioEnMinas = client.calcularDesvioPuntoASegmentoKm(
                -34.3759,
                -55.2377,
                "Fray Bentos - Planta UPM I (Río Negro)",
                "Puerto de Montevideo (Montevideo)"
        );
        assertTrue(desvioEnMinas > 70.0, "Minas debe presentar un desvío mayor a 70 km respecto a Ruta 2 -> Ruta 1");
    }

    @Test
    @DisplayName("Debe rechazar localidades desconocidas con IllegalArgumentException en lugar de caer en Montevideo")
    void testRechazarLocalidadDesconocidaSinFallbackSilencioso() {
        FaasTrackingClient client = new FaasTrackingClient(httpClient, "", "");

        assertThrows(
                IllegalArgumentException.class,
                () -> client.resolverCoordenadasLocalidad("Ciudad Inexistente XYZ"),
                "No debe caer silenciosamente en Montevideo ante una localidad desconocida"
        );

        EventoViajeDTO evento = new EventoViajeDTO(
                "evt-invalido",
                LocalDateTime.now(),
                TipoEvento.POSICION,
                -34.9011,
                -56.1645,
                1L,
                null
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> client.procesarEvento(evento, "Atlantida Fantasma", "Montevideo")
        );
    }

    @Test
    @DisplayName("GuiaDeViaje debe persistir automáticamente el corredor vial y la polilínea serializada")
    void testGuiaDeViajePersisteCorredorYPolilinea() {
        GuiaDeViaje guia = new GuiaDeViaje(
                LocalDate.now(),
                "Puerto de Nueva Palmira (Colonia)",
                "Puerto de Montevideo (Montevideo)",
                "Granos",
                30000.0f,
                null,
                null,
                null
        );

        assertNotNull(guia.getCorredorVial());
        assertTrue(guia.getCorredorVial().contains("Ruta 1"));
        assertNotNull(guia.getPolilineaRuta());

        List<double[]> puntos = RedVialUruguay.deserializarPolilinea(guia.getPolilineaRuta());
        assertTrue(puntos.size() >= 4);
    }

    @Test
    public void testCoordenadasSeedCoincidenConDesviosDeclaradosEnRedVial() {
        RedVialUruguay.RutaPlanificada ruta1001 = RedVialUruguay.calcularRuta(
                "Puerto de Montevideo", "Rivera (Frontera)");
        assertNotNull(ruta1001);
        double[][] muestras1001 = {
                {-34.9011, -56.1645, 0.0},
                {-34.7303, -56.2170, 0.2},
                {-34.3092, -56.2416, 0.4},
                {-34.0956, -56.2088, 0.5}
        };
        assertMuestrasSobreRuta(ruta1001.polilinea(), muestras1001);

        RedVialUruguay.RutaPlanificada ruta1005 = RedVialUruguay.calcularRuta(
                "Canelones", "Fray Bentos (UPM)");
        assertNotNull(ruta1005);
        double[][] muestras1005 = {
                {-34.5228, -56.2778, 0.0},
                {-34.3375, -56.7125, 0.1}
        };
        assertMuestrasSobreRuta(ruta1005.polilinea(), muestras1005);
    }

    @Test
    @DisplayName("La polilínea planificada debe seguir vértices intermedios de rutas nacionales reales")
    void testPolilineaDetalladaSigueCurvasVialesReales() {
        RedVialUruguay.RutaPlanificada ruta1001 = RedVialUruguay.calcularRuta(
                "Puerto de Montevideo", "Rivera (Frontera)");
        assertNotNull(ruta1001);
        assertEquals("Ruta 5", ruta1001.corredorVial());
        assertTrue(ruta1001.polilinea().size() >= 20,
                "Ruta 5 debe incluir vértices intermedios reales entre ciudades nodo");
        assertTrue(RedVialUruguay.calcularDesvioPolilineaKm(-32.2389, -56.1917, ruta1001.polilinea()) < 1.0,
                "La polilínea de Ruta 5 debe pasar cerca de Peralta");
        assertTrue(RedVialUruguay.calcularDesvioPolilineaKm(-31.1833, -55.7611, ruta1001.polilinea()) < 1.0,
                "La polilínea de Ruta 5 debe pasar cerca de Tranqueras / Empalme Ruta 30");

        RedVialUruguay.RutaPlanificada ruta1005 = RedVialUruguay.calcularRuta(
                "Canelones", "Fray Bentos (UPM)");
        assertNotNull(ruta1005);
        assertEquals("Ruta 11 -> Ruta 3 -> Ruta 14 -> Ruta 2", ruta1005.corredorVial());
        assertTrue(ruta1005.polilinea().size() >= 14,
                "Ruta Canelones -> Fray Bentos debe incluir curvas reales de Ruta 11, 3, 14 y 2");
        assertTrue(RedVialUruguay.calcularDesvioPolilineaKm(-33.5086, -57.8008, ruta1005.polilinea()) < 1.0,
                "La polilínea de Ruta 14 -> Ruta 2 debe pasar cerca del empalme en Palmitas");
    }

    private void assertMuestrasSobreRuta(List<double[]> polilinea, double[][] muestras) {
        assertNotNull(polilinea);
        for (double[] m : muestras) {
            double desvioRealKm = RedVialUruguay.calcularDesvioPolilineaKm(m[0], m[1], polilinea);
            assertTrue(Math.abs(desvioRealKm - m[2]) <= 0.05);
        }
    }
}
