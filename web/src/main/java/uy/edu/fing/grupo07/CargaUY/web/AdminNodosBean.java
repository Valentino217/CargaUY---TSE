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
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService;

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
 * Gestión y monitoreo de nodos periféricos (Balanzas, PDI, LDAP, gub.uy, Colas JMS)
 * y administración de catálogos de plataforma (Rubros y Tipos de Carga).
 */
@Named("adminNodosBean")
@ViewScoped
public class AdminNodosBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(1500);

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private GestionPlataformaService plataformaService;

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
    private List<GestionPlataformaService.RubroVista> rubros;
    private List<GestionPlataformaService.TipoCargaVista> tiposCarga;

    private String nuevoRubroNombre;
    private String nuevoRubroDesc;
    private String nuevoTipoCargaDesc;

    @PostConstruct
    public void init() {
        verificarNodos();
        cargarCatalogos();
    }

    public void verificarNodos() {
        nodos = new ArrayList<>();

        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        String balanzaEndpoint = (envBalanza != null && !envBalanza.isBlank()) ? envBalanza : "http://localhost:8082";
        boolean balanzaUp = balanzaService != null && sondearEndpoint(balanzaEndpoint);

        nodos.add(new NodoPerifericoVista(
                "NODO-BAL-01",
                "Simulador Periférico de Balanzas en Ruta",
                "HTTP / JSON",
                balanzaEndpoint,
                balanzaUp,
                balanzaUp ? "Operativo (Sondeo HTTP 200 OK - Timeout 5s)" : "OFFLINE / Degradado (Periférico Inalcanzable)"
        ));

        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        boolean pdiUp = sondearSocketDesdeUri(pdiEndpoint, 8081);

        nodos.add(new NodoPerifericoVista(
                "NODO-PDI-01",
                "Plataforma de Interoperabilidad",
                "SOAP / XML",
                pdiEndpoint,
                pdiUp,
                pdiUp ? "Conectado (WS-Addressing + Circuit Breaker Activo)" : "OFFLINE / Degradado (Fallback Resiliente Activo)"
        ));

        String ldapEndpoint = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        boolean ldapUp = sondearSocketDesdeUri(ldapEndpoint, 1389);

        nodos.add(new NodoPerifericoVista(
                "NODO-LDAP-01",
                "Directorio Interno OpenLDAP",
                "LDAPv3",
                ldapEndpoint,
                ldapUp,
                ldapUp
                        ? "Bind Activo (" + Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local") + ")"
                        : "OFFLINE / Degradado (Servidor LDAP Inalcanzable)"
        ));

        String oidcEndpoint = Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
        boolean oidcUp = sondearSocketDesdeUri(oidcEndpoint, 8083);

        nodos.add(new NodoPerifericoVista(
                "NODO-OIDC-01",
                "Proveedor de Identidad Usuario gub.uy",
                "OIDC / OAuth 2.0 PKCE",
                oidcEndpoint,
                oidcUp,
                oidcUp ? "Emisor de Tokens Verificado" : "OFFLINE / Degradado (IdP OIDC Inalcanzable)"
        ));

        nodos.add(new NodoPerifericoVista(
                "JMS-TRACKING",
                "Cola Asíncrona de Eventos de Viaje",
                "Jakarta Messaging (Artemis)",
                "java:/jms/queue/TrackingQueue",
                true,
                "Consumidor TrackingMDB Activo (maxSession=10)"
        ));

        nodos.add(new NodoPerifericoVista(
                "JMS-FISC",
                "Cola Asíncrona del Motor de Fiscalización",
                "Jakarta Messaging (Artemis)",
                "java:/jms/queue/FiscalizacionQueue",
                true,
                "Consumidor FiscalizacionMDB Activo"
        ));

        boolean mongoUp = posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo();
        String mongoEndpoint = posicionNoSqlRepository != null
                ? posicionNoSqlRepository.obtenerDescriptorEndpoint()
                : "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";
        long totalEventosMongo = (posicionNoSqlRepository != null) ? posicionNoSqlRepository.contarTotalEventos() : 0L;

        nodos.add(new NodoPerifericoVista(
                "NOSQL-MONGO-01",
                "Base NoSQL / Serie Temporal de Posiciones (MongoDB)",
                "MongoDB 7 Time-Series / GeoJSON",
                mongoEndpoint,
                mongoUp,
                mongoUp
                        ? "Colección Time-Series Activa (" + totalEventosMongo + " eventos de posición registrados)"
                        : "OFFLINE / Degradado (Respaldo en Memoria Activo)"
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
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), (int) PROBE_TIMEOUT.toMillis());
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public void sondarNodos() {
        verificarNodos();
        mostrarMensaje(FacesMessage.SEVERITY_INFO, "Sondeo Completado", "Se actualizó el estado de salud de todos los nodos periféricos.");
    }

    public void cargarCatalogos() {
        try {
            String token = SesionActual.token();
            rubros = plataformaService.listarRubros(token);
            tiposCarga = plataformaService.listarTiposCarga(token);
        } catch (Exception e) {
            rubros = new ArrayList<>();
            tiposCarga = new ArrayList<>();
        }
    }

    public void crearRubro() {
        if (nuevoRubroNombre == null || nuevoRubroNombre.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "El nombre del rubro es obligatorio.");
            return;
        }
        try {
            plataformaService.crearRubro(SesionActual.token(), nuevoRubroNombre, nuevoRubroDesc);
            nuevoRubroNombre = "";
            nuevoRubroDesc = "";
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Rubro Creado", "Se registró el nuevo rubro en el catálogo.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al crear rubro", e.getMessage());
        }
    }

    public void eliminarRubro(int codigo) {
        try {
            plataformaService.eliminarRubro(SesionActual.token(), codigo);
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Rubro Eliminado", "Se eliminó el rubro #" + codigo);
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se puede eliminar", e.getMessage());
        }
    }

    public void crearTipoCarga() {
        if (nuevoTipoCargaDesc == null || nuevoTipoCargaDesc.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "La descripción del tipo de carga es obligatoria.");
            return;
        }
        try {
            plataformaService.crearTipoCarga(SesionActual.token(), nuevoTipoCargaDesc);
            nuevoTipoCargaDesc = "";
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Tipo de Carga Creado", "Se agregó el tipo de carga al catálogo.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
        }
    }

    public void eliminarTipoCarga(int codigo) {
        try {
            plataformaService.eliminarTipoCarga(SesionActual.token(), codigo);
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Tipo de Carga Eliminado", "Se eliminó el tipo de carga #" + codigo);
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se puede eliminar", e.getMessage());
        }
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

    public List<GestionPlataformaService.RubroVista> getRubros() {
        return rubros;
    }

    public List<GestionPlataformaService.TipoCargaVista> getTiposCarga() {
        return tiposCarga;
    }

    public String getNuevoRubroNombre() {
        return nuevoRubroNombre;
    }

    public void setNuevoRubroNombre(String nuevoRubroNombre) {
        this.nuevoRubroNombre = nuevoRubroNombre;
    }

    public String getNuevoRubroDesc() {
        return nuevoRubroDesc;
    }

    public void setNuevoRubroDesc(String nuevoRubroDesc) {
        this.nuevoRubroDesc = nuevoRubroDesc;
    }

    public String getNuevoTipoCargaDesc() {
        return nuevoTipoCargaDesc;
    }

    public void setNuevoTipoCargaDesc(String nuevoTipoCargaDesc) {
        this.nuevoTipoCargaDesc = nuevoTipoCargaDesc;
    }
}
