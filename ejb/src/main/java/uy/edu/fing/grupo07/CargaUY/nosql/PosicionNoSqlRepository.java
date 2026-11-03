package uy.edu.fing.grupo07.CargaUY.nosql;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Singleton;
import org.bson.Document;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repositorio NoSQL y de serie temporal sobre MongoDB para la persistencia de eventos de posición (Req. Opcional 10).
 * Utiliza una colección Time-Series nativa de MongoDB (timeField="tiempo", metaField="metadata")
 * con indexación compuesta por guía/tiempo, desvío de ruta y coordenadas GeoJSON (2dsphere).
 * Incluye respaldo resiliente en memoria en caso de indisponibilidad temporal del motor NoSQL.
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class PosicionNoSqlRepository implements PosicionNoSqlRepositoryLocal {

    private static final Logger LOGGER = Logger.getLogger(PosicionNoSqlRepository.class.getName());

    private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_DATABASE = "cargauy_tracking";
    private static final String DEFAULT_COLLECTION = "eventos_posicion";

    private final String mongoUri;
    private final String databaseName;
    private final String collectionName;
    private final boolean soloMemoria;

    private volatile MongoClient mongoClient;
    private volatile MongoDatabase database;
    private volatile boolean coleccionInicializada = false;
    private volatile long reintentarConexionDespuesDeMillis = 0L;

    // Respaldo resiliente en memoria para continuidad operativa o entornos de test sin contenedor Mongo activo
    private final Map<String, EventoViajeDTO> respaldoMemoria = new ConcurrentHashMap<>();

    public PosicionNoSqlRepository() {
        this.mongoUri = resolverConfig("MONGO_URI", "cargauy.mongo.uri", DEFAULT_MONGO_URI);
        this.databaseName = resolverConfig("MONGO_DB", "cargauy.mongo.db", DEFAULT_DATABASE);
        this.collectionName = resolverConfig("MONGO_COLLECTION", "cargauy.mongo.collection", DEFAULT_COLLECTION);
        this.soloMemoria = false;
    }

    /**
     * Constructor para pruebas unitarias (permite operar en modo memoria o apuntando a instancia de test).
     */
    public PosicionNoSqlRepository(boolean soloMemoria) {
        this.mongoUri = DEFAULT_MONGO_URI;
        this.databaseName = DEFAULT_DATABASE;
        this.collectionName = DEFAULT_COLLECTION;
        this.soloMemoria = soloMemoria;
    }

    public PosicionNoSqlRepository(String mongoUri, String databaseName, String collectionName) {
        this.mongoUri = mongoUri != null && !mongoUri.isBlank() ? mongoUri.trim() : DEFAULT_MONGO_URI;
        this.databaseName = databaseName != null && !databaseName.isBlank() ? databaseName.trim() : DEFAULT_DATABASE;
        this.collectionName = collectionName != null && !collectionName.isBlank() ? collectionName.trim() : DEFAULT_COLLECTION;
        this.soloMemoria = false;
    }

    @PostConstruct
    public void inicializar() {
        if (soloMemoria) {
            return;
        }
        try {
            asegurarColeccionTimeSeries();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "MongoDB no disponible durante el arranque inicial ({0}). Se reintentará bajo demanda.", e.getMessage());
        }
    }

    @PreDestroy
    public void cerrar() {
        MongoClient cliente = this.mongoClient;
        this.mongoClient = null;
        this.database = null;
        this.coleccionInicializada = false;
        if (cliente != null) {
            try {
                cliente.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error al cerrar cliente MongoDB", e);
            }
        }
    }

    @Override
    public EventoViajeDTO guardarEventoPosicion(EventoViajeDTO dto, Long guiaId, String matricula) {
        if (dto == null) {
            return null;
        }

        String uuid = (dto.uuid() != null && !dto.uuid().isBlank())
                ? dto.uuid().trim()
                : UUID.randomUUID().toString();
        LocalDateTime tiempo = dto.tiempo() != null ? dto.tiempo() : LocalDateTime.now();
        TipoEvento tipo = dto.tipo() != null ? dto.tipo() : TipoEvento.POSICION;
        Long idGuiaEfectivo = guiaId != null ? guiaId : dto.guiaId();
        String matriculaEfectiva = (matricula != null && !matricula.isBlank())
                ? matricula.trim()
                : dto.matricula();

        EventoViajeDTO normalizado = new EventoViajeDTO(
                uuid,
                tiempo,
                tipo,
                dto.latitud(),
                dto.longitud(),
                idGuiaEfectivo,
                dto.incidente(),
                dto.distanciaDesvioKm(),
                dto.cantidadKg(),
                dto.mercaderia(),
                dto.empresaCarga(),
                matriculaEfectiva
        );

        respaldoMemoria.put(uuid, normalizado);

        if (soloMemoria) {
            return normalizado;
        }

        try {
            MongoCollection<Document> col = obtenerColeccion();
            if (col != null) {
                Document existente = col.find(Filters.eq("uuid", uuid)).first();
                if (existente == null) {
                    Document doc = mapearADocumentoBson(normalizado);
                    col.insertOne(doc);
                    LOGGER.log(
                            Level.INFO,
                            "Evento de posición persistido en MongoDB Time-Series [{0}.{1}] UUID={2}, guiaId={3}, desvioKm={4}",
                            new Object[]{databaseName, collectionName, uuid, idGuiaEfectivo, normalizado.distanciaDesvioKm()}
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "No se pudo persistir el evento de posición {0} en MongoDB ({1}); se mantiene en almacenamiento de respaldo.",
                    new Object[]{uuid, e.getMessage()}
            );
        }

        return normalizado;
    }

    @Override
    public boolean existeEvento(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        String limpio = uuid.trim();
        if (respaldoMemoria.containsKey(limpio)) {
            return true;
        }
        if (soloMemoria) {
            return false;
        }
        try {
            MongoCollection<Document> col = obtenerColeccion();
            if (col != null) {
                Document doc = col.find(Filters.eq("uuid", limpio)).first();
                return doc != null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Fallo consultando existencia de UUID en MongoDB: {0}", e.getMessage());
        }
        return false;
    }

    @Override
    public List<EventoViajeDTO> listarEventosPorGuia(Long guiaId) {
        if (guiaId == null) {
            return List.of();
        }

        Map<String, EventoViajeDTO> porUuid = new ConcurrentHashMap<>();

        if (!soloMemoria) {
            try {
                MongoCollection<Document> col = obtenerColeccion();
                if (col != null) {
                    FindIterable<Document> cursor = col
                            .find(Filters.or(
                                    Filters.eq("metadata.guiaId", guiaId),
                                    Filters.eq("guiaId", guiaId)
                            ))
                            .sort(Sorts.ascending("tiempo"));

                    for (Document doc : cursor) {
                        EventoViajeDTO dto = mapearDesdeDocumentoBson(doc);
                        if (dto != null && dto.uuid() != null) {
                            porUuid.put(dto.uuid(), dto);
                            respaldoMemoria.putIfAbsent(dto.uuid(), dto);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al consultar eventos de posición en MongoDB para guía {0}: {1}", new Object[]{guiaId, e.getMessage()});
            }
        }

        for (EventoViajeDTO mem : respaldoMemoria.values()) {
            if (mem != null && guiaId.equals(mem.guiaId()) && mem.uuid() != null) {
                porUuid.putIfAbsent(mem.uuid(), mem);
            }
        }

        List<EventoViajeDTO> resultado = new ArrayList<>(porUuid.values());
        resultado.sort(Comparator.comparing(
                ev -> ev.tiempo() != null ? ev.tiempo() : LocalDateTime.MIN
        ));
        return resultado;
    }

    @Override
    public EventoViajeDTO buscarEventoConMayorDesvio(Long guiaId) {
        if (guiaId == null) {
            return null;
        }

        EventoViajeDTO mayor = null;

        if (!soloMemoria) {
            try {
                MongoCollection<Document> col = obtenerColeccion();
                if (col != null) {
                    Document doc = col.find(Filters.and(
                                    Filters.or(
                                            Filters.eq("metadata.guiaId", guiaId),
                                            Filters.eq("guiaId", guiaId)
                                    ),
                                    Filters.ne("distanciaDesvioKm", null)
                            ))
                            .sort(Sorts.descending("distanciaDesvioKm"))
                            .first();
                    if (doc != null) {
                        mayor = mapearDesdeDocumentoBson(doc);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error consultando mayor desvío en MongoDB para guía {0}: {1}", new Object[]{guiaId, e.getMessage()});
            }
        }

        for (EventoViajeDTO mem : respaldoMemoria.values()) {
            if (mem != null && guiaId.equals(mem.guiaId()) && mem.distanciaDesvioKm() != null) {
                if (mayor == null || mayor.distanciaDesvioKm() == null || mem.distanciaDesvioKm() > mayor.distanciaDesvioKm()) {
                    mayor = mem;
                }
            }
        }

        return mayor;
    }

    @Override
    public long contarEventosPorGuia(Long guiaId) {
        if (guiaId == null) {
            return 0L;
        }
        return listarEventosPorGuia(guiaId).size();
    }

    @Override
    public long contarTotalEventos() {
        if (!soloMemoria) {
            try {
                MongoCollection<Document> col = obtenerColeccion();
                if (col != null) {
                    return col.countDocuments();
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "No se pudo contar documentos en MongoDB: {0}", e.getMessage());
            }
        }
        return respaldoMemoria.size();
    }

    @Override
    public boolean estaOperativo() {
        if (soloMemoria) {
            return true;
        }
        try {
            MongoDatabase db = obtenerDatabase();
            if (db == null) {
                return false;
            }
            Document ping = db.runCommand(new Document("ping", 1));
            return ping != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String obtenerDescriptorEndpoint() {
        return mongoUri + "/" + databaseName + "." + collectionName + " (Time-Series)";
    }

    private MongoCollection<Document> obtenerColeccion() {
        if (soloMemoria) {
            return null;
        }
        if (!coleccionInicializada) {
            if (System.currentTimeMillis() < reintentarConexionDespuesDeMillis) {
                return null;
            }
            asegurarColeccionTimeSeries();
            if (!coleccionInicializada) {
                return null;
            }
        }
        MongoDatabase db = this.database;
        return db != null ? db.getCollection(collectionName) : null;
    }

    private synchronized MongoDatabase obtenerDatabase() {
        if (this.database != null) {
            return this.database;
        }
        ConnectionString connString = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(1500, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(1500, TimeUnit.MILLISECONDS)
                                .readTimeout(2500, TimeUnit.MILLISECONDS))
                .build();
        this.mongoClient = MongoClients.create(settings);
        this.database = this.mongoClient.getDatabase(databaseName);
        return this.database;
    }

    private synchronized void asegurarColeccionTimeSeries() {
        if (coleccionInicializada || soloMemoria) {
            return;
        }
        if (System.currentTimeMillis() < reintentarConexionDespuesDeMillis) {
            return;
        }
        try {
            MongoDatabase db = obtenerDatabase();
            if (db == null) {
                return;
            }

            boolean existe = false;
            for (String nombre : db.listCollectionNames()) {
                if (collectionName.equalsIgnoreCase(nombre)) {
                    existe = true;
                    break;
                }
            }

            if (!existe) {
                try {
                    TimeSeriesOptions tsOptions = new TimeSeriesOptions("tiempo")
                            .metaField("metadata")
                            .granularity(TimeSeriesGranularity.SECONDS);
                    CreateCollectionOptions createOptions = new CreateCollectionOptions()
                            .timeSeriesOptions(tsOptions);
                    db.createCollection(collectionName, createOptions);
                    LOGGER.log(Level.INFO, "Colección Time-Series MongoDB creada exitosamente: {0}.{1}", new Object[]{databaseName, collectionName});
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Nota al crear colección Time-Series {0}: {1}", new Object[]{collectionName, e.getMessage()});
                }
            }

            try {
                MongoCollection<Document> col = db.getCollection(collectionName);
                col.createIndex(Indexes.compoundIndex(Indexes.ascending("metadata.guiaId"), Indexes.ascending("tiempo")));
                col.createIndex(Indexes.compoundIndex(Indexes.ascending("metadata.guiaId"), Indexes.descending("distanciaDesvioKm")));
                col.createIndex(Indexes.ascending("uuid"));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Nota al verificar índices de serie temporal en MongoDB: {0}", e.getMessage());
            }

            coleccionInicializada = true;
            reintentarConexionDespuesDeMillis = 0L;
        } catch (Exception e) {
            reintentarConexionDespuesDeMillis = System.currentTimeMillis() + 15_000L;
            LOGGER.log(Level.FINE, "MongoDB no disponible temporalmente, usando respaldo en memoria por 15s: {0}", e.getMessage());
        }
    }

    static Document mapearADocumentoBson(EventoViajeDTO dto) {
        LocalDateTime tiempo = dto.tiempo() != null ? dto.tiempo() : LocalDateTime.now();
        Date fechaBson = Date.from(tiempo.toInstant(ZoneOffset.UTC));
        String tipoStr = dto.tipo() != null ? dto.tipo().name() : TipoEvento.POSICION.name();

        Document metadata = new Document()
                .append("guiaId", dto.guiaId())
                .append("matricula", dto.matricula())
                .append("tipo", tipoStr);

        Document ubicacionGeoJson = new Document("type", "Point")
                .append("coordinates", List.of(dto.longitud(), dto.latitud()));

        Document doc = new Document()
                .append("uuid", dto.uuid())
                .append("tiempo", fechaBson)
                .append("tiempoIso", tiempo.toString())
                .append("metadata", metadata)
                .append("guiaId", dto.guiaId())
                .append("matricula", dto.matricula())
                .append("tipo", tipoStr)
                .append("latitud", dto.latitud())
                .append("longitud", dto.longitud())
                .append("ubicacion", ubicacionGeoJson);

        if (dto.distanciaDesvioKm() != null) {
            doc.append("distanciaDesvioKm", dto.distanciaDesvioKm());
        }
        if (dto.cantidadKg() != null) {
            doc.append("cantidadKg", dto.cantidadKg());
        }
        if (dto.mercaderia() != null) {
            doc.append("mercaderia", dto.mercaderia());
        }
        if (dto.empresaCarga() != null) {
            doc.append("empresaCarga", dto.empresaCarga());
        }
        if (dto.incidente() != null) {
            doc.append("incidente", new Document()
                    .append("foto", dto.incidente().foto())
                    .append("descripcion", dto.incidente().descripcion()));
        }
        return doc;
    }

    static EventoViajeDTO mapearDesdeDocumentoBson(Document doc) {
        if (doc == null) {
            return null;
        }
        String uuid = doc.getString("uuid");
        LocalDateTime tiempo = null;
        String tiempoIso = doc.getString("tiempoIso");
        if (tiempoIso != null && !tiempoIso.isBlank()) {
            try {
                tiempo = LocalDateTime.parse(tiempoIso);
            } catch (Exception ignored) {
            }
        }
        if (tiempo == null) {
            Date fechaBson = doc.getDate("tiempo");
            if (fechaBson != null) {
                tiempo = LocalDateTime.ofInstant(Instant.ofEpochMilli(fechaBson.getTime()), ZoneOffset.UTC);
            } else {
                tiempo = LocalDateTime.now();
            }
        }

        Document metadata = doc.get("metadata", Document.class);
        String tipoStr = doc.getString("tipo");
        if (tipoStr == null && metadata != null) {
            tipoStr = metadata.getString("tipo");
        }
        TipoEvento tipo = TipoEvento.POSICION;
        if (tipoStr != null) {
            try {
                tipo = TipoEvento.valueOf(tipoStr);
            } catch (IllegalArgumentException ignored) {
                tipo = TipoEvento.POSICION;
            }
        }

        Long guiaId = leerLong(doc, "guiaId");
        if (guiaId == null && metadata != null) {
            guiaId = leerLong(metadata, "guiaId");
        }

        String matricula = doc.getString("matricula");
        if (matricula == null && metadata != null) {
            matricula = metadata.getString("matricula");
        }

        double latitud = leerDoublePrimitivo(doc, "latitud");
        double longitud = leerDoublePrimitivo(doc, "longitud");
        Double distanciaDesvioKm = leerDoubleObjeto(doc, "distanciaDesvioKm");
        Double cantidadKg = leerDoubleObjeto(doc, "cantidadKg");
        String mercaderia = doc.getString("mercaderia");
        String empresaCarga = doc.getString("empresaCarga");

        IncidenteDTO incDto = null;
        Document incDoc = doc.get("incidente", Document.class);
        if (incDoc != null) {
            incDto = new IncidenteDTO(incDoc.getString("foto"), incDoc.getString("descripcion"));
        }

        return new EventoViajeDTO(
                uuid,
                tiempo,
                tipo,
                latitud,
                longitud,
                guiaId,
                incDto,
                distanciaDesvioKm,
                cantidadKg,
                mercaderia,
                empresaCarga,
                matricula
        );
    }

    private static Long leerLong(Document doc, String clave) {
        Object val = doc.get(clave);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    private static double leerDoublePrimitivo(Document doc, String clave) {
        Object val = doc.get(clave);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }

    private static Double leerDoubleObjeto(Document doc, String clave) {
        Object val = doc.get(clave);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }

    private static String resolverConfig(String envKey, String sysPropKey, String defaultValue) {
        String prop = System.getProperty(sysPropKey);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultValue;
    }
}
