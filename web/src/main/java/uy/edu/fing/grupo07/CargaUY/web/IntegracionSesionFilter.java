package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

/** Las pantallas JSF de viajes comparten el login y la sesión de P1. */
@WebFilter(urlPatterns={"*.xhtml","/index.jsp","/DetalleViajeServlet","/detalle-viaje","/detalleViaje.jsp"})
public class IntegracionSesionFilter implements Filter {
    @EJB private AutenticacionService auth;
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        var r=(HttpServletRequest)request; var s=(HttpServletResponse)response;
        if(r.getRequestURI().contains("/jakarta.faces.resource/")) { chain.doFilter(r,s); return; }
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        // PrimeFaces genera scripts y estilos inline para sus componentes JSF.
        s.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self' 'unsafe-inline'; "
            +"style-src 'self' 'unsafe-inline'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'");
        try {
            Acceso actor=auth.validarToken(WebSeguridad.token(r));
            String p=r.getServletPath();
            boolean gestion=p.equals("/GuiaDeViajeJSF.xhtml")||p.equals("/VehiculoJSF.xhtml")
                ||p.equals("/ITV.xhtml")||p.equals("/PNC.xhtml");
            // Los módulos de fiscalización aún tienen operaciones globales; se reservan a operadores internos.
            boolean permitido=p.equals("/index.xhtml")||p.equals("/index.jsp")||actor.tiene(TipoUsuario.ADMINISTRADOR)
                ||gestion&&actor.tiene(TipoUsuario.CIUDADANO)
                ||!gestion&&(actor.tiene(TipoUsuario.FUNCIONARIO)
                    ||actor.tiene(TipoUsuario.AUTORIDAD)&&(r.getMethod().equals("GET")||r.getMethod().equals("HEAD")));
            if(!permitido) throw new ErrorAcceso(403,"Sin permisos para esta pantalla");
            r.setAttribute("actor",actor);
            chain.doFilter(r,s);
        } catch(ErrorAcceso e) {
            if(e.getEstado()==401) s.sendRedirect(r.getContextPath()+"/login");
            else WebSeguridad.error(s,e.getEstado(),e.getMessage());
        }
    }
}
