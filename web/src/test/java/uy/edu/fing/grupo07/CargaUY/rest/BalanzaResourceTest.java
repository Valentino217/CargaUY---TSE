package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - BalanzaResource (REST API de Balanzas)")
class BalanzaResourceTest {

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    private BalanzaResource resource;

    @BeforeEach
    void setUp() {
        resource = new BalanzaResource(balanzaService);
    }

    @Test
    @DisplayName("POST /pesaje con datos válidos retorna 201 Created")
    void testRegistrarPesajeExitoso() {
        PesadaBalanzaDTO request = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.now(), 32000, "123456", null);
        PesadaBalanzaDTO responseDto = new PesadaBalanzaDTO(1, LocalDate.now(), LocalTime.now(), 32000, "123456", 10L);

        when(balanzaService.registrarPesada(request)).thenReturn(responseDto);

        Response response = resource.registrarPesaje(request);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(responseDto, response.getEntity());
    }

    @Test
    @DisplayName("POST /pesaje con datos nulos responde 400 Bad Request")
    void testRegistrarPesajeNulo() {
        Response response = resource.registrarPesaje(null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("POST /pesaje cuando servicio lanza BusinessException responde 400 Bad Request")
    void testRegistrarPesajeErrorNegocio() {
        PesadaBalanzaDTO request = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.now(), 32000, "999", null);
        when(balanzaService.registrarPesada(request)).thenThrow(new BusinessException("Sin guía activa"));

        Response response = resource.registrarPesaje(request);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("POST /mock-sync invoca sincronización del mock y responde 201 Created")
    void testSincronizarDesdeMockExitoso() {
        PesadaBalanzaDTO responseDto = new PesadaBalanzaDTO(2, LocalDate.now(), LocalTime.now(), 35000, "123456", 10L);
        when(balanzaService.registrarPesadaDesdeMock()).thenReturn(responseDto);

        Response response = resource.sincronizarDesdeMock();

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(responseDto, response.getEntity());
    }

    @Test
    @DisplayName("POST /mock-sync ante fallo de conexión responde 400 Bad Request con mensaje amigable")
    void testSincronizarDesdeMockFallo() {
        when(balanzaService.registrarPesadaDesdeMock()).thenThrow(new BusinessException("Fallo de conexión"));

        Response response = resource.sincronizarDesdeMock();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("GET /guia/{guiaId} retorna 200 OK con lista de pesadas")
    void testListarPesadasPorGuiaExitoso() {
        PesadaBalanzaDTO p = new PesadaBalanzaDTO(1, LocalDate.now(), LocalTime.now(), 30000, "123", 10L);
        when(balanzaService.listarPesadasPorGuia(10L)).thenReturn(List.of(p));

        Response response = resource.listarPesadasPorGuia(10L);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        List<PesadaBalanzaDTO> lista = (List<PesadaBalanzaDTO>) response.getEntity();
        assertEquals(1, lista.size());
    }

    @Test
    @DisplayName("GET /guia/{guiaId} con error responde 404 Not Found")
    void testListarPesadasPorGuiaError() {
        when(balanzaService.listarPesadasPorGuia(99L)).thenThrow(new BusinessException("Guía no encontrada"));

        Response response = resource.listarPesadasPorGuia(99L);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
