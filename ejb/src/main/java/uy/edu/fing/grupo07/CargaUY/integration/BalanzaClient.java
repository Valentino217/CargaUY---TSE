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
    private static final Duration TIMEOUT = resolverTimeout();
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(10);

    private static Duration resolverTimeout() {
        String envTimeout = System.getenv("BALANZA_TIMEOUT_SECONDS");
        if (envTimeout != null && !envTimeout.isBlank()) {
            try {
                int secs = Integer.parseInt(envTimeout.trim());
                if (secs > 0) {
                    return Duration.ofSeconds(secs);
                }
            } catch (NumberFormatException ignored) {
                // fallback al valor por defecto
            }
        }
        return Duration.ofSeconds(15);
    }

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
                .followRedirects(HttpClient.Redirect.NORMAL)
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
        List<PesadaBalanzaDTO> lista = ejecutarConsultaResiliente(resolverPesadasUrl(), correlacion);
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
        return consultarPesadasVehiculo(matricula, fecha, null, correlacion);
    }

    /**
     * Consulta las pesadas registradas en el nodo periférico de balanzas filtrando por matrícula,
     * fecha opcional y número de empresa opcional (?matricula=...&fecha=...&nroEmpresa=...).
     */
    public List<PesadaBalanzaDTO> consultarPesadasVehiculo(String matricula, LocalDate fecha, Integer nroEmpresa, String correlacion) {
        return consultarPesadasVehiculoEnEndpoint(null, matricula, fecha, nroEmpresa, correlacion);
    }

    public List<PesadaBalanzaDTO> consultarPesadasVehiculoEnEndpoint(
            String baseUrlOverride,
            String matricula,
            LocalDate fecha,
            Integer nroEmpresa,
            String correlacion
    ) {
        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("La matrícula es obligatoria para consultar pesadas de vehículo.");
        }
        String pesadasBase = resolverPesadasUrl(baseUrlOverride);
        String sep = pesadasBase.contains("?") ? "&" : "?";
        StringBuilder sb = new StringBuilder(pesadasBase)
                .append(sep)
                .append("matricula=").append(URLEncoder.encode(matricula.trim(), StandardCharsets.UTF_8));
        if (fecha != null) {
            sb.append("&fecha=").append(URLEncoder.encode(fecha.toString(), StandardCharsets.UTF_8));
        }
        if (nroEmpresa != null) {
            sb.append("&nroEmpresa=").append(nroEmpresa);
        }
        return ejecutarConsultaResiliente(sb.toString(), correlacion);
    }

    /**
     * Consulta las pesadas registradas en el nodo periférico de balanzas para un rango de fechas
     * (?matricula=...&fechaDesde=...&fechaHasta=...&nroEmpresa=...), útil para viajes multi-día.
     */
    public List<PesadaBalanzaDTO> consultarPesadasVehiculo(
            String matricula,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Integer nroEmpresa,
            String correlacion
    ) {
        return consultarPesadasVehiculoEnEndpoint(null, matricula, fechaDesde, fechaHasta, nroEmpresa, correlacion);
    }

    public List<PesadaBalanzaDTO> consultarPesadasVehiculoEnEndpoint(
            String baseUrlOverride,
            String matricula,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Integer nroEmpresa,
            String correlacion
    ) {
        if (matricula == null || matricula.isBlank()) {
            throw new BusinessException("La matrícula es obligatoria para consultar pesadas de vehículo.");
        }
        String pesadasBase = resolverPesadasUrl(baseUrlOverride);
        String sep = pesadasBase.contains("?") ? "&" : "?";
        StringBuilder sb = new StringBuilder(pesadasBase)
                .append(sep)
                .append("matricula=").append(URLEncoder.encode(matricula.trim(), StandardCharsets.UTF_8));
        if (fechaDesde != null) {
            sb.append("&fechaDesde=").append(URLEncoder.encode(fechaDesde.toString(), StandardCharsets.UTF_8));
        }
        if (fechaHasta != null) {
            sb.append("&fechaHasta=").append(URLEncoder.encode(fechaHasta.toString(), StandardCharsets.UTF_8));
        }
        if (nroEmpresa != null) {
            sb.append("&nroEmpresa=").append(nroEmpresa);
        }
        return ejecutarConsultaResiliente(sb.toString(), correlacion);
    }

    /**
     * Consulta todas las pesadas registradas en una fecha dada en el endpoint indicado (?fecha=YYYY-MM-DD),
     * utilizado por el sondeo periódico para detectar lecturas nuevas (incluyendo vehículos sin guía).
     */
    public List<PesadaBalanzaDTO> consultarPesadasPorFechaEnEndpoint(
            String baseUrlOverride,
            LocalDate fecha,
            String correlacion
    ) {
        LocalDate fechaEfectiva = fecha != null ? fecha : LocalDate.now();
        String pesadasBase = resolverPesadasUrl(baseUrlOverride);
        String sep = pesadasBase.contains("?") ? "&" : "?";
        String url = pesadasBase + sep + "fecha=" + URLEncoder.encode(fechaEfectiva.toString(), StandardCharsets.UTF_8);
        return ejecutarConsultaResiliente(url, correlacion);
    }

    /**
     * Registra una nueva pesada en el nodo periférico de balanzas (POST /pesadas) aplicando
     * Bulkhead, Circuit Breaker y propagación de X-Correlation-ID.
     */
    public PesadaBalanzaDTO registrarPesadaEnPeriferico(PesadaBalanzaDTO dto, Integer nroEmpresa, String peaje, String correlacion) {
        return registrarPesadaEnPerifericoEnEndpoint(null, dto, nroEmpresa, peaje, correlacion);
    }

    public PesadaBalanzaDTO registrarPesadaEnPerifericoEnEndpoint(
            String baseUrlOverride,
            PesadaBalanzaDTO dto,
            Integer nroEmpresa,
            String peaje,
            String correlacion
    ) {
        if (dto == null || dto.matricula() == null || dto.matricula().isBlank()) {
            throw new BusinessException("La matrícula es obligatoria para registrar una pesada en balanza.");
        }
        String corr = normalizarCorrelacion(correlacion);
        if (!capacidad.tryAcquire()) {
            rechazadas.incrementAndGet();
            throw new BusinessException("Capacidad de operaciones simultáneas a balanza agotada (Bulkhead).");
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
            var payloadBuilder = Json.createObjectBuilder()
                    .add("matricula", dto.matricula().trim())
                    .add("pesoRegistrado", dto.pesoRegistrado());
            if (nroEmpresa != null) {
                payloadBuilder.add("nroEmpresa", nroEmpresa);
            }
            if (peaje != null && !peaje.isBlank()) {
                payloadBuilder.add("peaje", peaje.trim());
            }
            if (dto.fecha() != null) {
                payloadBuilder.add("fecha", dto.fecha().toString());
            }
            if (dto.hora() != null) {
                payloadBuilder.add("hora", dto.hora().withNano(0).toString());
            }
            String jsonBody = payloadBuilder.build().toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolverPesadasUrl(baseUrlOverride)))
                    .timeout(TIMEOUT)
                    .header(TraceContext.HEADER_CORRELACION, corr)
                    .header(TraceContext.HEADER_TRACEPARENT, TraceContext.siguienteTraceparentHijo(null, corr))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            llamadas.incrementAndGet();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new BusinessException("El nodo de balanza rechazó el registro con código " + response.statusCode());
            }

            List<PesadaBalanzaDTO> resultado = parsearRespuestaBalanza(response.body());
            if (resultado.isEmpty()) {
                exito = true;
                return dto;
            }
            exito = true;
            return resultado.get(0);
        } catch (BusinessException be) {
            if (!admitida || be.getMessage() == null || !be.getMessage().contains("Circuito")) {
                fallas.incrementAndGet();
            }
            throw be;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fallas.incrementAndGet();
            throw new BusinessException("El registro en la balanza periférica fue interrumpido.");
        } catch (Exception e) {
            fallas.incrementAndGet();
            throw new BusinessException("No fue posible registrar la pesada en la balanza periférica: " + e.getMessage());
        } finally {
            if (admitida) {
                registrarResultado(exito);
            }
            capacidad.release();
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            LOGGER.info("{\"evento\":\"balanza.registro\",\"correlacion\":\"" + corr
                    + "\",\"ok\":" + exito + ",\"duracionMs\":" + duracionMs + "}");
        }
    }

    private String baseUrl() {
        return normalizarBaseUrl(endpointUrl);
    }

    private String normalizarBaseUrl(String rawOverride) {
        String raw = (rawOverride != null && !rawOverride.isBlank()) ? rawOverride : endpointUrl;
        String url = raw == null ? "http://localhost:8082" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/pesadas")) {
            url = url.substring(0, url.length() - "/pesadas".length());
        } else if (url.endsWith("/health")) {
            url = url.substring(0, url.length() - "/health".length());
        }
        return url;
    }

    private String resolverPesadasUrl() {
        return resolverPesadasUrl(null);
    }

    private String resolverPesadasUrl(String baseUrlOverride) {
        return normalizarBaseUrl(baseUrlOverride) + "/pesadas";
    }

    private String resolverHealthUrl() {
        return resolverHealthUrl(null);
    }

    private String resolverHealthUrl(String baseUrlOverride) {
        return normalizarBaseUrl(baseUrlOverride) + "/health";
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
                            .header(TraceContext.HEADER_CORRELACION, corr)
                            .header(TraceContext.HEADER_TRACEPARENT, TraceContext.siguienteTraceparentHijo(null, corr))
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
     * Sondeo rápido de salud (timeout 2s) contra /health que retorna true si el nodo responde HTTP 200 y el circuito está cerrado.
     */
    public boolean verificarSalud() {
        return verificarSaludEnEndpoint(null);
    }

    public boolean verificarSaludEnEndpoint(String baseUrlOverride) {
        if (estaCircuitoAbierto()) {
            return false;
        }
        try {
            String corr = TraceContext.normalizarCorrelacion(null);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolverHealthUrl(baseUrlOverride)))
                    .timeout(HEALTH_TIMEOUT)
                    .header(TraceContext.HEADER_CORRELACION, corr)
                    .header(TraceContext.HEADER_TRACEPARENT, TraceContext.siguienteTraceparentHijo(null, corr))
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
        return TraceContext.normalizarCorrelacion(correlacion);
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }
}
