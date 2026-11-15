package uy.edu.fing.grupo07.CargaUY.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para la integración con el nodo periférico descentralizado de Tracking GPS (mock-tracking).
 * <p>
 * Permite controlar el nodo periférico desde el sistema central (iniciar, actualizar frecuencia y desvíos,
 * detener, reiniciar recorrido, emitir paso inmediato y sincronizar guías activas / drenar eventos emitidos),
 * aplicando Bulkhead, Circuit Breaker, Timeouts y propagación de {@code X-Correlation-ID}.
 * </p>
 */
@ApplicationScoped
public class TrackingClient implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(TrackingClient.class.getName());
    private static final Duration TIMEOUT = resolverTimeout();
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(10);

    private static Duration resolverTimeout() {
        String envTimeout = System.getenv("TRACKING_TIMEOUT_SECONDS");
        if (envTimeout != null && !envTimeout.isBlank()) {
            try {
                int secs = Integer.parseInt(envTimeout.trim());
                if (secs > 0) {
                    return Duration.ofSeconds(secs);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Duration.ofSeconds(15);
    }

    public record GuiaActivaPayload(
            Long id,
            String origen,
            String destino,
            String matricula,
            String corredorVial,
            String polilineaRuta
    ) implements Serializable {
    }

    public record EstadoNodoTracking(
            boolean activo,
            int eventosPorMinuto,
            int cantidadDesvios,
            long totalEventosEmitidos,
            long totalDesviosEmitidos,
            String ultimoResultado,
            List<EventoViajeDTO> eventosEmitidos
    ) implements Serializable {
    }

    private transient final HttpClient httpClient;
    private volatile String endpointUrl;
    private volatile boolean endpointExplicito;
    private final Semaphore capacidad = new Semaphore(8);
    private final AtomicLong llamadas = new AtomicLong();
    private final AtomicLong fallas = new AtomicLong();
    private final AtomicLong rechazadas = new AtomicLong();

    private int consecutivas = 0;
    private long abiertoHasta = 0L;
    private boolean prueba = false;

    public TrackingClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String propUrl = System.getProperty("tracking.endpoint");
        String envUrl = System.getenv("TRACKING_ENDPOINT");
        if (propUrl != null && !propUrl.isBlank()) {
            this.endpointUrl = propUrl.trim();
            this.endpointExplicito = true;
        } else if (envUrl != null && !envUrl.isBlank()) {
            this.endpointUrl = envUrl.trim();
            this.endpointExplicito = true;
        } else {
            this.endpointUrl = "http://localhost:8085";
            this.endpointExplicito = false;
        }
    }

    public TrackingClient(HttpClient httpClient, String endpointUrl) {
        this.httpClient = httpClient;
        this.endpointUrl = (endpointUrl != null && !endpointUrl.isBlank()) ? endpointUrl.trim() : "http://localhost:8085";
        this.endpointExplicito = true;
    }

    public EstadoNodoTracking iniciar(
            int eventosPorMinuto,
            int cantidadDesvios,
            List<GuiaActivaPayload> guiasActivas,
            String faasUrl,
            String correlacion
    ) {
        JsonObjectBuilder body = Json.createObjectBuilder()
                .add("eventosPorMinuto", eventosPorMinuto)
                .add("cantidadDesvios", cantidadDesvios)
                .add("guiasActivas", serializarGuias(guiasActivas));
        if (faasUrl != null && !faasUrl.isBlank()) {
            body.add("faasUrl", faasUrl.trim());
        }
        return ejecutarPost("/iniciar", body.build().toString(), correlacion);
    }

    public EstadoNodoTracking detener(String correlacion) {
        return ejecutarPost("/detener", "{}", correlacion);
    }

    public EstadoNodoTracking reiniciar(List<GuiaActivaPayload> guiasActivas, String correlacion) {
        JsonObjectBuilder body = Json.createObjectBuilder()
                .add("guiasActivas", serializarGuias(guiasActivas));
        return ejecutarPost("/reiniciar", body.build().toString(), correlacion);
    }

    public EstadoNodoTracking emitirPaso(
            int eventosPorMinuto,
            int cantidadDesvios,
            List<GuiaActivaPayload> guiasActivas,
            String faasUrl,
            String correlacion
    ) {
        JsonObjectBuilder body = Json.createObjectBuilder()
                .add("eventosPorMinuto", eventosPorMinuto)
                .add("cantidadDesvios", cantidadDesvios)
                .add("guiasActivas", serializarGuias(guiasActivas));
        if (faasUrl != null && !faasUrl.isBlank()) {
            body.add("faasUrl", faasUrl.trim());
        }
        return ejecutarPost("/paso", body.build().toString(), correlacion);
    }

    public EstadoNodoTracking sincronizarGuiasYDrenar(
            List<GuiaActivaPayload> guiasActivas,
            boolean drenar,
            String correlacion
    ) {
        JsonObjectBuilder body = Json.createObjectBuilder()
                .add("drenar", drenar)
                .add("guiasActivas", serializarGuias(guiasActivas));
        return ejecutarPost("/guias", body.build().toString(), correlacion);
    }

    public EstadoNodoTracking consultarEstado(boolean drenar, String correlacion) {
        String path = drenar ? "/estado?drenar=true" : "/estado";
        return ejecutarGet(path, correlacion);
    }

    public boolean verificarSalud() {
        if (estaCircuitoAbierto()) {
            return false;
        }
        for (String base : candidatosBaseUrl()) {
            try {
                String corr = TraceContext.normalizarCorrelacion(null);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/health"))
                        .timeout(HEALTH_TIMEOUT)
                        .header(TraceContext.HEADER_CORRELACION, corr)
                        .header(TraceContext.HEADER_TRACEPARENT, TraceContext.siguienteTraceparentHijo(null, corr))
                        .GET()
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    this.endpointUrl = base;
                    return true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private EstadoNodoTracking ejecutarGet(String path, String correlacion) {
        String corr = normalizarCorrelacion(correlacion);
        if (!capacidad.tryAcquire()) {
            rechazadas.incrementAndGet();
            throw new BusinessException("Capacidad de operaciones simultáneas al nodo de tracking agotada (Bulkhead).");
        }
        boolean admitida = false;
        boolean exito = false;
        long inicio = System.nanoTime();
        try {
            admitida = admitirCircuito();
            if (!admitida) {
                rechazadas.incrementAndGet();
                throw new BusinessException("Circuito del nodo periférico de tracking abierto temporalmente por fallas previas.");
            }
            Exception ultimoError = null;
            for (String base : candidatosBaseUrl()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(base + path))
                            .timeout(TIMEOUT)
                            .header(TraceContext.HEADER_CORRELACION, corr)
                            .header(TraceContext.HEADER_TRACEPARENT, TraceContext.siguienteTraceparentHijo(null, corr))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
                    llamadas.incrementAndGet();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        throw new BusinessException("El nodo de tracking retornó HTTP " + response.statusCode());
                    }
                    this.endpointUrl = base;
                    EstadoNodoTracking estado = parsearEstadoJson(response.body());
                    exito = true;
                    return estado;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ultimoError = ie;
                    break;
                } catch (Exception e) {
                    ultimoError = e;
                }
            }
            fallas.incrementAndGet();
            if (ultimoError instanceof BusinessException be) {
                throw be;
            }
            String detalle = ultimoError != null ? ultimoError.getMessage() : "Error desconocido";
            throw new BusinessException("No fue posible comunicarse con el nodo periférico de tracking: " + detalle);
        } finally {
            if (admitida) {
                registrarResultado(exito);
            }
            capacidad.release();
            final long duracionFinalMs = (System.nanoTime() - inicio) / 1_000_000;
            final boolean exitoFinal = exito;
            LOGGER.log(Level.FINE, () -> Json.createObjectBuilder()
                    .add("evento", "tracking.periferico.get")
                    .add("correlacion", corr)
                    .add("ok", exitoFinal)
                    .add("duracionMs", duracionFinalMs)
                    .build().toString());
        }
    }

    private EstadoNodoTracking ejecutarPost(String path, String jsonBody, String correlacion) {
        String corr = normalizarCorrelacion(correlacion);
        if (!capacidad.tryAcquire()) {
            rechazadas.incrementAndGet();
            throw new BusinessException("Capacidad de operaciones simultáneas al nodo de tracking agotada (Bulkhead).");
        }
        boolean admitida = false;
        boolean exito = false;
        long inicio = System.nanoTime();
        try {
            admitida = admitirCircuito();
            if (!admitida) {
                rechazadas.incrementAndGet();
                throw new BusinessException("Circuito del nodo periférico de tracking abierto temporalmente por fallas previas.");
            }
            Exception ultimoError = null;
            for (String base : candidatosBaseUrl()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(base + path))
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
                        throw new BusinessException("El nodo de tracking retornó HTTP " + response.statusCode());
                    }
                    this.endpointUrl = base;
                    EstadoNodoTracking estado = parsearEstadoJson(response.body());
                    exito = true;
                    return estado;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ultimoError = ie;
                    break;
                } catch (Exception e) {
                    ultimoError = e;
                }
            }
            fallas.incrementAndGet();
            if (ultimoError instanceof BusinessException be) {
                throw be;
            }
            String detalle = ultimoError != null ? ultimoError.getMessage() : "Error desconocido";
            throw new BusinessException("No fue posible comunicarse con el nodo periférico de tracking: " + detalle);
        } finally {
            if (admitida) {
                registrarResultado(exito);
            }
            capacidad.release();
            final long duracionFinalMs = (System.nanoTime() - inicio) / 1_000_000;
            final boolean exitoFinal = exito;
            final String pathFinal = path != null ? path : "";
            LOGGER.log(Level.FINE, () -> Json.createObjectBuilder()
                    .add("evento", "tracking.periferico.post")
                    .add("path", pathFinal)
                    .add("correlacion", corr)
                    .add("ok", exitoFinal)
                    .add("duracionMs", duracionFinalMs)
                    .build().toString());
        }
    }

    private List<String> candidatosBaseUrl() {
        String primaria = normalizarBaseUrl(endpointUrl);
        if (endpointExplicito) {
            return List.of(primaria);
        }
        List<String> lista = new ArrayList<>(2);
        lista.add(primaria);
        if (!"http://mock-tracking:8080".equalsIgnoreCase(primaria)) {
            lista.add("http://mock-tracking:8080");
        }
        if (!"http://localhost:8085".equalsIgnoreCase(primaria) && !lista.contains("http://localhost:8085")) {
            lista.add("http://localhost:8085");
        }
        return lista;
    }

    private static String normalizarBaseUrl(String raw) {
        String url = (raw == null || raw.isBlank()) ? "http://localhost:8085" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/estado")) {
            url = url.substring(0, url.length() - "/estado".length());
        } else if (url.endsWith("/health")) {
            url = url.substring(0, url.length() - "/health".length());
        }
        return url;
    }

    private static JsonArray serializarGuias(List<GuiaActivaPayload> guias) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        if (guias != null) {
            for (GuiaActivaPayload g : guias) {
                if (g == null || g.id() == null) {
                    continue;
                }
                JsonObjectBuilder obj = Json.createObjectBuilder()
                        .add("id", g.id())
                        .add("origen", g.origen() != null ? g.origen() : "Montevideo")
                        .add("destino", g.destino() != null ? g.destino() : "Rivera");
                if (g.matricula() != null) {
                    obj.add("matricula", g.matricula());
                }
                if (g.corredorVial() != null) {
                    obj.add("corredorVial", g.corredorVial());
                }
                if (g.polilineaRuta() != null) {
                    obj.add("polilineaRuta", g.polilineaRuta());
                }
                arr.add(obj);
            }
        }
        return arr.build();
    }

    private EstadoNodoTracking parsearEstadoJson(String body) {
        if (body == null || body.isBlank()) {
            return new EstadoNodoTracking(false, 12, 0, 0L, 0L, "Sin respuesta del nodo de tracking.", Collections.emptyList());
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject json = reader.readObject();
            boolean activo = json.getBoolean("activo", false);
            int epm = json.getInt("eventosPorMinuto", 12);
            int desvios = json.getInt("cantidadDesvios", 0);
            long totalEv = json.containsKey("totalEventosEmitidos") && !json.isNull("totalEventosEmitidos")
                    ? json.getJsonNumber("totalEventosEmitidos").longValue()
                    : 0L;
            long totalDesv = json.containsKey("totalDesviosEmitidos") && !json.isNull("totalDesviosEmitidos")
                    ? json.getJsonNumber("totalDesviosEmitidos").longValue()
                    : 0L;
            String ultimoRes = json.getString("ultimoResultado", "");

            List<EventoViajeDTO> eventos = new ArrayList<>();
            if (json.containsKey("eventosEmitidos") && json.get("eventosEmitidos").getValueType() == JsonValue.ValueType.ARRAY) {
                JsonArray arr = json.getJsonArray("eventosEmitidos");
                for (JsonValue val : arr) {
                    if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                        eventos.add(parsearEventoJson(val.asJsonObject()));
                    }
                }
            }
            return new EstadoNodoTracking(activo, epm, desvios, totalEv, totalDesv, ultimoRes, eventos);
        }
    }

    private EventoViajeDTO parsearEventoJson(JsonObject obj) {
        String uuid = obj.getString("uuid", UUID.randomUUID().toString());
        LocalDateTime tiempo = LocalDateTime.now();
        if (obj.containsKey("tiempo") && !obj.isNull("tiempo")) {
            try {
                String tStr = obj.getString("tiempo");
                if (tStr.endsWith("Z")) {
                    tStr = tStr.substring(0, tStr.length() - 1);
                }
                tiempo = LocalDateTime.parse(tStr);
            } catch (Exception ignored) {
            }
        }
        TipoEvento tipo = TipoEvento.POSICION;
        if (obj.containsKey("tipo") && !obj.isNull("tipo")) {
            try {
                tipo = TipoEvento.valueOf(obj.getString("tipo"));
            } catch (Exception ignored) {
            }
        }
        double lat = obj.containsKey("latitud") && !obj.isNull("latitud")
                ? obj.getJsonNumber("latitud").doubleValue()
                : 0.0;
        double lon = obj.containsKey("longitud") && !obj.isNull("longitud")
                ? obj.getJsonNumber("longitud").doubleValue()
                : 0.0;
        Long guiaId = obj.containsKey("guiaId") && !obj.isNull("guiaId")
                ? obj.getJsonNumber("guiaId").longValue()
                : null;
        Double desvioKm = null;
        if (obj.containsKey("distanciaDesvioKm") && !obj.isNull("distanciaDesvioKm")) {
            JsonNumber num = obj.getJsonNumber("distanciaDesvioKm");
            if (num != null) {
                desvioKm = num.doubleValue();
            }
        }
        IncidenteDTO incDto = null;
        if (obj.containsKey("incidente") && obj.get("incidente").getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject incObj = obj.getJsonObject("incidente");
            String foto = incObj.containsKey("foto") && !incObj.isNull("foto") ? incObj.getString("foto") : null;
            String desc = incObj.containsKey("descripcion") && !incObj.isNull("descripcion") ? incObj.getString("descripcion") : null;
            incDto = new IncidenteDTO(foto, desc);
        }
        String matricula = obj.containsKey("matricula") && !obj.isNull("matricula") ? obj.getString("matricula") : null;

        return new EventoViajeDTO(
                uuid,
                tiempo,
                tipo,
                lat,
                lon,
                guiaId,
                incDto,
                desvioKm,
                null,
                null,
                null,
                matricula
        );
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
        return normalizarBaseUrl(endpointUrl);
    }

    public void setEndpointUrl(String endpointUrl) {
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            this.endpointUrl = endpointUrl.trim();
            this.endpointExplicito = true;
        }
    }
}
