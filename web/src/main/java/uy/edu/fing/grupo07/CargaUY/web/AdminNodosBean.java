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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Managed Bean para /admin/nodos.xhtml.
 * Gestión y monitoreo en tiempo real de nodos periféricos (Balanzas, PDI, LDAP, gub.uy, Colas JMS, NoSQL).
 */
@Named("adminNodosBean")
@ViewScoped
public class AdminNodosBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(350);

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
    ) implements Serializable {}

    private List<NodoPerifericoVista> nodos;

    @PostConstruct
    public void init() {
        verificarNodos();
    }

    public void verificarNodos() {
        nodos = new ArrayList<>();

        // 1. Balanza
        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        String balanzaEndpoint = (envBalanza != null && !envBalanza.isBlank()) ? envBalanza : "http://localhost:8082";
        boolean balanzaUp = probarSocket("mock-balanza", 8080)
                || probarSocket("localhost", 8082)
                || (balanzaService != null && sondearEndpoint(balanzaEndpoint));

        nodos.add(new NodoPerifericoVista(
                "NODO-BAL-01",
                "Simulador Balanza",
                "HTTP",
                balanzaEndpoint,
                balanzaUp,
                balanzaUp ? "Operativo" : "OFFLINE"
        ));

        // 2. PDI (DNIC)
        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        boolean pdiUp = probarSocket("mock-pdi", 8080)
                || probarSocket("localhost", 8081)
                || sondearSocketDesdeUri(pdiEndpoint, 8081);

        nodos.add(new NodoPerifericoVista(
                "NODO-PDI-01",
                "Servicio PDI (DNIC)",
                "SOAP",
                pdiEndpoint,
                pdiUp,
                pdiUp ? "Operativo" : "OFFLINE"
        ));

        // 3. LDAP
        String ldapEndpoint = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        boolean ldapUp = probarSocket("ldap", 1389)
                || probarSocket("127.0.0.1", 1389)
                || sondearSocketDesdeUri(ldapEndpoint, 1389);

        nodos.add(new NodoPerifericoVista(
                "NODO-LDAP-01",
                "Directorio OpenLDAP",
                "LDAP",
                ldapEndpoint,
                ldapUp,
                ldapUp ? "Operativo" : "OFFLINE"
        ));

        // 4. gub.uy (ID Uruguay oficial o Keycloak mock opcional)
        String oidcEndpoint = Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
        boolean esLocalOidc = oidcEndpoint.contains("localhost") || oidcEndpoint.contains("127.0.0.1") || oidcEndpoint.contains("cargauy-test");
        
        boolean oidcUp;
        String oidcNombre;
        String oidcDisplay = oidcEndpoint;

        if (esLocalOidc) {
            oidcUp = probarSocket("gub", 8080)
                    || probarSocket("localhost", 8083)
                    || sondearSocketDesdeUri(oidcEndpoint, 8083);
            oidcNombre = "Login gub.uy (Keycloak local)";
            if (oidcUp) {
                oidcDisplay = "http://localhost:8083/realms/cargauy-test";
            }
        } else {
            // Entorno oficial gub.uy / AGESIC (ID Uruguay)
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

        nodos.add(new NodoPerifericoVista(
                "NODO-OIDC-01",
                oidcNombre,
                "OIDC",
                oidcDisplay,
                oidcUp,
                oidcUp ? "Operativo" : "OFFLINE"
        ));

        // 5. MongoDB NoSQL
        boolean mongoUp = (posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo())
                || probarSocket("mongodb", 27017)
                || probarSocket("localhost", 27017);

        String mongoEndpoint = posicionNoSqlRepository != null
                ? posicionNoSqlRepository.obtenerDescriptorEndpoint()
                : "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";
        long totalEventosMongo = (posicionNoSqlRepository != null) ? posicionNoSqlRepository.contarTotalEventos() : 0L;

        nodos.add(new NodoPerifericoVista(
                "NOSQL-MONGO-01",
                "Base NoSQL (MongoDB)",
                "MongoDB",
                mongoEndpoint,
                mongoUp,
                mongoUp ? "Operativo (" + totalEventosMongo + " eventos)" : "OFFLINE"
        ));
    }

    private boolean sondearEndpoint(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(PROBE_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(PROBE_TIMEOUT)
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 500;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return sondearSocketDesdeUri(urlStr, 8082);
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
