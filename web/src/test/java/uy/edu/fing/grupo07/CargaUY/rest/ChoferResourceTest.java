package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.SyncResultDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - ChoferResource (API REST de Sincronización)")
class ChoferResourceTest {

    @Mock
    private GestionEventosServiceLocal eventosService;

    private ChoferResource resource;

    @BeforeEach
    void setUp() {
        resource = new ChoferResource(eventosService);
    }

    @Test
    @DisplayName("POST /eventos/sync con lista de eventos debe retornar 200 OK y la lista de resultados")
    void testSincronizarEventosExitoso() {
        EventoViajeDTO evento = new EventoViajeDTO(
                "ev-1", LocalDateTime.now(), TipoEvento.CARGA, -34.9, -56.1, 1L, null
        );
        SyncResultDTO resultado = SyncResultDTO.accepted("ev-1");

        when(eventosService.guardarEventosLote(anyList())).thenReturn(List.of(resultado));

        Response resp = resource.sincronizarEventos(List.of(evento));

        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        assertNotNull(resp.getEntity());

        @SuppressWarnings("unchecked")
        List<SyncResultDTO> list = (List<SyncResultDTO>) resp.getEntity();
        assertEquals(1, list.size());
        assertEquals("ACCEPTED", list.get(0).estado());
        assertEquals("ev-1", list.get(0).uuid());
        verify(eventosService).guardarEventosLote(List.of(evento));
    }

    @Test
    @DisplayName("POST /eventos/sync con lista nula o vacía debe responder 400 Bad Request")
    void testSincronizarEventosListaVacia() {
        Response respNulo = resource.sincronizarEventos(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respNulo.getStatus());

        Response respVacio = resource.sincronizarEventos(Collections.emptyList());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respVacio.getStatus());

        verify(eventosService, never()).guardarEventosLote(anyList());
    }

    @Test
    @DisplayName("GET /eventos/{guiaId} debe retornar 200 OK con la lista cronológica")
    void testListarEventosPorGuia() {
        EventoViajeDTO ev1 = new EventoViajeDTO(
                "ev-1", LocalDateTime.now(), TipoEvento.INICIO, -34.9, -56.1, 10L, null
        );
        when(eventosService.listarEventosPorGuia(10L)).thenReturn(List.of(ev1));

        Response resp = resource.listarEventos(10L);

        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        @SuppressWarnings("unchecked")
        List<EventoViajeDTO> dtos = (List<EventoViajeDTO>) resp.getEntity();
        assertEquals(1, dtos.size());
        assertEquals("ev-1", dtos.get(0).uuid());
    }

    @Test
    @DisplayName("GET /eventos/{guiaId} con error debe retornar 404 Not Found")
    void testListarEventosError() {
        when(eventosService.listarEventosPorGuia(999L)).thenThrow(new BusinessException("Guía no encontrada"));

        Response resp = resource.listarEventos(999L);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
    }
}
