package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.security.SesionesService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NacimientoPendienteFilterTest {
    private NacimientoPendienteFilter filtro;
    private SesionesService sesiones;
    private HttpServletRequest req;
    private HttpServletResponse res;
    private FilterChain chain;
    @BeforeEach void preparar()throws Exception {
        filtro=new NacimientoPendienteFilter();sesiones=mock(SesionesService.class);
        var f=NacimientoPendienteFilter.class.getDeclaredField("sesiones");f.setAccessible(true);f.set(filtro,sesiones);
        req=mock(HttpServletRequest.class);res=mock(HttpServletResponse.class);chain=mock(FilterChain.class);
        var ses=mock(HttpSession.class);
        when(req.getSession(false)).thenReturn(ses);when(ses.getAttribute("token")).thenReturn("token");
        when(req.getContextPath()).thenReturn("/CargaUY-web");when(req.getMethod()).thenReturn("GET");
    }
    @Test void bloqueaAccesoDirectoAInicioYPortalSiFaltaFecha()throws Exception {
        when(sesiones.nacimientoPendiente("token")).thenReturn(true);
        for(String ruta:new String[]{"/index.xhtml","/portal/inicio.xhtml","/empresa/perfil.xhtml","/app/empresa"}) {
            when(req.getServletPath()).thenReturn(ruta);filtro.doFilter(req,res,chain);
        }
        verify(res,times(4)).sendRedirect("/CargaUY-web/completar-perfil.xhtml");verifyNoInteractions(chain);
    }
    @Test void permiteGuardarEnElFormularioPendiente()throws Exception {
        when(req.getServletPath()).thenReturn("/completar-perfil.xhtml");
        when(req.getMethod()).thenReturn("POST");when(sesiones.nacimientoPendiente("token")).thenReturn(true);
        filtro.doFilter(req,res,chain);verify(chain).doFilter(req,res);
    }
    @Test void usuarioCompletoNoVeLaVentana()throws Exception {
        when(req.getServletPath()).thenReturn("/index.xhtml");
        filtro.doFilter(req,res,chain);verify(chain).doFilter(req,res);verify(res,never()).sendRedirect(anyString());
    }
    @Test void formularioDirectoConFechaCompletaVuelveAInicio()throws Exception {
        when(req.getServletPath()).thenReturn("/completar-perfil.xhtml");
        filtro.doFilter(req,res,chain);verify(res).sendRedirect("/CargaUY-web/index.xhtml");
    }
    @Test void visitanteSinSesionContinuaHaciaLosFiltrosDeAutenticacion()throws Exception {
        when(req.getServletPath()).thenReturn("/portal/inicio.xhtml");when(req.getSession(false)).thenReturn(null);
        filtro.doFilter(req,res,chain);verifyNoInteractions(sesiones);verify(chain).doFilter(req,res);
    }
    @Test void permiteCallbackYRecursosSinRedirigir()throws Exception {
        for(String ruta:new String[]{"/gub/callback","/jakarta.faces.resource/primefaces.js","/logout","/css/plataforma.css"}){
            when(req.getServletPath()).thenReturn(ruta);filtro.doFilter(req,res,chain);
        }
        verifyNoInteractions(sesiones);verify(chain,times(4)).doFilter(req,res);
    }
    @Test void ajaxRecibeRedireccionJSFEnLugarDeHtml()throws Exception {
        when(req.getServletPath()).thenReturn("/empresa/perfil.xhtml");
        when(req.getHeader("Faces-Request")).thenReturn("partial/ajax");
        when(sesiones.nacimientoPendiente("token")).thenReturn(true);
        var salida=new StringWriter();when(res.getWriter()).thenReturn(new PrintWriter(salida));
        filtro.doFilter(req,res,chain);
        assertTrue(salida.toString().contains("<redirect url=\"/CargaUY-web/completar-perfil.xhtml\"/>"));
        verifyNoInteractions(chain);
    }
}
