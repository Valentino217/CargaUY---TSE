package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.Config;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionNodosPerifericosServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.TrackingMockServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJBLocal;

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
import java.util.Set;
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
    private static final String ESTADO_APAGADO = "APAGADO";
    private static final Set<String> CODIGOS_BASE_SISTEMA = Set.of("NODO-BAL-01", "NODO-BAL-02", "NODO-TRK-01");
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(350);
    private static final Duration HTTP_PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(HTTP_PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private GestionNodosPerifericosServiceLocal nodosPerifericosService;

    @EJB
    private TrackingMockServiceLocal trackingMockService;

    @EJB
    private VehiculoEJBLocal vehiculoEJB;

    @EJB
    private PosicionNoSqlRepositoryLocal posicionNoSqlRepository;

    public record NodoPerifericoVista(
            String codigo,
            String nombre,
            String tipo,
            String endpoint,
            boolean online,
            String detalleEstado,
            boolean administrable,
            boolean habilitado,
            boolean esBalanza,
            boolean esTracking,
            String modo,
            String etiquetaModo,
            boolean personalizado,
            String estacionPeaje
    ) implements Serializable {
        public NodoPerifericoVista(
                String codigo,
                String nombre,
                String tipo,
                String endpoint,
                boolean online,
                String detalleEstado
        ) {
            this(codigo, nombre, tipo, endpoint, online, detalleEstado, false, true, false, false, null, "N/A", false, null);
        }

        public String estado() {
            if (ESTADO_CONSULTANDO.equals(detalleEstado)) {
                return ESTADO_CONSULTANDO;
            }
            if (administrable && !habilitado) {
                return ESTADO_APAGADO;
            }
            return online ? "ONLINE" : "OFFLINE";
        }

        public boolean isConsultando() {
            return ESTADO_CONSULTANDO.equals(detalleEstado);
        }

        public boolean isApagadoAdmin() {
            return administrable && !habilitado;
        }

        public boolean isAdministrable() {
            return administrable;
        }

        public boolean isHabilitado() {
            return habilitado;
        }

        public boolean isEsBalanza() {
            return esBalanza;
        }

        public boolean isEsTracking() {
            return esTracking;
        }

        public boolean isPersonalizado() {
            return personalizado;
        }
    }

    private List<NodoPerifericoVista> nodos;

    // Formulario modal: Agregar Balanza
    private String nuevaBalanzaCodigo;
    private String nuevaBalanzaNombre;
    private String nuevaBalanzaPeaje;
    private String nuevaBalanzaEndpoint;
    private String nuevaBalanzaModo = ModoNodoPeriferico.CALIBRADA.name();
    private boolean nuevaBalanzaHabilitada = true;

    // Formulario modal: Simular paso por balanza
    private String codigoNodoPaso;
    private String nombreNodoPaso;
    private String matriculaPaso;
    private String matriculaPasoPersonalizada;
    private Integer pesoKgPaso = 32000;
    private List<Vehiculo> vehiculosDisponibles = List.of();

    void setBalanzaService(GestionBalanzaServiceLocal balanzaService) {
        this.balanzaService = balanzaService;
    }

    void setNodosPerifericosService(GestionNodosPerifericosServiceLocal nodosPerifericosService) {
        this.nodosPerifericosService = nodosPerifericosService;
    }

    void setTrackingMockService(TrackingMockServiceLocal trackingMockService) {
        this.trackingMockService = trackingMockService;
    }

    void setVehiculoEJB(VehiculoEJBLocal vehiculoEJB) {
        this.vehiculoEJB = vehiculoEJB;
    }

    void setPosicionNoSqlRepository(PosicionNoSqlRepositoryLocal posicionNoSqlRepository) {
        this.posicionNoSqlRepository = posicionNoSqlRepository;
    }

    @PostConstruct
    public void init() {
        cargarEsqueletoNodos();
    }

    /**
     * Construye la tabla de nodos al instante sin realizar I/O de red, dejando cada nodo habilitado en
     * estado CONSULTANDO hasta que el sondeo asíncrono por AJAX complete la verificación, y los nodos
     * apagados administrativamente directamente en APAGADO.
     */
    public void cargarEsqueletoNodos() {
        List<NodoPerifericoVista> lista = new ArrayList<>();

        if (nodosPerifericosService != null) {
            for (NodoPerifericoConfig cfg : nodosPerifericosService.listarNodos()) {
                String detalleInicial = cfg.habilitado() ? ESTADO_CONSULTANDO : "Desconectado administrativamente";
                lista.add(construirVistaDesdeConfig(cfg, false, detalleInicial));
            }
        } else {
            lista.add(new NodoPerifericoVista(
                    "NODO-BAL-01", "Balanza #1", "HTTP", resolverBalanzaBase(), false, ESTADO_CONSULTANDO,
                    true, true, true, false, ModoNodoPeriferico.CALIBRADA.name(), ModoNodoPeriferico.CALIBRADA.getEtiqueta(), false, ""
            ));
            lista.add(new NodoPerifericoVista(
                    "NODO-TRK-01", "Tracking GPS", "HTTP", resolverTrackingBase(), false, ESTADO_CONSULTANDO,
                    true, true, false, true, ModoNodoPeriferico.RUTA_NORMAL.name(), ModoNodoPeriferico.RUTA_NORMAL.getEtiqueta(), false, null
            ));
        }

        lista.addAll(construirEsqueletoInfraestructura());
        nodos = List.copyOf(lista);
    }

    private List<NodoPerifericoVista> construirEsqueletoInfraestructura() {
        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        String ldapEndpoint = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        String oidcEndpoint = Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
        String oidcNombre = esLocalOidc(oidcEndpoint) ? "Keycloak" : "ID Uruguay";
        String mongoEndpoint = posicionNoSqlRepository != null
                ? posicionNoSqlRepository.obtenerDescriptorEndpoint()
                : "mongodb://localhost:27017/cargauy_tracking.eventos_posicion";

        return List.of(
                new NodoPerifericoVista("NODO-PDI-01", "PDI (DNIC)", "SOAP", pdiEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-LDAP-01", "OpenLDAP", "LDAP", ldapEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-OIDC-01", oidcNombre, "OIDC", oidcEndpoint, false, ESTADO_CONSULTANDO),
                new NodoPerifericoVista("NODO-MONGO-01", "MongoDB", "MongoDB", mongoEndpoint, false, ESTADO_CONSULTANDO)
        );
    }

    public void verificarNodos() {
        List<CompletableFuture<NodoPerifericoVista>> futuros = new ArrayList<>();

        if (nodosPerifericosService != null) {
            for (NodoPerifericoConfig cfg : nodosPerifericosService.listarNodos()) {
                futuros.add(CompletableFuture.supplyAsync(() -> sondearNodoConfigurado(cfg)));
            }
        } else {
            futuros.add(CompletableFuture.supplyAsync(this::sondearBalanza));
            futuros.add(CompletableFuture.supplyAsync(this::sondearTracking));
        }

        futuros.add(CompletableFuture.supplyAsync(this::sondearPdi));
        futuros.add(CompletableFuture.supplyAsync(this::sondearLdap));
        futuros.add(CompletableFuture.supplyAsync(this::sondearOidc));
        futuros.add(CompletableFuture.supplyAsync(this::sondearMongo));

        CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0])).join();
        List<NodoPerifericoVista> resultado = new ArrayList<>(futuros.size());
        for (CompletableFuture<NodoPerifericoVista> f : futuros) {
            resultado.add(f.join());
        }
        nodos = List.copyOf(resultado);
    }

    private NodoPerifericoVista sondearNodoConfigurado(NodoPerifericoConfig cfg) {
        if (!cfg.habilitado()) {
            return construirVistaDesdeConfig(cfg, false, "Desconectado por el Administrador");
        }
        String fallback = cfg.esBalanza() ? resolverBalanzaBase() : resolverTrackingBase();
        String base = limpiarBaseUrl(cfg.endpointUrl(), fallback);
        boolean online = sondearHealthHttp(base + "/health");
        if (!online) {
            if (cfg.esBalanza() && isBlank(System.getenv("BALANZA_ENDPOINT"))) {
                online = sondearHealthHttp("http://localhost:8082/health") || sondearHealthHttp("http://mock-balanza:8080/health");
            } else if (cfg.esTracking() && isBlank(System.getenv("TRACKING_ENDPOINT"))) {
                online = sondearHealthHttp("http://localhost:8085/health") || sondearHealthHttp("http://mock-tracking:8080/health");
            }
        }
        return construirVistaDesdeConfig(cfg, online, online ? "Operativo" : "OFFLINE");
    }

    private NodoPerifericoVista construirVistaDesdeConfig(NodoPerifericoConfig cfg, boolean online, String detalleEstado) {
        ModoNodoPeriferico modo = cfg.modoOperacion();
        return new NodoPerifericoVista(
                cfg.codigo(),
                cfg.nombre(),
                "HTTP",
                cfg.endpointUrl(),
                online,
                detalleEstado,
                true,
                cfg.habilitado(),
                cfg.esBalanza(),
                cfg.esTracking(),
                modo != null ? modo.name() : null,
                modo != null ? modo.getEtiqueta() : "-",
                !CODIGOS_BASE_SISTEMA.contains(cfg.codigo()),
                cfg.estacionPeaje()
        );
    }

    private static boolean isBlank(String val) {
        return val == null || val.isBlank();
    }

    private String resolverBalanzaBase() {
        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        return limpiarBaseUrl(envBalanza, "http://localhost:8082");
    }

    private String resolverTrackingBase() {
        String envTracking = System.getenv("TRACKING_ENDPOINT");
        String configured = !isBlank(envTracking) ? envTracking : Config.valor("tracking.endpoint", "http://localhost:8085");
        return limpiarBaseUrl(configured, "http://localhost:8085");
    }

    private static String limpiarBaseUrl(String raw, String fallback) {
        String base = !isBlank(raw) ? raw.trim() : fallback;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/pesadas")) {
            base = base.substring(0, base.length() - "/pesadas".length());
        } else if (base.endsWith("/estado")) {
            base = base.substring(0, base.length() - "/estado".length());
        }
        return base;
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
                "Balanza Rutas Nacionales",
                "HTTP",
                balanzaBase,
                balanzaUp,
                balanzaUp ? "Operativo" : "OFFLINE",
                true,
                true,
                true,
                false,
                ModoNodoPeriferico.CALIBRADA.name(),
                ModoNodoPeriferico.CALIBRADA.getEtiqueta(),
                false,
                "Peaje Manuel Díaz"
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
                "Tracking GPS",
                "HTTP",
                trackingBase,
                trackingUp,
                trackingUp ? "Operativo" : "OFFLINE",
                true,
                true,
                false,
                true,
                ModoNodoPeriferico.RUTA_NORMAL.name(),
                ModoNodoPeriferico.RUTA_NORMAL.getEtiqueta(),
                false,
                null
        );
    }

    private NodoPerifericoVista sondearPdi() {
        String pdiEndpoint = Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws");
        boolean pdiUp = probarSocket("mock-pdi", 8080)
                || probarSocket("localhost", 8081)
                || sondearSocketDesdeUri(pdiEndpoint, 8081);
        return new NodoPerifericoVista(
                "NODO-PDI-01",
                "PDI (DNIC)",
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
                "OpenLDAP",
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
            oidcNombre = "Keycloak";
            if (oidcUp) {
                oidcDisplay = "http://localhost:8083/realms/cargauy-test";
            }
        } else {
            oidcNombre = "ID Uruguay";
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
                "NODO-MONGO-01",
                "MongoDB",
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

    public void toggleHabilitado(String codigo) {
        if (nodosPerifericosService == null || isBlank(codigo)) {
            return;
        }
        try {
            NodoPerifericoConfig actual = nodosPerifericosService.buscarPorCodigo(codigo).orElse(null);
            if (actual == null) {
                mostrarMensaje(FacesMessage.SEVERITY_WARN, "Nodo no encontrado", "No existe el nodo " + codigo + ".");
                return;
            }
            boolean nuevoEstado = !actual.habilitado();
            nodosPerifericosService.setHabilitado(SesionActual.token(), codigo, nuevoEstado);
            notificarCambioSegunTipo(actual.esBalanza());
            verificarNodos();
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    nuevoEstado ? "Nodo Encendido" : "Nodo Apagado",
                    actual.nombre() + " (" + codigo + ") ahora está " + (nuevoEstado ? "ENCENDIDO." : "APAGADO.")
            );
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se pudo cambiar el estado", e.getMessage());
        }
    }

    public void cambiarModoNodo(String codigo, String modoStr) {
        if (nodosPerifericosService == null || isBlank(codigo) || isBlank(modoStr)) {
            return;
        }
        try {
            ModoNodoPeriferico nuevoModo = ModoNodoPeriferico.valueOf(modoStr.trim());
            nodosPerifericosService.cambiarModo(SesionActual.token(), codigo, nuevoModo);
            notificarCambioSegunTipo(
                    nodosPerifericosService.buscarPorCodigo(codigo).map(NodoPerifericoConfig::esBalanza).orElse(false)
            );
            verificarNodos();
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Modo Operativo Actualizado",
                    codigo + " configurado en modo: " + nuevoModo.getEtiqueta()
            );
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al cambiar modo", e.getMessage());
        }
    }

    public void abrirDialogoNuevaBalanza() {
        this.nuevaBalanzaCodigo = sugerirCodigoBalanza();
        this.nuevaBalanzaNombre = "";
        this.nuevaBalanzaPeaje = "";
        this.nuevaBalanzaEndpoint = resolverBalanzaBase();
        this.nuevaBalanzaModo = ModoNodoPeriferico.CALIBRADA.name();
        this.nuevaBalanzaHabilitada = true;
    }

    private String sugerirCodigoBalanza() {
        int seq = 3;
        while (nodosPerifericosService != null
                && nodosPerifericosService.buscarPorCodigo(String.format("NODO-BAL-%02d", seq)).isPresent()) {
            seq++;
        }
        return String.format("NODO-BAL-%02d", seq);
    }

    public void agregarNuevaBalanza() {
        if (nodosPerifericosService == null) {
            return;
        }
        try {
            ModoNodoPeriferico modo = isBlank(nuevaBalanzaModo)
                    ? ModoNodoPeriferico.CALIBRADA
                    : ModoNodoPeriferico.valueOf(nuevaBalanzaModo.trim());
            NodoPerifericoConfig creada = nodosPerifericosService.agregarBalanza(
                    SesionActual.token(),
                    nuevaBalanzaCodigo,
                    nuevaBalanzaNombre,
                    nuevaBalanzaPeaje,
                    nuevaBalanzaEndpoint,
                    modo,
                    nuevaBalanzaHabilitada
            );
            notificarCambioSegunTipo(true);
            verificarNodos();
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Balanza Agregada",
                    "Se registró el nodo " + creada.codigo() + " (" + creada.estacionPeaje() + ")."
            );
        } catch (Exception e) {
            marcarErrorValidacion();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se pudo agregar la balanza", e.getMessage());
        }
    }

    public void eliminarBalanza(String codigo) {
        if (nodosPerifericosService == null || isBlank(codigo)) {
            return;
        }
        try {
            boolean eliminada = nodosPerifericosService.eliminarBalanzaPersonalizada(SesionActual.token(), codigo);
            verificarNodos();
            if (eliminada) {
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Balanza Eliminada", "Se quitó el nodo " + codigo + " del registro.");
            }
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se pudo eliminar", e.getMessage());
        }
    }

    public void abrirDialogoPasoBalanza(String codigo) {
        this.codigoNodoPaso = codigo;
        this.nombreNodoPaso = codigo;
        this.matriculaPasoPersonalizada = "";
        this.pesoKgPaso = 32000;
        if (nodosPerifericosService != null && !isBlank(codigo)) {
            nodosPerifericosService.buscarPorCodigo(codigo).ifPresent(cfg -> {
                this.nombreNodoPaso = !isBlank(cfg.estacionPeaje())
                        ? cfg.nombre() + " (" + cfg.estacionPeaje() + ")"
                        : cfg.nombre();
                if (cfg.modoOperacion() == ModoNodoPeriferico.SOBRECARGA) {
                    this.pesoKgPaso = 45000;
                }
            });
        }
        if (vehiculoEJB != null) {
            List<Vehiculo> lista = vehiculoEJB.listarParaFiscalizacion(SesionActual.token());
            this.vehiculosDisponibles = lista != null ? lista : List.of();
            if (!this.vehiculosDisponibles.isEmpty()) {
                this.matriculaPaso = this.vehiculosDisponibles.get(0).getMatricula();
            }
        } else {
            this.vehiculosDisponibles = List.of();
            this.matriculaPaso = "";
        }
    }

    public void registrarPasoSimuladoBalanza() {
        if (balanzaService == null) {
            return;
        }
        String matriculaEfectiva = !isBlank(matriculaPasoPersonalizada) ? matriculaPasoPersonalizada.trim() : matriculaPaso;
        if (isBlank(matriculaEfectiva)) {
            marcarErrorValidacion();
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Matrícula requerida", "Seleccioná o ingresá la matrícula del vehículo.");
            return;
        }
        if (pesoKgPaso == null || pesoKgPaso <= 0) {
            marcarErrorValidacion();
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Peso inválido", "Ingresá un peso positivo en kilogramos.");
            return;
        }
        try {
            PesadaBalanzaDTO pesada = balanzaService.registrarLecturaExternaEnNodo(
                    SesionActual.token(), codigoNodoPaso, matriculaEfectiva, pesoKgPaso);
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Lectura Emitida en Balanza",
                    String.format("%s registrada con %d kg (%s).",
                            pesada.matricula(), pesada.pesoRegistrado(),
                            codigoNodoPaso != null ? codigoNodoPaso : "Balanza")
            );
        } catch (Exception e) {
            marcarErrorValidacion();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error en lectura de balanza", e.getMessage());
        }
    }

    private void notificarCambioSegunTipo(boolean esBalanza) {
        if (trackingMockService == null) {
            return;
        }
        String token = SesionActual.token();
        if (esBalanza) {
            trackingMockService.notificarCambioBalanza(token);
        } else {
            trackingMockService.notificarCambioConfiguracionNodos(token);
        }
    }

    private void marcarErrorValidacion() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.validationFailed();
            }
        } catch (Throwable ignored) {
        }
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.addMessage(null, new FacesMessage(sev, res, det));
            }
        } catch (Throwable ignored) {
        }
    }

    public List<NodoPerifericoVista> getNodos() {
        return nodos;
    }

    public String getNuevaBalanzaCodigo() {
        return nuevaBalanzaCodigo;
    }

    public void setNuevaBalanzaCodigo(String nuevaBalanzaCodigo) {
        this.nuevaBalanzaCodigo = nuevaBalanzaCodigo;
    }

    public String getNuevaBalanzaNombre() {
        return nuevaBalanzaNombre;
    }

    public void setNuevaBalanzaNombre(String nuevaBalanzaNombre) {
        this.nuevaBalanzaNombre = nuevaBalanzaNombre;
    }

    public String getNuevaBalanzaPeaje() {
        return nuevaBalanzaPeaje;
    }

    public void setNuevaBalanzaPeaje(String nuevaBalanzaPeaje) {
        this.nuevaBalanzaPeaje = nuevaBalanzaPeaje;
    }

    public String getNuevaBalanzaEndpoint() {
        return nuevaBalanzaEndpoint;
    }

    public void setNuevaBalanzaEndpoint(String nuevaBalanzaEndpoint) {
        this.nuevaBalanzaEndpoint = nuevaBalanzaEndpoint;
    }

    public String getNuevaBalanzaModo() {
        return nuevaBalanzaModo;
    }

    public void setNuevaBalanzaModo(String nuevaBalanzaModo) {
        this.nuevaBalanzaModo = nuevaBalanzaModo;
    }

    public boolean isNuevaBalanzaHabilitada() {
        return nuevaBalanzaHabilitada;
    }

    public void setNuevaBalanzaHabilitada(boolean nuevaBalanzaHabilitada) {
        this.nuevaBalanzaHabilitada = nuevaBalanzaHabilitada;
    }

    public String getCodigoNodoPaso() {
        return codigoNodoPaso;
    }

    public void setCodigoNodoPaso(String codigoNodoPaso) {
        this.codigoNodoPaso = codigoNodoPaso;
    }

    public String getNombreNodoPaso() {
        return nombreNodoPaso;
    }

    public String getMatriculaPaso() {
        return matriculaPaso;
    }

    public void setMatriculaPaso(String matriculaPaso) {
        this.matriculaPaso = matriculaPaso;
    }

    public String getMatriculaPasoPersonalizada() {
        return matriculaPasoPersonalizada;
    }

    public void setMatriculaPasoPersonalizada(String matriculaPasoPersonalizada) {
        this.matriculaPasoPersonalizada = matriculaPasoPersonalizada;
    }

    public Integer getPesoKgPaso() {
        return pesoKgPaso;
    }

    public void setPesoKgPaso(Integer pesoKgPaso) {
        this.pesoKgPaso = pesoKgPaso;
    }

    public List<Vehiculo> getVehiculosDisponibles() {
        return vehiculosDisponibles;
    }
}
