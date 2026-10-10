package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - DetalleViajeServlet")
class DetalleViajeServletTest {

    @Mock
    private GestionEventosServiceLocal eventosService;

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private RequestDispatcher dispatcher;

    private DetalleViajeServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new DetalleViajeServlet(eventosService, balanzaService);
        when(request.getRequestDispatcher("/detalleViaje.jsp")).thenReturn(dispatcher);
    }

    @Test
    @DisplayName("doGet sin parámetro guiaId simplemente despacha al JSP sin invocar servicios")
    void testDoGetSinParametro() throws ServletException, IOException {
        when(request.getParameter("guiaId")).thenReturn(null);

        servlet.doGet(request, response);

        verifyNoInteractions(eventosService);
        verifyNoInteractions(balanzaService);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doGet con guiaId válido consulta eventos y balanzas, seteando atributos")
    void testDoGetConGuiaIdValido() throws ServletException, IOException {
        when(request.getParameter("guiaId")).thenReturn("5");

        EventoViajeDTO ev = new EventoViajeDTO("u1", LocalDateTime.now(), TipoEvento.CARGA, -34.0, -56.0, 5L, null);
        PesadaBalanzaDTO pb = new PesadaBalanzaDTO(1, LocalDate.now(), LocalTime.now(), 32000, "123", 5L);

        when(eventosService.listarEventosPorGuia(5L)).thenReturn(List.of(ev));
        when(balanzaService.listarPesadasPorGuia(5L)).thenReturn(List.of(pb));

        servlet.doGet(request, response);

        verify(eventosService).listarEventosPorGuia(5L);
        verify(balanzaService).listarPesadasPorGuia(5L);
        verify(request).setAttribute(eq("guiaId"), eq(5L));
        verify(request).setAttribute(eq("eventos"), eq(List.of(ev)));
        verify(request).setAttribute(eq("pesadas"), eq(List.of(pb)));
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doGet con guiaId no numérico setea atributo de error")
    void testDoGetConGuiaIdNoNumerico() throws ServletException, IOException {
        when(request.getParameter("guiaId")).thenReturn("abc");

        servlet.doGet(request, response);

        verify(request).setAttribute(eq("error"), eq("El identificador de la guía debe ser numérico."));
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doGet cuando el servicio lanza excepción captura el error y setea atributo")
    void testDoGetConErrorEnServicio() throws ServletException, IOException {
        when(request.getParameter("guiaId")).thenReturn("99");
        when(eventosService.listarEventosPorGuia(99L)).thenThrow(new BusinessException("Guía no encontrada"));

        servlet.doGet(request, response);

        verify(request).setAttribute(eq("error"), eq("Error al consultar viaje: Guía no encontrada"));
        verify(dispatcher).forward(request, response);
    }
}
