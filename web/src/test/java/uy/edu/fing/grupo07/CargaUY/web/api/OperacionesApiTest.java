package uy.edu.fing.grupo07.CargaUY.web.api;

import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.integration.TrackingClient;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.service.EstadoService;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - OperacionesApi (/ops/health y /ops/metricas)")
class OperacionesApiTest {

    private OperacionesApi crearApi(
            ControlAcceso control,
            ClientePdi pdi,
            PosicionNoSqlRepositoryLocal mongo,
            EstadoService estadoService,
            BalanzaClient balanzaClient,
            TrackingClient trackingClient,
            HttpServletRequest req
    ) throws Exception {
        OperacionesApi api = new OperacionesApi();
        for (Map.Entry<String, Object> entry : Map.of(
                "control", control,
                "pdi", pdi,
                "posicionNoSqlRepository", mongo,
                "estadoService", estadoService,
                "balanzaClient", balanzaClient,
                "trackingClient", trackingClient,
                "req", req
        ).entrySet()) {
            Field f = OperacionesApi.class.getDeclaredField(entry.getKey());
            f.setAccessible(true);
            f.set(api, entry.getValue());
        }
        return api;
    }

    @Test
    @DisplayName("health y metricas exigen control.admin")
    void testRequiereAdministrador() throws Exception {
        ControlAcceso control = mock(ControlAcceso.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("token")).thenReturn("tok-user");
        doThrow(new ErrorAcceso(403, "Solo admin")).when(control).admin("tok-user");

        OperacionesApi api = crearApi(
                control,
                mock(ClientePdi.class),
                mock(PosicionNoSqlRepositoryLocal.class),
                mock(EstadoService.class),
                mock(BalanzaClient.class),
                mock(TrackingClient.class),
                req
        );

        assertEquals(403, assertThrows(ErrorAcceso.class, api::health).getEstado());
        assertEquals(403, assertThrows(ErrorAcceso.class, api::metricas).getEstado());
    }

    @Test
    @DisplayName("health reporta UP cuando PostgreSQL, Mongo, JMS y circuitos están saludables")
    void testHealthUp() throws Exception {
        ControlAcceso control = mock(ControlAcceso.class);
        ClientePdi pdi = mock(ClientePdi.class);
        PosicionNoSqlRepositoryLocal mongo = mock(PosicionNoSqlRepositoryLocal.class);
        EstadoService estado = mock(EstadoService.class);
        BalanzaClient balanza = mock(BalanzaClient.class);
        TrackingClient tracking = mock(TrackingClient.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("token")).thenReturn("tok-admin");

        when(estado.listo()).thenReturn(true);
        when(estado.jmsListo()).thenReturn(true);
        when(mongo.estaOperativo()).thenReturn(true);
        when(balanza.estaCircuitoAbierto()).thenReturn(false);
        when(tracking.estaCircuitoAbierto()).thenReturn(false);

        OperacionesApi api = crearApi(control, pdi, mongo, estado, balanza, tracking, req);
        JsonObject json = api.health();

        verify(control).admin("tok-admin");
        assertEquals("UP", json.getString("status"));
        assertEquals("UP", json.getString("postgres"));
        assertEquals("UP", json.getString("mongodb"));
        assertEquals("UP", json.getString("jmsTracking"));
        assertEquals("CLOSED", json.getString("balanzaCircuito"));
        assertEquals("CLOSED", json.getString("trackingCircuito"));
    }

    @Test
    @DisplayName("health reporta DEGRADED y metricas incluye contadores de PDI, Balanza y Tracking")
    void testHealthDegradadoYMetricas() throws Exception {
        ControlAcceso control = mock(ControlAcceso.class);
        ClientePdi pdi = mock(ClientePdi.class);
        PosicionNoSqlRepositoryLocal mongo = mock(PosicionNoSqlRepositoryLocal.class);
        EstadoService estado = mock(EstadoService.class);
        BalanzaClient balanza = mock(BalanzaClient.class);
        TrackingClient tracking = mock(TrackingClient.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("token")).thenReturn("tok-admin");

        when(estado.listo()).thenReturn(false);
        when(estado.jmsListo()).thenReturn(false);
        when(mongo.estaOperativo()).thenReturn(false);
        when(balanza.estaCircuitoAbierto()).thenReturn(true);
        when(tracking.estaCircuitoAbierto()).thenReturn(true);

        when(pdi.metricas()).thenReturn(new long[]{10L, 1L, 0L});
        when(balanza.metricas()).thenReturn(new long[]{20L, 4L, 2L});
        when(tracking.metricas()).thenReturn(new long[]{30L, 3L, 1L});

        OperacionesApi api = crearApi(control, pdi, mongo, estado, balanza, tracking, req);

        JsonObject healthDown = api.health();
        assertEquals("DOWN", healthDown.getString("status"));
        assertEquals("DOWN", healthDown.getString("postgres"));
        assertEquals("DEGRADED_FALLBACK_MEMORIA", healthDown.getString("mongodb"));
        assertEquals("DOWN", healthDown.getString("jmsTracking"));
        assertEquals("OPEN", healthDown.getString("balanzaCircuito"));
        assertEquals("OPEN", healthDown.getString("trackingCircuito"));

        when(estado.listo()).thenReturn(true);
        when(estado.jmsListo()).thenReturn(true);
        JsonObject healthDegraded = api.health();
        assertEquals("DEGRADED", healthDegraded.getString("status"));

        JsonObject metricas = api.metricas();
        assertTrue(metricas.containsKey("errores"));
        assertTrue(metricas.containsKey("errores4xx"));
        assertTrue(metricas.containsKey("errores5xx"));
        assertEquals(10L, metricas.getJsonNumber("pdiIntentos").longValue());
        assertEquals(20L, metricas.getJsonNumber("balanzaLlamadas").longValue());
        assertEquals(4L, metricas.getJsonNumber("balanzaFallas").longValue());
        assertEquals(2L, metricas.getJsonNumber("balanzaRechazos").longValue());
        assertTrue(metricas.getBoolean("balanzaCircuitoAbierto"));
        assertEquals(30L, metricas.getJsonNumber("trackingLlamadas").longValue());
        assertEquals(3L, metricas.getJsonNumber("trackingFallas").longValue());
        assertEquals(1L, metricas.getJsonNumber("trackingRechazos").longValue());
        assertTrue(metricas.getBoolean("trackingCircuitoAbierto"));
    }
}
