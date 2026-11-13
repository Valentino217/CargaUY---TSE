package uy.edu.fing.grupo07.CargaUY.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.dto.CategoriaNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - GestionNodosPerifericosService")
class GestionNodosPerifericosServiceTest {

    private GestionNodosPerifericosServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GestionNodosPerifericosServiceImpl();
        service.init();
    }

    @Test
    @DisplayName("Debe inicializar los 3 nodos base apagados (BAL-01 CALIBRADA, BAL-02 SOBRECARGA, TRK-01 RUTA_NORMAL)")
    void testEstadoInicialPorDefecto() {
        List<NodoPerifericoConfig> todos = service.listarNodos();
        assertEquals(3, todos.size());

        NodoPerifericoConfig bal1 = service.buscarPorCodigo("nodo-bal-01").orElseThrow();
        assertEquals("NODO-BAL-01", bal1.codigo());
        assertFalse(bal1.habilitado());
        assertTrue(bal1.esBalanza());
        assertFalse(bal1.esTracking());
        assertEquals(ModoNodoPeriferico.CALIBRADA, bal1.modoOperacion());

        NodoPerifericoConfig bal2 = service.buscarPorCodigo("NODO-BAL-02").orElseThrow();
        assertFalse(bal2.habilitado());
        assertEquals(ModoNodoPeriferico.SOBRECARGA, bal2.modoOperacion());

        NodoPerifericoConfig trk1 = service.buscarPorCodigo("NODO-TRK-01").orElseThrow();
        assertFalse(trk1.habilitado());
        assertTrue(trk1.esTracking());
        assertEquals(ModoNodoPeriferico.RUTA_NORMAL, trk1.modoOperacion());

        assertEquals(0, service.listarBalanzasHabilitadas().size());
        assertEquals(1, service.listarNodosTracking().size());
        assertFalse(service.isTrackingHabilitado());
        assertFalse(service.isAlgunaBalanzaHabilitada());
        assertEquals(ModoNodoPeriferico.RUTA_NORMAL, service.getModoTrackingActivo());
        assertFalse(service.buscarPorCodigo(" ").isPresent());
        assertFalse(service.isNodoHabilitado("NO-EXISTE"));
    }

    @Test
    @DisplayName("Permite apagar/encender nodos, limpia auto-pesadas al reencender balanza e incrementa versión")
    void testSetHabilitadoYVersion() {
        long v0 = service.getVersionConfiguracion();

        service.marcarAutoPesadaEmitida(1001L, "NODO-BAL-02", ModoNodoPeriferico.SOBRECARGA);
        assertTrue(service.yaEmitioAutoPesada(1001L, "NODO-BAL-02", ModoNodoPeriferico.SOBRECARGA));

        service.setHabilitado("NODO-BAL-02", true);
        assertTrue(service.isNodoHabilitado("NODO-BAL-02"));
        assertEquals(1, service.listarBalanzasHabilitadas().size());
        assertFalse(service.yaEmitioAutoPesada(1001L, "NODO-BAL-02", ModoNodoPeriferico.SOBRECARGA));
        assertTrue(service.getVersionConfiguracion() > v0);

        // Idempotencia: repetir el mismo estado no incrementa versión
        long v1 = service.getVersionConfiguracion();
        service.setHabilitado("NODO-BAL-02", true);
        assertEquals(v1, service.getVersionConfiguracion());

        // Apagar ambas balanzas y tracking
        service.setHabilitado("NODO-BAL-01", false);
        service.setHabilitado("NODO-BAL-02", false);
        service.setHabilitado("NODO-TRK-01", false);
        assertFalse(service.isAlgunaBalanzaHabilitada());
        assertFalse(service.isTrackingHabilitado());

        assertThrows(BusinessException.class, () -> service.setHabilitado("INEXISTENTE", true));
        assertThrows(BusinessException.class, () -> service.setHabilitado("", true));
    }

    @Test
    @DisplayName("Permite cambiar modo operativo validando compatibilidad por categoría")
    void testCambiarModoYValidaciones() {
        service.cambiarModo("NODO-BAL-01", ModoNodoPeriferico.SOBRECARGA);
        assertEquals(ModoNodoPeriferico.SOBRECARGA, service.buscarPorCodigo("NODO-BAL-01").orElseThrow().modoOperacion());

        service.cambiarModo("NODO-TRK-01", ModoNodoPeriferico.CON_DESVIO);
        assertEquals(ModoNodoPeriferico.CON_DESVIO, service.getModoTrackingActivo());
        assertEquals(CategoriaNodoPeriferico.TRACKING, ModoNodoPeriferico.CON_DESVIO.getCategoria());
        assertNotNull(ModoNodoPeriferico.CON_DESVIO.getEtiqueta());

        assertThrows(BusinessException.class, () -> service.cambiarModo("NODO-BAL-01", null));
        assertThrows(BusinessException.class, () -> service.cambiarModo("NODO-BAL-01", ModoNodoPeriferico.CON_DESVIO));
        assertThrows(BusinessException.class, () -> service.cambiarModo("NODO-TRK-01", ModoNodoPeriferico.CALIBRADA));
    }

    @Test
    @DisplayName("Permite agregar y eliminar balanzas personalizadas, actualizando y normalizando endpoints")
    void testAgregarEliminarBalanzaYActualizarEndpoint() {
        NodoPerifericoConfig custom = service.agregarBalanza(
                "nodo-bal-03",
                "Balanza Ruta 3",
                "",
                "https://mi-balanza.example.com/pesadas/",
                ModoNodoPeriferico.SOBRECARGA,
                true
        );
        assertEquals("NODO-BAL-03", custom.codigo());
        assertEquals("Balanza Ruta 3", custom.estacionPeaje());
        assertEquals("https://mi-balanza.example.com", custom.endpointUrl());
        assertEquals(1, service.listarBalanzasHabilitadas().size());

        service.actualizarEndpoint("NODO-BAL-03", "http://bal3:8080/health");
        assertEquals("http://bal3:8080", service.buscarPorCodigo("NODO-BAL-03").orElseThrow().endpointUrl());

        service.actualizarEndpoint("NODO-TRK-01", "http://trk:8085/estado/");
        assertEquals("http://trk:8085", service.getEndpointTrackingActivo());

        assertThrows(BusinessException.class, () -> service.actualizarEndpoint("NODO-BAL-01", " "));
        assertThrows(BusinessException.class, () -> service.agregarBalanza("NODO-BAL-03", "Dup", null, null, null, true));
        assertThrows(BusinessException.class, () -> service.agregarBalanza(" ", "SinCodigo", null, null, null, true));
        assertThrows(BusinessException.class, () -> service.agregarBalanza("NODO-BAL-04", " ", null, null, null, true));
        assertThrows(BusinessException.class, () -> service.eliminarBalanzaPersonalizada("NODO-BAL-01"));

        assertTrue(service.eliminarBalanzaPersonalizada("NODO-BAL-03"));
        assertFalse(service.eliminarBalanzaPersonalizada("NODO-BAL-03"));
        assertFalse(service.eliminarBalanzaPersonalizada(" "));
    }

    @Test
    @DisplayName("Gestiona correctamente los watermarks de pesadas por endpoint")
    void testWatermarkPorEndpoint() {
        String ep = "http://mock-balanza:8080/pesadas/";
        assertFalse(service.isWatermarkInicializado(ep));
        service.inicializarWatermark(ep, 40);
        assertTrue(service.isWatermarkInicializado("http://mock-balanza:8080"));
        assertEquals(40, service.getWatermarkIdPesada(ep));

        service.actualizarWatermarkIdPesada(ep, 25);
        assertEquals(40, service.getWatermarkIdPesada(ep));
        service.actualizarWatermarkIdPesada(ep, 55);
        assertEquals(55, service.getWatermarkIdPesada(ep));
    }

    @Test
    @DisplayName("Persiste nodos y watermarks en PostgreSQL, restaura estado y exige rol ADMINISTRADOR")
    void testPersistenciaDbYOverloadsTokenizados() {
        jakarta.persistence.EntityManager em = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);

        uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico existenteDb =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico();
        existenteDb.setCodigo("NODO-BAL-01");
        existenteDb.setNombre("Balanza #1 DB");
        existenteDb.setCategoria(CategoriaNodoPeriferico.BALANZA);
        existenteDb.setEndpointUrl("http://bal-db:8082/");
        existenteDb.setEstacionPeaje("Peaje DB");
        existenteDb.setHabilitado(false);
        existenteDb.setModoOperacion(ModoNodoPeriferico.SOBRECARGA);
        existenteDb.setWatermarkId(77);
        existenteDb.setWatermarkInicializado(true);

        assertEquals("NODO-BAL-01", existenteDb.getCodigo());
        assertEquals("Balanza #1 DB", existenteDb.getNombre());
        assertEquals(CategoriaNodoPeriferico.BALANZA, existenteDb.getCategoria());
        assertEquals("http://bal-db:8082/", existenteDb.getEndpointUrl());
        assertEquals("Peaje DB", existenteDb.getEstacionPeaje());
        assertFalse(existenteDb.isHabilitado());
        assertEquals(ModoNodoPeriferico.SOBRECARGA, existenteDb.getModoOperacion());
        assertEquals(77, existenteDb.getWatermarkId());
        assertTrue(existenteDb.isWatermarkInicializado());

        @SuppressWarnings("unchecked")
        jakarta.persistence.TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico> q =
                org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        org.mockito.Mockito.when(em.createQuery(
                "SELECT c FROM ConfiguracionNodoPeriferico c ORDER BY c.codigo ASC",
                uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico.class
        )).thenReturn(q);
        org.mockito.Mockito.when(q.getResultList()).thenReturn(List.of(existenteDb));

        GestionNodosPerifericosServiceImpl persistente = new GestionNodosPerifericosServiceImpl(em, control);
        persistente.init();

        assertFalse(persistente.isNodoHabilitado("NODO-BAL-01"));
        assertEquals(77, persistente.getWatermarkIdPesada("http://bal-db:8082"));
        assertTrue(persistente.isWatermarkInicializado("http://bal-db:8082"));
        // Debe haber persistido los nodos faltantes por defecto (BAL-02 y TRK-01)
        org.mockito.Mockito.verify(em, org.mockito.Mockito.times(2))
                .persist(org.mockito.ArgumentMatchers.any(uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico.class));

        // Sin control configurado debe fallar cerrado
        GestionNodosPerifericosServiceImpl sinControl = new GestionNodosPerifericosServiceImpl();
        assertThrows(
                uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso.class,
                () -> sinControl.setHabilitado("tok", "NODO-BAL-01", true)
        );

        org.mockito.Mockito.when(em.find(
                uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico.class, "NODO-BAL-01"))
                .thenReturn(existenteDb);
        org.mockito.Mockito.when(em.createQuery(
                "SELECT c FROM ConfiguracionNodoPeriferico c",
                uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico.class
        )).thenReturn(q);

        persistente.setHabilitado("tok-adm", "NODO-BAL-01", true);
        persistente.cambiarModo("tok-adm", "NODO-BAL-01", ModoNodoPeriferico.CALIBRADA);
        persistente.actualizarEndpoint("tok-adm", "NODO-BAL-01", "http://bal-db:8082/pesadas/");
        persistente.actualizarWatermarkIdPesada("http://bal-db:8082", 95);
        assertEquals(95, existenteDb.getWatermarkId());

        persistente.agregarBalanza(
                "tok-adm", "NODO-BAL-99", "Balanza Extra", "Peaje 99", "http://bal99:8080",
                ModoNodoPeriferico.CALIBRADA, true
        );
        uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico customDb =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico(
                        persistente.buscarPorCodigo("NODO-BAL-99").orElseThrow()
                );
        org.mockito.Mockito.when(em.find(
                uy.edu.fing.grupo07.CargaUY.domain.entity.ConfiguracionNodoPeriferico.class, "NODO-BAL-99"))
                .thenReturn(customDb);
        assertTrue(persistente.eliminarBalanzaPersonalizada("tok-adm", "NODO-BAL-99"));
        org.mockito.Mockito.verify(em).remove(customDb);
        org.mockito.Mockito.verify(control, org.mockito.Mockito.times(5))
                .exigir("tok-adm", uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR);
    }
}
