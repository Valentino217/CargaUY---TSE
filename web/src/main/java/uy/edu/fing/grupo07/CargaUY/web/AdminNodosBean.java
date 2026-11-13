package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.Config;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Managed Bean para /admin/nodos.xhtml.
 * Gestión y monitoreo en tiempo real de nodos periféricos (Balanzas, Tracking, PDI, LDAP, gub.uy, NoSQL).
 */
@Named("adminNodosBean")
@ViewScoped
public class AdminNodosBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String ESTADO_CONSULTANDO = "CONSULTANDO";
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(350);
    private static final Duration HTTP_PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private PosicionNoSqlRepositoryLocal posicionNoSqlRepository;

    public record NodoPerifericoVista(
            String codigo,
            String nombre,
            String tipo,
            String endpoint,
            boolean online,
            String detalleEstado
    ) implements Serializable {
        public String estado() {
            if (ESTADO_CONSULTANDO.equals(detalleEstado)) {
                return ESTADO_CONSULTANDO;
            }
            return online ? "ONLINE" : "OFFLINE";
        }

        public boolean isConsultando() {
            return ESTADO_CONSULTANDO.equals(detalleEstado);
        }
    }

    private List<NodoPerifericoVista> nodos;

    @PostConstruct
    public void init() {
        cargarEsqueletoNodos();
    }

    /**
     * Construye la tabla de nodos al instante sin realizar I/O de red, dejando cada nodo en
     * estado CONSULTANDO hasta que el sondeo asíncrono por AJAX complete la verificación.
     */
    public void cargarEsqueletoNodos() {
        String balanzaBase = resolverBalanzaBase();
        String trackingBase = resolverTrackingBase();
        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        String ldapEndpoint = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        String oidcEndpoint = Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
        String oidcNombre = esLocalOidc(oidcEndpoint) ? "Login gub.uy (Keycloak local)" : "Login gub.uy (ID Uruguay)";
        String mongoEndpoint = posicionNoSqlRepository != null
                ? posicionNoSqlRepository.obtenerDescriptorEndpoint()
                : "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";

        nodos = List.of(
                new NodoPerifericoVista("NODO-BAL-01", "Nodo Periférico Balanzas", "HTTP", balanzaBase, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-TRK-01", "Nodo Periférico Tracking", "HTTP", trackingBase, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-PDI-01", "Servicio PDI (DNIC)", "SOAP", pdiEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-LDAP-01", "Directorio OpenLDAP", "LDAP", ldapEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-OIDC-01", oidcNombre, "OIDC", oidcEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NOSQL-MONGO-01", "Base NoSQL (MongoDB)", "MongoDB", mongoEndpoint, false, ESTADO_CONSULTANDO)
        );
    }

    public void verificarNodos() {
        CompletableFuture<NodoPerifericoVista> fBalanza = CompletableFuture.supplyAsync(this::sondearBalanza);
        CompletableFuture<NodoPerifericoVista> fTracking = CompletableFuture.supplyAsync(this::sondearTracking);
        CompletableFuture<NodoPerifericoVista> fPdi = CompletableFuture.supplyAsync(this::sondearPdi);
        CompletableFuture<NodoPerifericoVista> fLdap = CompletableFuture.supplyAsync(this::sondearLdap);
        CompletableFuture<NodoPerifericoVista> fOidc = CompletableFuture.supplyAsync(this::sondearOidc);
        CompletableFuture<NodoPerifericoVista> fMongo = CompletableFuture.supplyAsync(this::sondearMongo);

        CompletableFuture.allOf(fBalanza, fTracking, fPdi, fLdap, fOidc, fMongo).join();
        nodos = List.of(
                fBalanza.join(),
                fTracking.join(),
                fPdi.join(),
                fLdap.join(),
                fOidc.join(),
                fMongo.join()
        );
    }

    private String resolverBalanzaBase() {
        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        boolean tieneEnvBalanza = envBalanza != null && !envBalanza.isBlank();
        String balanzaEndpoint = tieneEnvBalanza ? envBalanza.trim() : "http://localhost:8082";
        while (balanzaEndpoint.endsWith("/")) {
            balanzaEndpoint = balanzaEndpoint.substring(0, balanzaEndpoint.length() - 1);
        }
        return balanzaEndpoint.endsWith("/pesadas")
                ? balanzaEndpoint.substring(0, balanzaEndpoint.length() - "/pesadas".length())
                : balanzaEndpoint;
    }

    private String resolverTrackingBase() {
        String envTracking = System.getenv("TRACKING_ENDPOINT");
        boolean tieneEnvTracking = envTracking != null && !envTracking.isBlank();
        String trackingEndpoint = tieneEnvTracking ? envTracking.trim() : Config.valor("tracking.endpoint", "http://localhost:8085");
        while (trackingEndpoint.endsWith("/")) {
            trackingEndpoint = trackingEndpoint.substring(0, trackingEndpoint.length() - 1);
        }
        return trackingEndpoint.endsWith("/estado")
                ? trackingEndpoint.substring(0, trackingEndpoint.length() - "/estado".length())
                : trackingEndpoint;
    }

    private boolean esLocalOidc(String oidcEndpoint) {
        return oidcEndpoint.contains("localhost") || oidcEndpoint.contains("127.0.0.1") || oidcEndpoint.contains("cargauy-test");
    }

    private NodoPerifericoVista sondearBalanza() {
        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        boolean tieneEnvBalanza = envBalanza != null && !envBalanza.isBlank();
        String balanzaBase = resolverBalanzaBase();
        boolean balanzaUp = sondearHealthHttp(balanzaBase + "/health")
                || (!tieneEnvBalanza && sondearHealthHttp("http://localhost:8082/health"));
        return new NodoPerifericoVista(
                "NODO-BAL-01",
                "Nodo Periférico Balanzas",
                "HTTP",
                balanzaBase,
                balanzaUp,
                balanzaUp ? "Operativo" : "OFFLINE"
        );
    }

    private NodoPerifericoVista sondearTracking() {
        String envTracking = System.getenv("TRACKING_ENDPOINT");
        boolean tieneEnvTracking = envTracking != null && !envTracking.isBlank();
        String trackingBase = resolverTrackingBase();
        boolean trackingUp = sondearHealthHttp(trackingBase + "/health")
                || (!tieneEnvTracking && (sondearHealthHttp("http://localhost:8085/health") || sondearHealthHttp("http://mock-tracking:8080/health")));
        return new NodoPerifericoVista(
                "NODO-TRK-01",
                "Nodo Periférico Tracking",
                "HTTP",
                trackingBase,
                trackingUp,
                trackingUp ? "Operativo" : "OFFLINE"
        );
    }

    private NodoPerifericoVista sondearPdi() {
        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        boolean pdiUp = probarSocket("mock-pdi", 8080)
                || probarSocket("localhost", 8081)
                || sondearSocketDesdeUri(pdiEndpoint, 8081);
        return new NodoPerifericoVista(
                "NODO-PDI-01",
                "Servicio PDI (DNIC)",
                "SOAP",
                pdiEndpoint,
                pdiUp,
                pdiUp ? "Operativo" : "OFFLINE"
        );
    }

    private NodoPerifericoVista sondearLdap() {
        String ldapEndpoint = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        boolean ldapUp = probarSocket("ldap", 1389)
                || probarSocket("127.0.0.1", 1389)
                || sondearSocketDesdeUri(ldapEndpoint, 1389);
        return new NodoPerifericoVista(
                "NODO-LDAP-01",
                "Directorio OpenLDAP",
                "LDAP",
                ldapEndpoint,
                ldapUp,
                ldapUp ? "Operativo" : "OFFLINE"
        );
    }

    private NodoPerifericoVista sondearOidc() {
        String oidcEndpoint = Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
        boolean esLocal = esLocalOidc(oidcEndpoint);
        boolean oidcUp;
        String oidcNombre;
        String oidcDisplay = oidcEndpoint;

        if (esLocal) {
            oidcUp = probarSocket("gub", 8080)
                    || probarSocket("localhost", 8083)
                    || sondearSocketDesdeUri(oidcEndpoint, 8083);
            oidcNombre = "Login gub.uy (Keycloak local)";
            if (oidcUp) {
                oidcDisplay = "http://localhost:8083/realms/cargauy-test";
            }
        } else {
            oidcNombre = "Login gub.uy (ID Uruguay)";
            oidcUp = sondearSocketDesdeUri(oidcEndpoint, 443);
            if (!oidcUp) {
                try {
                    URI u = URI.create(oidcEndpoint);
                    oidcUp = probarSocket(u.getHost(), 443, 1500);
                } catch (Exception ignored) {
                    oidcUp = false;
                }
            }
        }

        return new NodoPerifericoVista(
                "NODO-OIDC-01",
                oidcNombre,
                "OIDC",
                oidcDisplay,
                oidcUp,
                oidcUp ? "Operativo" : "OFFLINE"
        );
    }

    private NodoPerifericoVista sondearMongo() {
        boolean mongoUp = (posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo())
                || probarSocket("mongodb", 27017)
                || probarSocket("localhost", 27017);

        String mongoEndpoint = posicionNoSqlRepository != null
                ? posicionNoSqlRepository.obtenerDescriptorEndpoint()
                : "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";
        long totalEventosMongo = (posicionNoSqlRepository != null) ? posicionNoSqlRepository.contarTotalEventos() : 0L;

        return new NodoPerifericoVista(
                "NOSQL-MONGO-01",
                "Base NoSQL (MongoDB)",
                "MongoDB",
                mongoEndpoint,
                mongoUp,
                mongoUp ? "Operativo (" + totalEventosMongo + " eventos)" : "OFFLINE"
        );
    }

    private boolean sondearHealthHttp(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(HTTP_PROBE_TIMEOUT)
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .GET()
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean sondearSocketDesdeUri(String uriStr, int defaultPort) {
        try {
            URI uri = URI.create(uriStr);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            int port = uri.getPort();
            if (port <= 0) {
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    port = 443;
                } else if ("http".equalsIgnoreCase(uri.getScheme())) {
                    port = 80;
                } else {
                    port = defaultPort;
                }
            }
            if (probarSocket(host, port)) {
                return true;
            }
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
                if (port == 8083 || uriStr.contains("realms") || uriStr.contains("gub")) {
                    if (probarSocket("gub", 8080)) return true;
                }
                if (port == 1389) {
                    if (probarSocket("ldap", 1389)) return true;
                }
                if (port == 8081 || uriStr.contains("pdi")) {
                    if (probarSocket("mock-pdi", 8080)) return true;
                }
                if (port == 8082 || uriStr.contains("balanza")) {
                    if (probarSocket("mock-balanza", 8080)) return true;
                }
                if (port == 8085 || uriStr.contains("tracking")) {
                    if (probarSocket("mock-tracking", 8080)) return true;
                }
                if (port == 27017) {
                    if (probarSocket("mongodb", 27017)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean probarSocket(String host, int port) {
        return probarSocket(host, port, (int) PROBE_TIMEOUT.toMillis());
    }

    private boolean probarSocket(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void sondearNodosInicial() {
        verificarNodos();
    }

    public void sondarNodos() {
        verificarNodos();
        mostrarMensaje(FacesMessage.SEVERITY_INFO, "Sondeo Completado", "Se actualizó el estado de salud de todos los nodos periféricos.");
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<NodoPerifericoVista> getNodos() {
        return nodos;
    }
}
