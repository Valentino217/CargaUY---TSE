package uy.edu.fing.grupo07.CargaUY.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para la consulta de pesadas desde el periférico/mock de balanza.
 * Cuenta con timeout estricto de 5 segundos y tolerancia a fallos.
 */
@ApplicationScoped
public class BalanzaClient {

    private static final Logger LOGGER = Logger.getLogger(BalanzaClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String endpointUrl;

    public BalanzaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        String envUrl = System.getenv("BALANZA_ENDPOINT");
        this.endpointUrl = (envUrl != null && !envUrl.isBlank()) ? envUrl : "http://localhost:8082";
    }

    public BalanzaClient(HttpClient httpClient, String endpointUrl) {
        this.httpClient = httpClient;
        this.endpointUrl = endpointUrl;
    }

    /**
     * Consulta el endpoint de balanza y retorna la última pesada registrada.
     */
    public PesadaBalanzaDTO consultarPesadaMock() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.log(Level.WARNING, "Balanza retornó HTTP {0}: {1}", new Object[]{response.statusCode(), response.body()});
                throw new BusinessException("El servicio de balanza retornó código " + response.statusCode());
            }

            try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
                JsonObject json = reader.readObject();

                int idPesada = json.getInt("idPesada", 0);
                String fechaStr = json.getString("fecha");
                String horaStr = json.getString("hora");
                int peso = json.getInt("pesoRegistrado", 0);
                String matricula = json.containsKey("matricula") ? json.getString("matricula") : "";

                LocalDate fecha = LocalDate.parse(fechaStr);
                LocalTime hora = LocalTime.parse(horaStr);

                return new PesadaBalanzaDTO(idPesada, fecha, hora, peso, matricula, null);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Llamada a balanza interrumpida", ie);
            throw new BusinessException("La consulta a la balanza fue interrumpida.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al comunicar con balanza en {0}: {1}", new Object[]{endpointUrl, e.getMessage()});
            throw new BusinessException("No fue posible comunicarse con la balanza periférica: " + e.getMessage());
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }
}
