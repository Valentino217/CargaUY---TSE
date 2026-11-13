package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.integration.TraceContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - CorrelacionFilter (Trazado W3C y contadores 4xx/5xx)")
class CorrelacionFilterTest {

    @Test
    @DisplayName("Propaga correlación y traceparent, y discrimina errores 4xx vs 5xx")
    void testPropagaTraceparentYCuentaErrores4xx5xx() throws Exception {
        CorrelacionFilter filtro = new CorrelacionFilter();
        long solInicial = CorrelacionFilter.solicitudes.sum();
        long errInicial = CorrelacionFilter.errores.sum();
        long e4Inicial = CorrelacionFilter.errores4xx.sum();
        long e5Inicial = CorrelacionFilter.errores5xx.sum();

        String corrUuid = "123e4567-e89b-12d3-a456-426614174000";
        HttpServletRequest req404 = mock(HttpServletRequest.class);
        HttpServletResponse res404 = mock(HttpServletResponse.class);
        when(req404.getHeader(TraceContext.HEADER_CORRELACION)).thenReturn(corrUuid);
        when(req404.getMethod()).thenReturn("GET");
        when(res404.getStatus()).thenReturn(404);
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(req404, res404, chain);

        verify(req404).setAttribute(eq("correlacion"), eq(corrUuid));
        verify(req404).setAttribute(eq("traceparent"), anyString());
        verify(res404).setHeader(eq(TraceContext.HEADER_CORRELACION), eq(corrUuid));
        verify(res404).setHeader(eq(TraceContext.HEADER_TRACEPARENT), anyString());
        verify(chain).doFilter(req404, res404);

        HttpServletRequest req500 = mock(HttpServletRequest.class);
        HttpServletResponse res500 = mock(HttpServletResponse.class);
        when(req500.getMethod()).thenReturn("POST");
        when(res500.getStatus()).thenReturn(503);
        filtro.doFilter(req500, res500, chain);

        HttpServletRequest req200 = mock(HttpServletRequest.class);
        HttpServletResponse res200 = mock(HttpServletResponse.class);
        when(req200.getMethod()).thenReturn("GET");
        when(res200.getStatus()).thenReturn(200);
        filtro.doFilter(req200, res200, chain);

        assertEquals(solInicial + 3, CorrelacionFilter.solicitudes.sum());
        assertEquals(errInicial + 2, CorrelacionFilter.errores.sum());
        assertEquals(e4Inicial + 1, CorrelacionFilter.errores4xx.sum());
        assertEquals(e5Inicial + 1, CorrelacionFilter.errores5xx.sum());
        assertTrue(CorrelacionFilter.nanos.sum() >= 0);
        assertNull(TraceContext.obtenerCorrelacion());
    }
}
