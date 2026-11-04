package uy.edu.fing.grupo07.CargaUY.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para la consulta de pesadas desde el periférico/mock de balanza.
 * Cuenta con timeout estricto de 5 segundos, Bulkhead, Circuit Breaker (3 fallos -> 10s abierto),
 * política de reintentos acotada (hasta 2 intentos), propagación de X-Correlation-ID y métricas.
 */
@ApplicationScoped
public class BalanzaClient {

    private static final Logger LOGGER = Logger.getLogger(BalanzaClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String endpointUrl;
    private final Semaphore capacidad = new Semaphore(8);
    private final AtomicLong llamadas = new AtomicLong();
    private final AtomicLong fallas = new AtomicLong();
    private final AtomicLong rechazadas = new AtomicLong();

    private int consecutivas = 0;
    private long abiertoHasta = 0L;
    private boolean prueba = false;

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

            llamadas.incrementAndGet();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                fallas.incrementAndGet();
                registrarResultado(false);
                LOGGER.log(Level.WARNING, "Balanza retornó HTTP {0}: {1}", new Object[]{response.statusCode(), response.body()});
                throw new BusinessException("El servicio de balanza retornó código " + response.statusCode());
            }

            try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
                JsonObject json = reader.readObject();
                PesadaBalanzaDTO dto = parsearPesadaJson(json);
                registrarResultado(true);
                return dto;
            }
        } catch (BusinessException be) {
            throw be;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fallas.incrementAndGet();
            registrarResultado(false);
            LOGGER.log(Level.SEVERE, "Llamada a balanza interrumpida", ie);
            throw new BusinessException("La consulta a la balanza fue interrumpida.");
        } catch (Exception e) {
            fallas.incrementAndGet();
            registrarResultado(false);
            LOGGER.log(Level.WARNING, "Error al comunicar con balanza en {0}: {1}", new Object[]{endpointUrl, e.getMessage()});
            throw new BusinessException("No fue posible comunicarse con la balanza periférica: " + e.getMessage());
        }
    }

    /**
     * Consulta una pesada aplicando Bulkhead, Circuit Breaker, política de reintentos (hasta 2 intentos)
     * y trazabilidad distribuida con X-Correlation-ID.
     */
    public PesadaBalanzaDTO consultarPesadaConResiliencia(String correlacion) {
        List<PesadaBalanzaDTO> lista = ejecutarConsultaResiliente(endpointUrl, correlacion);
        if (lista.isEmpty()) {
            throw new BusinessException("El periférico de balanza no retornó registros de pesada.");
        }
        return lista.get(0);
    }

    /**
     * Consulta las pesadas registradas para una matrícula y fecha determinadas (Sección 4.3.5),
     * enviando parámetros de consulta (?matricula=...&fecha=...) y encabezado X-Correlation-ID.
     */
    public List<PesadaBalanzaDTO> consultarPesadasVehiculo(String matricula, LocalDate fecha, String correlacion) {
        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("La matrícula es obligatoria para consultar pesadas de vehículo.");
        }
        LocalDate fechaConsulta = (fecha != null) ? fecha : LocalDate.now();
        String sep = endpointUrl.contains("?") ? "&" : "?";
        String targetUrl = endpointUrl + sep
                + "matricula=" + URLEncoder.encode(matricula.trim(), StandardCharsets.UTF_8)
                + "&fecha=" + URLEncoder.encode(fechaConsulta.toString(), StandardCharsets.UTF_8);
        return ejecutarConsultaResiliente(targetUrl, correlacion);
    }

    private List<PesadaBalanzaDTO> ejecutarConsultaResiliente(String url, String correlacion) {
        String corr = normalizarCorrelacion(correlacion);
        if (!capacidad.tryAcquire()) {
            rechazadas.incrementAndGet();
            throw new BusinessException("Capacidad de consultas simultáneas a balanza agotada (Bulkhead).");
        }
        boolean admitida = false;
        boolean exito = false;
        long inicio = System.nanoTime();
        try {
            admitida = admitirCircuito();
            if (!admitida) {
                rechazadas.incrementAndGet();
                throw new BusinessException("Circuito de balanza periférica abierto temporalmente por fallas previas.");
            }
            Exception ultimoError = null;
            for (int intento = 0; intento < 2; intento++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(TIMEOUT)
                            .header("X-Correlation-ID", corr)
                            .header("Accept", "application/json")
                            .GET()
                            .build();

                    llamadas.incrementAndGet();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        throw new BusinessException("El servicio de balanza retornó código " + response.statusCode());
                    }

                    List<PesadaBalanzaDTO> resultado = parsearRespuestaBalanza(response.body());
                    exito = true;
                    return resultado;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ultimoError = ie;
                    break;
                } catch (Exception e) {
                    ultimoError = e;
                    if (intento == 0) {
                        continue;
                    }
                }
            }
            fallas.incrementAndGet();
            if (ultimoError instanceof BusinessException be) {
                throw be;
            }
            String detalle = ultimoError != null ? ultimoError.getMessage() : "Error desconocido";
            throw new BusinessException("No fue posible comunicarse con la balanza periférica: " + detalle);
        } finally {
            if (admitida) {
                registrarResultado(exito);
            }
            capacidad.release();
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            LOGGER.info("{\"evento\":\"balanza.consulta\",\"correlacion\":\"" + corr
                    + "\",\"ok\":" + exito + ",\"duracionMs\":" + duracionMs + "}");
        }
    }

    private List<PesadaBalanzaDTO> parsearRespuestaBalanza(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonStructure struct = reader.read();
            if (struct.getValueType() == JsonValue.ValueType.ARRAY) {
                JsonArray arr = struct.asJsonArray();
                List<PesadaBalanzaDTO> lista = new ArrayList<>(arr.size());
                for (JsonValue val : arr) {
                    if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                        lista.add(parsearPesadaJson(val.asJsonObject()));
                    }
                }
                return lista;
            } else if (struct.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject obj = struct.asJsonObject();
                if (obj.containsKey("pesadas") && obj.get("pesadas").getValueType() == JsonValue.ValueType.ARRAY) {
                    JsonArray arr = obj.getJsonArray("pesadas");
                    List<PesadaBalanzaDTO> lista = new ArrayList<>(arr.size());
                    for (JsonValue val : arr) {
                        if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                            lista.add(parsearPesadaJson(val.asJsonObject()));
                        }
                    }
                    return lista;
                }
                return List.of(parsearPesadaJson(obj));
            }
            return Collections.emptyList();
        }
    }

    private PesadaBalanzaDTO parsearPesadaJson(JsonObject json) {
        int idPesada = json.getInt("idPesada", 0);
        String fechaStr = json.getString("fecha", LocalDate.now().toString());
        String horaStr = json.getString("hora", LocalTime.now().withNano(0).toString());
        int peso = json.getInt("pesoRegistrado", 0);
        String matricula = json.containsKey("matricula") && !json.isNull("matricula")
                ? json.getString("matricula")
                : "";

        LocalDate fecha = LocalDate.parse(fechaStr);
        LocalTime hora = LocalTime.parse(horaStr);

        return new PesadaBalanzaDTO(idPesada, fecha, hora, peso, matricula, null);
    }

    /**
     * Sondeo rápido de salud (timeout 2s) que retorna true si el nodo responde HTTP 200 y el circuito está cerrado.
     */
    public boolean verificarSalud() {
        if (estaCircuitoAbierto()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(HEALTH_TIMEOUT)
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean estaCircuitoAbierto() {
        if (abiertoHasta == 0L) {
            return false;
        }
        return System.nanoTime() < abiertoHasta;
    }

    private synchronized boolean admitirCircuito() {
        if (abiertoHasta == 0L) {
            return true;
        }
        if (System.nanoTime() < abiertoHasta || prueba) {
            return false;
        }
        prueba = true;
        return true;
    }

    public synchronized void registrarResultado(boolean exito) {
        if (prueba) {
            prueba = false;
            if (exito) {
                consecutivas = 0;
                abiertoHasta = 0L;
            } else {
                abiertoHasta = System.nanoTime() + CIRCUIT_OPEN_DURATION.toNanos();
            }
        } else if (abiertoHasta == 0L) {
            if (exito) {
                consecutivas = 0;
            } else if (++consecutivas >= 3) {
                abiertoHasta = System.nanoTime() + CIRCUIT_OPEN_DURATION.toNanos();
            }
        }
    }

    public long[] metricas() {
        return new long[]{llamadas.get(), fallas.get(), rechazadas.get()};
    }

    private static String normalizarCorrelacion(String correlacion) {
        if (correlacion == null || correlacion.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(correlacion.trim()).toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }
}
