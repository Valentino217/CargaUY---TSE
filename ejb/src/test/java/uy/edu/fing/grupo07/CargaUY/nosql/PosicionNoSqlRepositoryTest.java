package uy.edu.fing.grupo07.CargaUY.nosql;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - PosicionNoSqlRepository (MongoDB Time-Series & BSON)")
class PosicionNoSqlRepositoryTest {

    private PosicionNoSqlRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PosicionNoSqlRepository(true);
    }

    @Test
    @DisplayName("Debe mapear EventoViajeDTO hacia y desde documento BSON con estructura Time-Series y GeoJSON Point")
    void testMapeoBsonTimeSeriesYGeoJson() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 3, 17, 30, 15);
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-pos-1",
                t,
                TipoEvento.POSICION,
                -34.9011,
                -56.1645,
                10L,
                new IncidenteDTO(null, "Telemetría GPS en ruta 5"),
                1.25,
                null,
                null,
                null,
                "STP 1234"
        );

        Document bson = PosicionNoSqlRepository.mapearADocumentoBson(dto);
        assertNotNull(bson);
        assertEquals("uuid-pos-1", bson.getString("uuid"));
        assertNotNull(bson.getDate("tiempo"));

        Document metadata = bson.get("metadata", Document.class);
        assertNotNull(metadata);
        assertEquals(10L, metadata.getLong("guiaId"));
        assertEquals("STP 1234", metadata.getString("matricula"));
        assertEquals("POSICION", metadata.getString("tipo"));

        Document ubicacion = bson.get("ubicacion", Document.class);
        assertNotNull(ubicacion);
        assertEquals("Point", ubicacion.getString("type"));

        EventoViajeDTO reconstruido = PosicionNoSqlRepository.mapearDesdeDocumentoBson(bson);
        assertNotNull(reconstruido);
        assertEquals("uuid-pos-1", reconstruido.uuid());
        assertEquals(t, reconstruido.tiempo());
        assertEquals(TipoEvento.POSICION, reconstruido.tipo());
        assertEquals(-34.9011, reconstruido.latitud(), 0.0001);
        assertEquals(-56.1645, reconstruido.longitud(), 0.0001);
        assertEquals(10L, reconstruido.guiaId());
        assertEquals(1.25, reconstruido.distanciaDesvioKm(), 0.0001);
        assertEquals("STP 1234", reconstruido.matricula());
        assertNotNull(reconstruido.incidente());
        assertEquals("Telemetría GPS en ruta 5", reconstruido.incidente().descripcion());
    }

    @Test
    @DisplayName("Debe guardar, verificar idempotencia, listar en orden temporal y buscar mayor desvío por guía")
    void testOperacionesRepositorioSerieTemporal() {
        LocalDateTime base = LocalDateTime.now();

        EventoViajeDTO p1 = new EventoViajeDTO(
                "pos-1", base.minusMinutes(10), TipoEvento.POSICION, -34.90, -56.16, 5L, null, 0.4, null, null, null, "AAA 1111"
        );
        EventoViajeDTO p2 = new EventoViajeDTO(
                "pos-2", base.minusMinutes(20), TipoEvento.POSICION, -34.95, -56.18, 5L, null, 8.7, null, null, null, "AAA 1111"
        );
        EventoViajeDTO pOtraGuia = new EventoViajeDTO(
                "pos-3", base.minusMinutes(5), TipoEvento.POSICION, -32.00, -55.00, 9L, null, 15.0, null, null, null, "BBB 2222"
        );

        repository.guardarEventoPosicion(p1, 5L, "AAA 1111");
        repository.guardarEventoPosicion(p2, 5L, "AAA 1111");
        repository.guardarEventoPosicion(pOtraGuia, 9L, "BBB 2222");

        assertTrue(repository.existeEvento("pos-1"));
        assertTrue(repository.existeEvento("pos-2"));
        assertFalse(repository.existeEvento("inexistente"));
        assertFalse(repository.existeEvento("  "));

        List<EventoViajeDTO> serieGuia5 = repository.listarEventosPorGuia(5L);
        assertEquals(2, serieGuia5.size());
        // Debe estar ordenada cronológicamente (p2 es anterior a p1)
        assertEquals("pos-2", serieGuia5.get(0).uuid());
        assertEquals("pos-1", serieGuia5.get(1).uuid());

        EventoViajeDTO mayorDesvio = repository.buscarEventoConMayorDesvio(5L);
        assertNotNull(mayorDesvio);
        assertEquals("pos-2", mayorDesvio.uuid());
        assertEquals(8.7, mayorDesvio.distanciaDesvioKm(), 0.001);

        assertEquals(2L, repository.contarEventosPorGuia(5L));
        assertEquals(3L, repository.contarTotalEventos());
        assertTrue(repository.estaOperativo());
        assertEquals("mongodb://localhost:27017/cargauy_tracking.eventos_posicion", repository.obtenerDescriptorEndpoint());
    }

    @Test
    @DisplayName("GestionEventosServiceImpl persiste eventos POSICION en NoSQL sin escribir en JPA y los unifica en listarEventosPorGuia")
    @SuppressWarnings("unchecked")
    void testIntegracionGestionEventosServiceConNoSql() {
        EntityManager emMock = mock(EntityManager.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        TypedQuery<EventoViaje> listQuery = mock(TypedQuery.class);

        when(emMock.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        when(emMock.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(listQuery);
        when(listQuery.setParameter(anyString(), any())).thenReturn(listQuery);
        when(listQuery.getResultList()).thenReturn(Collections.emptyList());

        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(42L);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");
        guia.setEstado(TipoEstado.EN_CURSO);
        when(emMock.find(GuiaDeViaje.class, 42L)).thenReturn(guia);

        GestionEventosServiceImpl service = new GestionEventosServiceImpl(emMock, null, null, repository);

        EventoViajeDTO evPosicion = new EventoViajeDTO(
                "uuid-nosql-pos-42",
                LocalDateTime.now(),
                TipoEvento.POSICION,
                -33.50,
                -56.05,
                42L,
                null,
                3.45,
                null,
                null,
                null,
                "STP 9999"
        );

        service.guardarEvento(evPosicion);

        // El evento POSICION se guarda en el repositorio NoSQL y NO se persiste como fila relacional en JPA
        verify(emMock, never()).persist(any(EventoViaje.class));
        assertTrue(service.existeEvento("uuid-nosql-pos-42"));

        List<EventoViajeDTO> listados = service.listarEventosPorGuia(42L);
        assertEquals(1, listados.size());
        assertEquals("uuid-nosql-pos-42", listados.get(0).uuid());
        assertEquals(TipoEvento.POSICION, listados.get(0).tipo());
        assertEquals(3.45, listados.get(0).distanciaDesvioKm(), 0.001);
    }

    @Test
    @DisplayName("Debe conectar y persistir en MongoDB Time-Series real cuando el contenedor está disponible")
    void testPersistenciaRealEnMongoDbTimeSeriesSiDisponible() {
        PosicionNoSqlRepository repoMongo = new PosicionNoSqlRepository(
                "mongodb://localhost:27017",
                "cargauy_tracking_test",
                "eventos_posicion"
        );
        try {
            repoMongo.inicializar();
            if (!repoMongo.estaOperativo()) {
                return;
            }
            String uuidTest = "uuid-mongo-real-" + System.currentTimeMillis();
            EventoViajeDTO ev = new EventoViajeDTO(
                    uuidTest,
                    LocalDateTime.now(),
                    TipoEvento.POSICION,
                    -34.9011,
                    -56.1645,
                    777L,
                    new IncidenteDTO(null, "Punto GPS en colección Time-Series real"),
                    6.8,
                    null,
                    null,
                    null,
                    "MTP 7777"
            );
            repoMongo.guardarEventoPosicion(ev, 777L, "MTP 7777");
            assertTrue(repoMongo.existeEvento(uuidTest));
            List<EventoViajeDTO> lista = repoMongo.listarEventosPorGuia(777L);
            assertTrue(lista.stream().anyMatch(x -> uuidTest.equals(x.uuid())));
            EventoViajeDTO mayor = repoMongo.buscarEventoConMayorDesvio(777L);
            assertNotNull(mayor);
            assertTrue(mayor.distanciaDesvioKm() >= 6.8);
        } finally {
            repoMongo.cerrar();
        }
    }
}
