package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.CategoriaNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro central en tiempo real de nodos periféricos administrables (Balanzas y Tracking GPS).
 * Permite al Administrador encender/apagar nodos, conmutar entre múltiples balanzas o cambiar sus modos
 * operativos sin requerir intervención del Funcionario ni despliegues adicionales.
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class GestionNodosPerifericosServiceImpl implements GestionNodosPerifericosServiceLocal {

    private static final Logger LOGGER = Logger.getLogger(GestionNodosPerifericosServiceImpl.class.getName());
    private static final Set<String> CODIGOS_PREDEFINIDOS = Set.of("NODO-BAL-01", "NODO-BAL-02", "NODO-TRK-01");

    private final Map<String, NodoPerifericoConfig> nodos = new LinkedHashMap<>();
    private final Set<String> autoPesadasEmitidas = new HashSet<>();
    private final Map<String, Integer> watermarkPorEndpoint = new HashMap<>();
    private final Set<String> endpointsWatermarkInicializados = new HashSet<>();
    private final AtomicLong versionConfiguracion = new AtomicLong(1L);

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    @EJB
    private ControlAcceso control;

    public GestionNodosPerifericosServiceImpl() {
        inicializarNodosPorDefecto();
    }

    public GestionNodosPerifericosServiceImpl(EntityManager em, ControlAcceso control) {
        this.em = em;
        this.control = control;
        inicializarNodosPorDefecto();
    }

    @PostConstruct
    public void init() {
        synchronized (this) {
            if (nodos.isEmpty()) {
                inicializarNodosPorDefecto();
            }
            cargarDesdeBaseDeDatos();
        }
    }

    private void cargarDesdeBaseDeDatos() {
        if (em == null) {
            return;
        }
        try {
            List<ConfiguracionNodoPeriferico> filas = em.createQuery(
                    "SELECT c FROM ConfiguracionNodoPeriferico c ORDER BY c.codigo ASC",
                    ConfiguracionNodoPeriferico.class
            ).getResultList();
            if (filas != null && !filas.isEmpty()) {
                Map<String, NodoPerifericoConfig> defaults = new LinkedHashMap<>(nodos);
                nodos.clear();
                watermarkPorEndpoint.clear();
                endpointsWatermarkInicializados.clear();
                for (ConfiguracionNodoPeriferico fila : filas) {
                    if (fila == null || fila.getCodigo() == null) {
                        continue;
                    }
                    if (fila.isHabilitado()) {
                        fila.setHabilitado(false);
                        em.merge(fila);
                    }
                    NodoPerifericoConfig dto = fila.toDto();
                    nodos.put(dto.codigo(), dto);
                    if (fila.isWatermarkInicializado() && dto.endpointUrl() != null) {
                        String endpointKey = normalizarBalanzaUrl(dto.endpointUrl());
                        endpointsWatermarkInicializados.add(endpointKey);
                        watermarkPorEndpoint.merge(endpointKey, fila.getWatermarkId(), Math::max);
                    }
                }
                for (Map.Entry<String, NodoPerifericoConfig> def : defaults.entrySet()) {
                    if (!nodos.containsKey(def.getKey())) {
                        nodos.put(def.getKey(), def.getValue());
                        em.persist(new ConfiguracionNodoPeriferico(def.getValue()));
                    }
                }
            } else {
                for (NodoPerifericoConfig cfg : nodos.values()) {
                    em.persist(new ConfiguracionNodoPeriferico(cfg));
                }
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No fue posible sincronizar configuración de nodos con PostgreSQL al iniciar", e);
        }
    }

    private synchronized void inicializarNodosPorDefecto() {
        nodos.clear();
        String balanzaBase = resolverBalanzaBase();
        String trackingBase = resolverTrackingBase();

        nodos.put("NODO-BAL-01", new NodoPerifericoConfig(
                "NODO-BAL-01",
                "Balanza #1",
                CategoriaNodoPeriferico.BALANZA,
                balanzaBase,
                "",
                false,
                ModoNodoPeriferico.CALIBRADA
        ));
        nodos.put("NODO-BAL-02", new NodoPerifericoConfig(
                "NODO-BAL-02",
                "Balanza #2",
                CategoriaNodoPeriferico.BALANZA,
                balanzaBase,
                "",
                false,
                ModoNodoPeriferico.SOBRECARGA
        ));
        nodos.put("NODO-TRK-01", new NodoPerifericoConfig(
                "NODO-TRK-01",
                "Tracking GPS",
                CategoriaNodoPeriferico.TRACKING,
                trackingBase,
                "Red Vial Nacional MTOP",
                false,
                ModoNodoPeriferico.RUTA_NORMAL
        ));
    }

    @Override
    public synchronized List<NodoPerifericoConfig> listarNodos() {
        return new ArrayList<>(nodos.values());
    }

    @Override
    public synchronized List<NodoPerifericoConfig> listarBalanzasHabilitadas() {
        List<NodoPerifericoConfig> resultado = new ArrayList<>();
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esBalanza() && cfg.habilitado()) {
                resultado.add(cfg);
            }
        }
        return resultado;
    }

    @Override
    public synchronized List<NodoPerifericoConfig> listarNodosTracking() {
        List<NodoPerifericoConfig> resultado = new ArrayList<>();
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esTracking()) {
                resultado.add(cfg);
            }
        }
        return resultado;
    }

    @Override
    public synchronized Optional<NodoPerifericoConfig> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(nodos.get(codigo.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public synchronized boolean isNodoHabilitado(String codigo) {
        return buscarPorCodigo(codigo).map(NodoPerifericoConfig::habilitado).orElse(false);
    }

    @Override
    public synchronized boolean isTrackingHabilitado() {
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esTracking() && cfg.habilitado()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isAlgunaBalanzaHabilitada() {
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esBalanza() && cfg.habilitado()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized ModoNodoPeriferico getModoTrackingActivo() {
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esTracking() && cfg.habilitado()) {
                return cfg.modoOperacion() != null ? cfg.modoOperacion() : ModoNodoPeriferico.RUTA_NORMAL;
            }
        }
        NodoPerifericoConfig trk = nodos.get("NODO-TRK-01");
        return trk != null && trk.modoOperacion() != null ? trk.modoOperacion() : ModoNodoPeriferico.RUTA_NORMAL;
    }

    @Override
    public synchronized String getEndpointTrackingActivo() {
        for (NodoPerifericoConfig cfg : nodos.values()) {
            if (cfg.esTracking() && cfg.habilitado()) {
                return cfg.endpointUrl();
            }
        }
        NodoPerifericoConfig trk = nodos.get("NODO-TRK-01");
        return trk != null ? trk.endpointUrl() : resolverTrackingBase();
    }

    @Override
    public synchronized void setHabilitado(String codigo, boolean habilitado) {
        String key = normalizarCodigoExistente(codigo);
        NodoPerifericoConfig actual = nodos.get(key);
        if (actual.habilitado() == habilitado) {
            return;
        }
        if (habilitado && actual.esBalanza()) {
            // Al volver a encender una balanza, permitir registrar una nueva pasada en ruta si hay viajes EN_CURSO
            String marker = "|" + key + "|";
            autoPesadasEmitidas.removeIf(k -> k.contains(marker));
        }
        NodoPerifericoConfig actualizado = new NodoPerifericoConfig(
                actual.codigo(),
                actual.nombre(),
                actual.categoria(),
                actual.endpointUrl(),
                actual.estacionPeaje(),
                habilitado,
                actual.modoOperacion()
        );
        nodos.put(key, actualizado);
        sincronizarNodoEnDb(actualizado);
        versionConfiguracion.incrementAndGet();
        LOGGER.log(Level.INFO, "Nodo periférico {0} cambiado a estado habilitado={1}.", new Object[]{key, habilitado});
    }

    @Override
    public void setHabilitado(String token, String codigo, boolean habilitado) {
        exigirAdmin(token);
        setHabilitado(codigo, habilitado);
    }

    @Override
    public synchronized void cambiarModo(String codigo, ModoNodoPeriferico nuevoModo) {
        if (nuevoModo == null) {
            throw new BusinessException("El modo operativo del nodo no puede ser nulo.");
        }
        String key = normalizarCodigoExistente(codigo);
        NodoPerifericoConfig actual = nodos.get(key);
        if (!nuevoModo.esCompatibleCon(actual.categoria())) {
            throw new BusinessException("El modo " + nuevoModo + " no corresponde a la categoría " + actual.categoria());
        }
        NodoPerifericoConfig actualizado = new NodoPerifericoConfig(
                actual.codigo(),
                actual.nombre(),
                actual.categoria(),
                actual.endpointUrl(),
                actual.estacionPeaje(),
                actual.habilitado(),
                nuevoModo
        );
        nodos.put(key, actualizado);
        sincronizarNodoEnDb(actualizado);
        versionConfiguracion.incrementAndGet();
        LOGGER.log(Level.INFO, "Nodo periférico {0} cambiado a modo operativo {1}.", new Object[]{key, nuevoModo});
    }

    @Override
    public void cambiarModo(String token, String codigo, ModoNodoPeriferico nuevoModo) {
        exigirAdmin(token);
        cambiarModo(codigo, nuevoModo);
    }

    @Override
    public synchronized void actualizarEndpoint(String codigo, String nuevoEndpoint) {
        if (nuevoEndpoint == null || nuevoEndpoint.isBlank()) {
            throw new BusinessException("La URL del endpoint no puede estar vacía.");
        }
        String key = normalizarCodigoExistente(codigo);
        NodoPerifericoConfig actual = nodos.get(key);
        String limpia = actual.esBalanza()
                ? normalizarBalanzaUrl(nuevoEndpoint)
                : normalizarTrackingUrl(nuevoEndpoint);
        NodoPerifericoConfig actualizado = new NodoPerifericoConfig(
                actual.codigo(),
                actual.nombre(),
                actual.categoria(),
                limpia,
                actual.estacionPeaje(),
                actual.habilitado(),
                actual.modoOperacion()
        );
        nodos.put(key, actualizado);
        sincronizarNodoEnDb(actualizado);
        versionConfiguracion.incrementAndGet();
    }

    @Override
    public void actualizarEndpoint(String token, String codigo, String nuevoEndpoint) {
        exigirAdmin(token);
        actualizarEndpoint(codigo, nuevoEndpoint);
    }

    @Override
    public synchronized NodoPerifericoConfig agregarBalanza(
            String codigo,
            String nombre,
            String estacionPeaje,
            String endpointUrl,
            ModoNodoPeriferico modo,
            boolean habilitado
    ) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("El código de la balanza es obligatorio (ej: NODO-BAL-03).");
        }
        String key = codigo.trim().toUpperCase(Locale.ROOT);
        if (nodos.containsKey(key)) {
            throw new BusinessException("Ya existe un nodo registrado con el código " + key + ".");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new BusinessException("El nombre descriptivo de la balanza es obligatorio.");
        }
        String peaje = (estacionPeaje != null && !estacionPeaje.isBlank()) ? estacionPeaje.trim() : nombre.trim();
        String endpoint = (endpointUrl != null && !endpointUrl.isBlank())
                ? normalizarBalanzaUrl(endpointUrl)
                : resolverBalanzaBase();
        ModoNodoPeriferico modoEfectivo = (modo != null && modo.esCompatibleCon(CategoriaNodoPeriferico.BALANZA))
                ? modo
                : ModoNodoPeriferico.CALIBRADA;

        NodoPerifericoConfig nueva = new NodoPerifericoConfig(
                key,
                nombre.trim(),
                CategoriaNodoPeriferico.BALANZA,
                endpoint,
                peaje,
                habilitado,
                modoEfectivo
        );
        nodos.put(key, nueva);
        sincronizarNodoEnDb(nueva);
        versionConfiguracion.incrementAndGet();
        LOGGER.log(Level.INFO, "Nueva balanza periférica agregada: {0} ({1}) -> {2}", new Object[]{key, peaje, endpoint});
        return nueva;
    }

    @Override
    public NodoPerifericoConfig agregarBalanza(
            String token,
            String codigo,
            String nombre,
            String estacionPeaje,
            String endpointUrl,
            ModoNodoPeriferico modo,
            boolean habilitado
    ) {
        exigirAdmin(token);
        return agregarBalanza(codigo, nombre, estacionPeaje, endpointUrl, modo, habilitado);
    }

    @Override
    public synchronized boolean eliminarBalanzaPersonalizada(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        String key = codigo.trim().toUpperCase(Locale.ROOT);
        if (CODIGOS_PREDEFINIDOS.contains(key)) {
            throw new BusinessException("Los nodos base del sistema (" + key + ") no se eliminan; podés desconectarlos desde el control operativo.");
        }
        NodoPerifericoConfig removed = nodos.remove(key);
        if (removed != null) {
            eliminarNodoEnDb(key);
            String marker = "|" + key + "|";
            autoPesadasEmitidas.removeIf(k -> k.contains(marker));
            versionConfiguracion.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean eliminarBalanzaPersonalizada(String token, String codigo) {
        exigirAdmin(token);
        return eliminarBalanzaPersonalizada(codigo);
    }

    @Override
    public long getVersionConfiguracion() {
        return versionConfiguracion.get();
    }

    @Override
    public synchronized boolean yaEmitioAutoPesada(Long guiaId, String codigoBalanza, ModoNodoPeriferico modo) {
        return autoPesadasEmitidas.contains(claveAutoPesada(guiaId, codigoBalanza, modo));
    }

    @Override
    public synchronized void marcarAutoPesadaEmitida(Long guiaId, String codigoBalanza, ModoNodoPeriferico modo) {
        autoPesadasEmitidas.add(claveAutoPesada(guiaId, codigoBalanza, modo));
    }

    @Override
    public synchronized boolean isWatermarkInicializado(String endpointUrl) {
        return endpointsWatermarkInicializados.contains(normalizarBalanzaUrl(endpointUrl));
    }

    @Override
    public synchronized void inicializarWatermark(String endpointUrl, int maxIdInicial) {
        String key = normalizarBalanzaUrl(endpointUrl);
        endpointsWatermarkInicializados.add(key);
        int nuevo = watermarkPorEndpoint.merge(key, maxIdInicial, Math::max);
        sincronizarWatermarkEnDb(key, nuevo);
    }

    @Override
    public synchronized int getWatermarkIdPesada(String endpointUrl) {
        return watermarkPorEndpoint.getOrDefault(normalizarBalanzaUrl(endpointUrl), 0);
    }

    @Override
    public synchronized void actualizarWatermarkIdPesada(String endpointUrl, int idPesada) {
        String key = normalizarBalanzaUrl(endpointUrl);
        endpointsWatermarkInicializados.add(key);
        int nuevo = watermarkPorEndpoint.merge(key, idPesada, Math::max);
        sincronizarWatermarkEnDb(key, nuevo);
    }

    private void exigirAdmin(String token) {
        if (control == null) {
            throw new ErrorAcceso(500, "Servicio de control de acceso no disponible");
        }
        control.exigir(token, TipoUsuario.ADMINISTRADOR);
    }

    private void sincronizarNodoEnDb(NodoPerifericoConfig cfg) {
        if (em == null || cfg == null) {
            return;
        }
        try {
            ConfiguracionNodoPeriferico existente = em.find(ConfiguracionNodoPeriferico.class, cfg.codigo());
            if (existente == null) {
                em.persist(new ConfiguracionNodoPeriferico(cfg));
            } else {
                existente.aplicarDto(cfg);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error sincronizando nodo periférico en PostgreSQL: " + cfg.codigo(), e);
        }
    }

    private void eliminarNodoEnDb(String codigo) {
        if (em == null || codigo == null) {
            return;
        }
        try {
            ConfiguracionNodoPeriferico existente = em.find(ConfiguracionNodoPeriferico.class, codigo);
            if (existente != null) {
                em.remove(existente);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error eliminando nodo periférico en PostgreSQL: " + codigo, e);
        }
    }

    private void sincronizarWatermarkEnDb(String endpointNormalizado, int watermarkId) {
        if (em == null || endpointNormalizado == null) {
            return;
        }
        try {
            List<ConfiguracionNodoPeriferico> filas = em.createQuery(
                    "SELECT c FROM ConfiguracionNodoPeriferico c",
                    ConfiguracionNodoPeriferico.class
            ).getResultList();
            if (filas == null) {
                return;
            }
            for (ConfiguracionNodoPeriferico fila : filas) {
                if (fila != null && fila.getEndpointUrl() != null
                        && normalizarBalanzaUrl(fila.getEndpointUrl()).equals(endpointNormalizado)) {
                    fila.setWatermarkInicializado(true);
                    fila.setWatermarkId(Math.max(fila.getWatermarkId(), watermarkId));
                }
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error persistiendo watermark de balanza en PostgreSQL", e);
        }
    }

    private String normalizarCodigoExistente(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("El código del nodo es obligatorio.");
        }
        String key = codigo.trim().toUpperCase(Locale.ROOT);
        if (!nodos.containsKey(key)) {
            throw new BusinessException("No existe ningún nodo periférico registrado con código " + key);
        }
        return key;
    }

    private static String claveAutoPesada(Long guiaId, String codigoBalanza, ModoNodoPeriferico modo) {
        String cod = codigoBalanza != null ? codigoBalanza.trim().toUpperCase(Locale.ROOT) : "BAL";
        String m = modo != null ? modo.name() : ModoNodoPeriferico.CALIBRADA.name();
        return guiaId + "|" + cod + "|" + m;
    }

    public static String resolverBalanzaBase() {
        String envBalanza = System.getenv("BALANZA_ENDPOINT");
        return normalizarBalanzaUrl((envBalanza != null && !envBalanza.isBlank()) ? envBalanza : "http://localhost:8082");
    }

    public static String resolverTrackingBase() {
        String propUrl = System.getProperty("tracking.endpoint");
        String envTracking = System.getenv("TRACKING_ENDPOINT");
        String raw = (propUrl != null && !propUrl.isBlank())
                ? propUrl
                : ((envTracking != null && !envTracking.isBlank()) ? envTracking : "http://localhost:8085");
        return normalizarTrackingUrl(raw);
    }

    public static String normalizarBalanzaUrl(String raw) {
        String url = (raw == null || raw.isBlank()) ? "http://localhost:8082" : raw.trim();
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

    public static String normalizarTrackingUrl(String raw) {
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
}
