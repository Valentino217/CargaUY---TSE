package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.security.SesionesService;

/** Registrado primero en web.xml para controlar también el acceso directo a las vistas. */
public class NacimientoPendienteFilter implements Filter {
    @EJB private SesionesService sesiones;
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var r=(HttpServletRequest)request; var s=(HttpServletResponse)response;
        String ruta=r.getServletPath();
        if (ruta.startsWith("/api/") || ruta.startsWith("/gub/")
                || ruta.contains("/jakarta.faces.resource/") || ruta.startsWith("/css/")
                || ruta.startsWith("/resources/") || ruta.startsWith("/js/") || ruta.equals("/error.xhtml") || ruta.equals("/login") || ruta.equals("/logout")) {
            chain.doFilter(r,s); return;
        }
        String token=WebSeguridad.token(r);
        if (token!=null) {
            boolean pendiente;
            try { pendiente=sesiones.nacimientoPendiente(token); }
            catch (ErrorAcceso e) {
                if(e.getEstado()==401){chain.doFilter(r,s);return;}
                throw e;
            }
            boolean formulario=ruta.equals("/completar-perfil.xhtml");
            if (pendiente && !formulario) {
                redirigir(r,s,"/completar-perfil.xhtml");return;
            }
            if (!pendiente && formulario && "GET".equals(r.getMethod())) {
                redirigir(r,s,"/portal/info-publica.xhtml");return;
            }
        }
        chain.doFilter(r,s);
    }
    private void redirigir(HttpServletRequest r,HttpServletResponse s,String ruta)throws IOException {
        s.setHeader("Cache-Control","no-store");
        String destino=r.getContextPath()+ruta;
        if ("partial/ajax".equals(r.getHeader("Faces-Request"))) {
            s.setContentType("text/xml;charset=UTF-8");
            s.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><partial-response><redirect url=\""
                    +destino+"\"/></partial-response>");
        } else s.sendRedirect(destino);
    }
}
