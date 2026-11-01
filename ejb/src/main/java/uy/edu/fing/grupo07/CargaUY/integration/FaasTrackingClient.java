package uy.edu.fing.grupo07.CargaUY.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
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
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para la invocación de la función Serverless (FaaS) en nube pública
 * encargada del procesamiento geoespacial de eventos de tracking y cálculo de desvío (Haversine).
 */
@ApplicationScoped
public class FaasTrackingClient {

    private static final Logger LOGGER = Logger.getLogger(FaasTrackingClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final double RADIO_TIERRA_KM = 6371.0088;

    private static final Map<String, double[]> CATALOGO_LOCALIDADES_UY = crearCatalogoLocalidades();

    private final HttpClient httpClient;
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
     * Envía un evento de tracking a la función FaaS por HTTPS para calcular el desvío en kilómetros.
     * Si el endpoint cloud no está configurado o falla por red, aplica el cálculo geométrico de respaldo
     * para no interrumpir la operación del sistema.
     */
    public ResultadoFaas procesarEvento(EventoViajeDTO evento, String origen, String destino) {
        if (evento == null) {
            throw new IllegalArgumentException("El evento de viaje no puede ser nulo.");
        }

        String origenSeguro = "Montevideo";
        if (origen != null && !origen.isBlank()) {
            origenSeguro = origen.trim();
        }

        String destinoSeguro = "Rivera";
        if (destino != null && !destino.isBlank()) {
            destinoSeguro = destino.trim();
        }

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            try {
                return invocarFuncionRemota(evento, origenSeguro, destinoSeguro);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Invocación a FaaS interrumpida, aplicando cálculo de respaldo", ie);
            } catch (Exception ex) {
                LOGGER.log(
                        Level.WARNING,
                        "No se pudo contactar el FaaS remoto en {0} ({1}). Aplicando cálculo Haversine de respaldo.",
                        new Object[]{endpointUrl, ex.getMessage()}
                );
            }
        }

        double desvioCalculado = calcularDesvioPuntoASegmentoKm(
                evento.latitud(),
                evento.longitud(),
                origenSeguro,
                destinoSeguro
        );

        EventoViajeDTO enriquecido = new EventoViajeDTO(
                evento.uuid(),
                evento.tiempo(),
                evento.tipo(),
                evento.latitud(),
                evento.longitud(),
                evento.guiaId(),
                evento.incidente(),
                desvioCalculado
        );

        String motivoFallback = "Cálculo Haversine ejecutado en modo respaldo local (configure FAAS_TRACKING_URL para nube pública)";
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            motivoFallback = "FaaS remoto no disponible en " + endpointUrl + "; calculado con respaldo Haversine local";
        }

        return new ResultadoFaas(enriquecido, desvioCalculado, false, motivoFallback);
    }

    private ResultadoFaas invocarFuncionRemota(
            EventoViajeDTO evento,
            String origen,
            String destino
    ) throws Exception {
        LocalDateTime tiempoEfectivo = evento.tiempo();
        if (tiempoEfectivo == null) {
            tiempoEfectivo = LocalDateTime.now();
        }

        String tipoStr = "INCIDENTE";
        if (evento.tipo() != null) {
            tipoStr = evento.tipo().name();
        }

        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("latitud", evento.latitud())
                .add("longitud", evento.longitud())
                .add("origen", origen)
                .add("destino", destino)
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
                    desvioKm
            );

            String timestampFaas = objetoResultado.getString("faasTimestamp", "");
            String detalle = "Procesado en Cloud FaaS (" + endpointUrl + ")";
            if (!timestampFaas.isBlank()) {
                detalle = detalle + " [" + timestampFaas + "]";
            }

            return new ResultadoFaas(enriquecido, desvioKm, true, detalle);
        }
    }

    /**
     * Calcula la distancia mínima en kilómetros (Haversine punto-a-segmento)
     * desde el punto (latP, lonP) hacia el corredor entre origen y destino.
     */
    public double calcularDesvioPuntoASegmentoKm(
            double latP,
            double lonP,
            String nombreOrigen,
            String nombreDestino
    ) {
        double[] coordsOrigen = resolverCoordenadasLocalidad(nombreOrigen);
        double[] coordsDestino = resolverCoordenadasLocalidad(nombreDestino);

        double latA = coordsOrigen[0];
        double lonA = coordsOrigen[1];
        double latB = coordsDestino[0];
        double lonB = coordsDestino[1];

        double latMediaRad = Math.toRadians((latA + latB + latP) / 3.0);
        double escalaLon = Math.cos(latMediaRad);

        double ax = lonA * escalaLon;
        double ay = latA;
        double bx = lonB * escalaLon;
        double by = latB;
        double px = lonP * escalaLon;
        double py = latP;

        double dx = bx - ax;
        double dy = by - ay;
        double normaCuadrada = (dx * dx) + (dy * dy);

        if (normaCuadrada < 1e-12) {
            double distanciaPunto = distanciaHaversineKm(latP, lonP, latA, lonA);
            return Math.round(distanciaPunto * 100.0) / 100.0;
        }

        double t = (((px - ax) * dx) + ((py - ay) * dy)) / normaCuadrada;
        double tAcotado = t;
        if (tAcotado < 0.0) {
            tAcotado = 0.0;
        } else if (tAcotado > 1.0) {
            tAcotado = 1.0;
        }

        double latC = latA + (tAcotado * (latB - latA));
        double lonC = lonA + (tAcotado * (lonB - lonA));

        double distancia = distanciaHaversineKm(latP, lonP, latC, lonC);
        return Math.round(distancia * 100.0) / 100.0;
    }

    public double[] obtenerPuntoMedioEnRuta(String nombreOrigen, String nombreDestino) {
        double[] coordsOrigen = resolverCoordenadasLocalidad(nombreOrigen);
        double[] coordsDestino = resolverCoordenadasLocalidad(nombreDestino);

        double latMedia = Math.round(((coordsOrigen[0] + coordsDestino[0]) / 2.0) * 10000.0) / 10000.0;
        double lonMedia = Math.round(((coordsOrigen[1] + coordsDestino[1]) / 2.0) * 10000.0) / 10000.0;
        return new double[]{latMedia, lonMedia};
    }

    public double[] obtenerPuntoDesviadoDeRuta(String nombreOrigen, String nombreDestino) {
        double[] puntoMedio = obtenerPuntoMedioEnRuta(nombreOrigen, nombreDestino);
        // Desplazar ~0.35 grados en latitud y longitud (~45 km fuera del corredor)
        double latDesviada = Math.round((puntoMedio[0] - 0.35) * 10000.0) / 10000.0;
        double lonDesviada = Math.round((puntoMedio[1] + 0.45) * 10000.0) / 10000.0;
        return new double[]{latDesviada, lonDesviada};
    }

    public double[] resolverCoordenadasLocalidad(String nombreLugar) {
        String clave = normalizarTexto(nombreLugar);
        if (CATALOGO_LOCALIDADES_UY.containsKey(clave)) {
            return CATALOGO_LOCALIDADES_UY.get(clave);
        }

        for (Map.Entry<String, double[]> entry : CATALOGO_LOCALIDADES_UY.entrySet()) {
            String localidad = entry.getKey();
            if (!clave.isEmpty() && (localidad.contains(clave) || clave.contains(localidad))) {
                return entry.getValue();
            }
        }

        return CATALOGO_LOCALIDADES_UY.get("montevideo");
    }

    private static double distanciaHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double senoPhi = Math.sin(dPhi / 2.0);
        double senoLambda = Math.sin(dLambda / 2.0);
        double a = (senoPhi * senoPhi)
                + (Math.cos(phi1) * Math.cos(phi2) * (senoLambda * senoLambda));
        double resto = 1.0 - a;
        if (resto < 0.0) {
            resto = 0.0;
        }
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(resto));
        return RADIO_TIERRA_KM * c;
    }

    private static String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        String minusculas = texto.trim().toLowerCase(Locale.ROOT);
        String descompuesto = Normalizer.normalize(minusculas, Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "");
    }

    private static Map<String, double[]> crearCatalogoLocalidades() {
        Map<String, double[]> mapa = new HashMap<>();
        mapa.put("montevideo", new double[]{-34.9011, -56.1645});
        mapa.put("puerto de montevideo", new double[]{-34.9033, -56.2114});
        mapa.put("canelones", new double[]{-34.5228, -56.2778});
        mapa.put("las piedras", new double[]{-34.7303, -56.2192});
        mapa.put("pando", new double[]{-34.7167, -55.9583});
        mapa.put("ciudad de la costa", new double[]{-34.8167, -55.9500});
        mapa.put("san jose", new double[]{-34.3375, -56.7136});
        mapa.put("san jose de mayo", new double[]{-34.3375, -56.7136});
        mapa.put("libertad", new double[]{-34.6333, -56.6167});
        mapa.put("colonia", new double[]{-34.4626, -57.8398});
        mapa.put("colonia del sacramento", new double[]{-34.4626, -57.8398});
        mapa.put("nueva palmira", new double[]{-33.8703, -58.4117});
        mapa.put("carmelo", new double[]{-34.0002, -58.2840});
        mapa.put("mercedes", new double[]{-33.2524, -58.0305});
        mapa.put("soriano", new double[]{-33.2524, -58.0305});
        mapa.put("fray bentos", new double[]{-33.1165, -58.3107});
        mapa.put("rio negro", new double[]{-33.1165, -58.3107});
        mapa.put("young", new double[]{-32.6983, -57.6269});
        mapa.put("paysandu", new double[]{-32.3171, -58.0807});
        mapa.put("salto", new double[]{-31.3833, -57.9667});
        mapa.put("artigas", new double[]{-30.4000, -56.4667});
        mapa.put("bella union", new double[]{-30.2597, -57.6008});
        mapa.put("rivera", new double[]{-30.9053, -55.5508});
        mapa.put("tacuarembo", new double[]{-31.7169, -55.9811});
        mapa.put("paso de los toros", new double[]{-32.8167, -56.5167});
        mapa.put("durazno", new double[]{-33.3806, -56.5236});
        mapa.put("florida", new double[]{-34.0956, -56.2142});
        mapa.put("flores", new double[]{-33.5165, -56.8996});
        mapa.put("trinidad", new double[]{-33.5165, -56.8996});
        mapa.put("lavalleja", new double[]{-34.3759, -55.2377});
        mapa.put("minas", new double[]{-34.3759, -55.2377});
        mapa.put("maldonado", new double[]{-34.9000, -54.9500});
        mapa.put("punta del este", new double[]{-34.9667, -54.9500});
        mapa.put("rocha", new double[]{-34.4833, -54.3333});
        mapa.put("chuy", new double[]{-33.6978, -53.4616});
        mapa.put("treinta y tres", new double[]{-33.2333, -54.3833});
        mapa.put("cerro largo", new double[]{-32.3703, -54.1675});
        mapa.put("melo", new double[]{-32.3703, -54.1675});
        mapa.put("rio branco", new double[]{-32.5986, -53.3858});
        return mapa;
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
