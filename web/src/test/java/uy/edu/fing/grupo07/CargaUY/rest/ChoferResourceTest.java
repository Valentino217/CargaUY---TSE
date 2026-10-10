package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.time.LocalDate;
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

    @Mock
    private uy.edu.fing.grupo07.CargaUY.jms.TrackingProducerLocal trackingProducer;

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
    @DisplayName("POST /eventos/sync con trackingProducer activo debe encolar en JMS y verificar deduplicación")
    void testSincronizarEventosAsyncJMS() {
        ChoferResource asyncResource = new ChoferResource(eventosService, trackingProducer);

        EventoViajeDTO evNuevo = new EventoViajeDTO("ev-nuevo", LocalDateTime.now(), TipoEvento.CARGA, -34.9, -56.1, 1L, null);
        EventoViajeDTO evDup = new EventoViajeDTO("ev-dup", LocalDateTime.now(), TipoEvento.DESCARGA, -34.9, -56.1, 1L, null);

        when(eventosService.existeEvento("ev-nuevo")).thenReturn(false);
        when(eventosService.existeEvento("ev-dup")).thenReturn(true);

        Response resp = asyncResource.sincronizarEventos(List.of(evNuevo, evDup));

        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        @SuppressWarnings("unchecked")
        List<SyncResultDTO> list = (List<SyncResultDTO>) resp.getEntity();
        assertEquals(2, list.size());
        assertEquals("ACCEPTED", list.get(0).estado());
        assertEquals("DUPLICATE", list.get(1).estado());

        verify(trackingProducer).enviarEvento(evNuevo);
        verify(trackingProducer, never()).enviarEvento(evDup);
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

    @Test
    @DisplayName("GET /guia-asignada debe retornar 200 OK cuando existe guía para el chofer")
    void testObtenerGuiaAsignadaExitoso() {
        GuiaResumenDTO guia = new GuiaResumenDTO(
                1L, LocalDate.now(), "Montevideo", "Rivera", "Forestal", 40.0f,
                TipoEstado.EN_CURSO, 1, 1234, "Volvo FH", 100, "Trans SA", 5, "Carlos"
        );
        when(eventosService.obtenerGuiaAsignadaChofer(5, null)).thenReturn(guia);

        Response resp = resource.obtenerGuiaAsignada(5, null);

        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
        assertEquals(guia, resp.getEntity());
    }

    @Test
    @DisplayName("GET /guia-asignada sin parámetros debe retornar 400 Bad Request")
    void testObtenerGuiaAsignadaSinParametros() {
        Response resp = resource.obtenerGuiaAsignada(null, null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), resp.getStatus());
    }

    @Test
    @DisplayName("GET /guia-asignada cuando no existe guía debe retornar 404 Not Found")
    void testObtenerGuiaAsignadaNoEncontrada() {
        when(eventosService.obtenerGuiaAsignadaChofer(null, 12345678)).thenReturn(null);

        Response resp = resource.obtenerGuiaAsignada(null, 12345678);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
    }

    @Test
    @DisplayName("POST /incidente con datos válidos debe retornar 201 Created")
    void testReportarIncidenteExitoso() {
        ReportarIncidenteDTO dto = new ReportarIncidenteDTO(
                "inc-1", 1L, LocalDateTime.now(), -34.0, -56.0, "/fotos/1.png", "Rueda pinchada"
        );
        EventoViajeDTO creado = new EventoViajeDTO(
                "inc-1", LocalDateTime.now(), TipoEvento.INCIDENTE, -34.0, -56.0, 1L,
                new IncidenteDTO("/fotos/1.png", "Rueda pinchada")
        );
        when(eventosService.reportarIncidente(dto)).thenReturn(creado);

        Response resp = resource.reportarIncidente(dto);

        assertEquals(Response.Status.CREATED.getStatusCode(), resp.getStatus());
        assertEquals(creado, resp.getEntity());
    }

    @Test
    @DisplayName("POST /incidente con datos inválidos debe retornar 400 Bad Request")
    void testReportarIncidenteInvalido() {
        Response respNulo = resource.reportarIncidente(null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respNulo.getStatus());

        ReportarIncidenteDTO sinGuia = new ReportarIncidenteDTO(
                "inc-2", null, LocalDateTime.now(), -34.0, -56.0, null, "Falla"
        );
        Response respSinGuia = resource.reportarIncidente(sinGuia);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respSinGuia.getStatus());

        ReportarIncidenteDTO sinDesc = new ReportarIncidenteDTO(
                "inc-3", 1L, LocalDateTime.now(), -34.0, -56.0, null, "  "
        );
        Response respSinDesc = resource.reportarIncidente(sinDesc);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), respSinDesc.getStatus());
    }

    @Test
    @DisplayName("POST /incidente cuando el servicio lanza BusinessException debe retornar 400 Bad Request")
    void testReportarIncidenteBusinessException() {
        ReportarIncidenteDTO dto = new ReportarIncidenteDTO(
                "inc-dup", 1L, LocalDateTime.now(), -34.0, -56.0, null, "Problema mecánico"
        );
        when(eventosService.reportarIncidente(dto)).thenThrow(new BusinessException("UUID ya registrado"));

        Response resp = resource.reportarIncidente(dto);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), resp.getStatus());
    }
}
