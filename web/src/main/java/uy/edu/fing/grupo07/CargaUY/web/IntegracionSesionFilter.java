package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

/** Las pantallas JSF de viajes y los módulos por rol comparten el login y la sesión de P1. */
@WebFilter(urlPatterns={"*.xhtml","/DetalleViajeServlet","/detalle-viaje"})
public class IntegracionSesionFilter implements Filter {
    @EJB private AutenticacionService auth;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var r = (HttpServletRequest) request;
        var s = (HttpServletResponse) response;
        if (r.getRequestURI().contains("/jakarta.faces.resource/")) {
            chain.doFilter(r, s);
            return;
        }
        if (!WebSeguridad.transporte(r)) {
            s.sendError(403);
            return;
        }
        WebSeguridad.preparar(r, s);
        // PrimeFaces genera scripts y estilos inline para sus componentes JSF.
        s.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; form-action 'self'; frame-ancestors 'none'");

        String p = r.getServletPath();

        // 1. Vistas públicas del Portal (/portal/*) permiten acceso anónimo o autenticado
        if (p != null && p.startsWith("/portal/")) {
            String token = WebSeguridad.token(r);
            if (token != null && !token.isBlank()) {
                try {
                    Acceso actor = auth.validarToken(token);
                    r.setAttribute("actor", actor);
                } catch (Exception ignored) {
                    // Si el token expiró, continúa como visitante anónimo en el portal público
                }
            }
            chain.doFilter(r, s);
            return;
        }

        try {
            Acceso actor = auth.validarToken(WebSeguridad.token(r));
            boolean permitido = evaluarPermisoRuta(p, r.getMethod(), actor);
            if (!permitido) {
                throw new ErrorAcceso(403, "Sin permisos para esta pantalla");
            }
            r.setAttribute("actor", actor);
            chain.doFilter(r, s);
        } catch (ErrorAcceso e) {
            if (e.getEstado() == 401) {
                s.sendRedirect(r.getContextPath() + "/login");
            } else {
                WebSeguridad.error(s, e.getEstado(), e.getMessage());
            }
        }
    }

    private boolean evaluarPermisoRuta(String p, String metodo, Acceso actor) {
        if (actor.tiene(TipoUsuario.ADMINISTRADOR)) {
            return true;
        }
        if (p == null || p.equals("/index.xhtml")) {
            return true;
        }
        if (p.startsWith("/admin/")) {
            return actor.tiene(TipoUsuario.ADMINISTRADOR);
        }
        if (p.startsWith("/backoffice/")) {
            return actor.tiene(TipoUsuario.AUTORIDAD) || actor.tiene(TipoUsuario.FUNCIONARIO);
        }
        if (p.startsWith("/funcionario/")) {
            return actor.tiene(TipoUsuario.FUNCIONARIO)
                || (actor.tiene(TipoUsuario.AUTORIDAD) && ("GET".equals(metodo) || "HEAD".equals(metodo)));
        }
        if (p.startsWith("/empresa/")) {
            return actor.tiene(TipoUsuario.CIUDADANO) || actor.tiene(TipoUsuario.FUNCIONARIO);
        }
        boolean gestion = p.equals("/GuiaDeViajeJSF.xhtml") || p.equals("/VehiculoJSF.xhtml")
            || p.equals("/ITV.xhtml") || p.equals("/PNC.xhtml");
        return (gestion && actor.tiene(TipoUsuario.CIUDADANO))
            || (!gestion && (actor.tiene(TipoUsuario.FUNCIONARIO)
                || (actor.tiene(TipoUsuario.AUTORIDAD) && ("GET".equals(metodo) || "HEAD".equals(metodo)))));
    }
}
