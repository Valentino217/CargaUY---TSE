package uy.edu.fing.grupo07.CargaUY.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para la invocación de la función Serverless (FaaS) en nube pública
 * encargada del procesamiento geoespacial de eventos de tracking y cálculo de desvío
 * sobre polilíneas reales de la Red Vial Nacional de Uruguay (MTOP).
 */
@ApplicationScoped
public class FaasTrackingClient implements java.io.Serializable {

    private static final Logger LOGGER = Logger.getLogger(FaasTrackingClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private transient final HttpClient httpClient;
    private volatile String endpointUrl;
    private final String sharedSecret;

    public record ResultadoFaas(
            EventoViajeDTO eventoEnriquecido,
            double distanciaDesvioKm,
            boolean invocacionCloudExitosa,
            String detalleEjecucion
    ) {
    }

    public FaasTrackingClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.endpointUrl = resolverUrlInicial();
        this.sharedSecret = resolverSecretoInicial();
    }

    public FaasTrackingClient(HttpClient httpClient, String endpointUrl, String sharedSecret) {
        this.httpClient = httpClient;
        if (endpointUrl != null) {
            this.endpointUrl = endpointUrl.trim();
        } else {
            this.endpointUrl = "";
        }
        if (sharedSecret != null) {
            this.sharedSecret = sharedSecret.trim();
        } else {
            this.sharedSecret = "";
        }
    }

    private static String resolverUrlInicial() {
        String prop = System.getProperty("cargauy.faas.url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("FAAS_TRACKING_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "";
    }

    private static String resolverSecretoInicial() {
        String prop = System.getProperty("cargauy.faas.secret");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("FAAS_SHARED_SECRET");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "";
    }

    /**
     * Envía un evento de tracking a la función FaaS por HTTPS para calcular el desvío en kilómetros
     * respecto a la polilínea de rutas nacionales entre origen y destino.
     */
    public ResultadoFaas procesarEvento(EventoViajeDTO evento, String origen, String destino) {
        return procesarEvento(evento, origen, destino, null);
    }

    /**
     * Envía un evento de tracking a la función FaaS por HTTPS incluyendo la polilínea planificada de la guía.
     * Valida estrictamente que las localidades de origen y destino sean reconocidas en Uruguay (sin fallback silencioso).
     */
    public ResultadoFaas procesarEvento(
            EventoViajeDTO evento,
            String origen,
            String destino,
            String polilineaSerializada
    ) {
        if (evento == null) {
            throw new IllegalArgumentException("El evento de viaje no puede ser nulo.");
        }
        if (origen == null || origen.isBlank()) {
            throw new IllegalArgumentException("La localidad de origen es obligatoria para calcular el desvío.");
        }
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("La localidad de destino es obligatoria para calcular el desvío.");
        }

        String origenLimpio = origen.trim();
        String destinoLimpio = destino.trim();

        // Validar estrictamente las localidades y resolver la polilínea de ruta real en Uruguay
        RedVialUruguay.RutaPlanificada rutaPlanificada = RedVialUruguay.calcularRuta(origenLimpio, destinoLimpio);
        List<double[]> polilinea = RedVialUruguay.deserializarPolilinea(polilineaSerializada);
        if (polilinea.isEmpty()) {
            polilinea = rutaPlanificada.polilinea();
        }

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            try {
                return invocarFuncionRemota(evento, origenLimpio, destinoLimpio, polilinea, rutaPlanificada.corredorVial());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Invocación a FaaS interrumpida, aplicando cálculo de polilínea local", ie);
            } catch (Exception ex) {
                LOGGER.log(
                        Level.WARNING,
                        "No se pudo contactar el FaaS remoto en {0} ({1}). Aplicando cálculo sobre polilínea vial de respaldo.",
                        new Object[]{endpointUrl, ex.getMessage()}
                );
            }
        }

        double desvioCalculado = RedVialUruguay.calcularDesvioPolilineaKm(
                evento.latitud(),
                evento.longitud(),
                polilinea
        );

        EventoViajeDTO enriquecido = new EventoViajeDTO(
                evento.uuid(),
                evento.tiempo(),
                evento.tipo(),
                evento.latitud(),
                evento.longitud(),
                evento.guiaId(),
                evento.incidente(),
                desvioCalculado,
                evento.cantidadKg(),
                evento.mercaderia(),
                evento.empresaCarga(),
                evento.matricula()
        );

        String motivoFallback = "Cálculo sobre polilínea vial (" + rutaPlanificada.corredorVial()
                + ") ejecutado en motor local";
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            motivoFallback = "FaaS remoto no disponible en " + endpointUrl
                    + "; calculado sobre polilínea vial (" + rutaPlanificada.corredorVial() + ")";
        }

        return new ResultadoFaas(enriquecido, desvioCalculado, false, motivoFallback);
    }

    private ResultadoFaas invocarFuncionRemota(
            EventoViajeDTO evento,
            String origen,
            String destino,
            List<double[]> polilinea,
            String corredorVial
    ) throws Exception {
        LocalDateTime tiempoEfectivo = evento.tiempo();
        if (tiempoEfectivo == null) {
            tiempoEfectivo = LocalDateTime.now();
        }

        String tipoStr = "INCIDENTE";
        if (evento.tipo() != null) {
            tipoStr = evento.tipo().name();
        }

        JsonArrayBuilder waypointsBuilder = Json.createArrayBuilder();
        for (double[] wp : polilinea) {
            waypointsBuilder.add(Json.createArrayBuilder().add(wp[0]).add(wp[1]));
        }

        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("latitud", evento.latitud())
                .add("longitud", evento.longitud())
                .add("origen", origen)
                .add("destino", destino)
                .add("corredorVial", corredorVial != null ? corredorVial : "")
                .add("waypoints", waypointsBuilder)
                .add("tipo", tipoStr)
                .add("tiempo", tiempoEfectivo.toString());

        if (evento.uuid() != null) {
            builder.add("uuid", evento.uuid());
        }
        if (evento.guiaId() != null) {
            builder.add("guiaId", evento.guiaId());
        }

        String payloadJson = builder.build().toString();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson));

        if (sharedSecret != null && !sharedSecret.isBlank()) {
            reqBuilder.header("X-FaaS-Secret", sharedSecret);
        }

        HttpResponse<String> response = httpClient.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("El FaaS devolvió HTTP " + response.statusCode() + ": " + response.body());
        }

        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            JsonObject json = reader.readObject();

            JsonObject objetoResultado = json;
            if (json.containsKey("body") && json.get("body").getValueType() == jakarta.json.JsonValue.ValueType.STRING) {
                try (JsonReader innerReader = Json.createReader(new StringReader(json.getString("body")))) {
                    objetoResultado = innerReader.readObject();
                }
            }

            JsonNumber numeroDesvio = objetoResultado.getJsonNumber("distanciaDesvioKm");
            if (numeroDesvio == null) {
                throw new IllegalStateException("La respuesta del FaaS no contiene el campo 'distanciaDesvioKm'.");
            }

            double desvioKm = Math.round(numeroDesvio.doubleValue() * 100.0) / 100.0;
            EventoViajeDTO enriquecido = new EventoViajeDTO(
                    evento.uuid(),
                    evento.tiempo(),
                    evento.tipo(),
                    evento.latitud(),
                    evento.longitud(),
                    evento.guiaId(),
                    evento.incidente(),
                    desvioKm,
                    evento.cantidadKg(),
                    evento.mercaderia(),
                    evento.empresaCarga(),
                    evento.matricula()
            );

            String timestampFaas = objetoResultado.getString("faasTimestamp", "");
            String detalle = "Procesado en Cloud FaaS (" + endpointUrl + ") sobre " + corredorVial;
            if (!timestampFaas.isBlank()) {
                detalle = detalle + " [" + timestampFaas + "]";
            }

            return new ResultadoFaas(enriquecido, desvioKm, true, detalle);
        }
    }

    /**
     * Calcula la distancia mínima en kilómetros desde el punto (latP, lonP) hacia la polilínea
     * de rutas nacionales que une {@code nombreOrigen} y {@code nombreDestino}.
     */
    public double calcularDesvioPuntoASegmentoKm(
            double latP,
            double lonP,
            String nombreOrigen,
            String nombreDestino
    ) {
        RedVialUruguay.RutaPlanificada ruta = RedVialUruguay.calcularRuta(nombreOrigen, nombreDestino);
        return RedVialUruguay.calcularDesvioPolilineaKm(latP, lonP, ruta.polilinea());
    }

    public double[] obtenerPuntoMedioEnRuta(String nombreOrigen, String nombreDestino) {
        return RedVialUruguay.obtenerPuntoEnPolilinea(RedVialUruguay.calcularRuta(nombreOrigen, nombreDestino).polilinea());
    }

    public double[] obtenerPuntoDesviadoDeRuta(String nombreOrigen, String nombreDestino) {
        return RedVialUruguay.obtenerPuntoDesviadoDePolilinea(RedVialUruguay.calcularRuta(nombreOrigen, nombreDestino).polilinea());
    }

    /**
     * Resuelve las coordenadas geográficas de una localidad oficial de Uruguay.
     * Lanza {@link IllegalArgumentException} si la localidad no existe en el catálogo nacional.
     */
    public double[] resolverCoordenadasLocalidad(String nombreLugar) {
        RedVialUruguay.NodoVial nodo = RedVialUruguay.resolverNodo(nombreLugar);
        return new double[]{nodo.latitud(), nodo.longitud()};
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        if (endpointUrl != null) {
            this.endpointUrl = endpointUrl.trim();
        } else {
            this.endpointUrl = "";
        }
    }

    public String getSharedSecret() {
        return sharedSecret;
    }
}
