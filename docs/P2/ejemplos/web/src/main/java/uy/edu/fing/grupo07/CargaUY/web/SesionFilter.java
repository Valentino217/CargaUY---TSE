package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@WebFilter(urlPatterns="/app/*", dispatcherTypes=DispatcherType.REQUEST)
public class SesionFilter implements Filter {
    @EJB private AutenticacionService auth;
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException,ServletException {
        var r=(HttpServletRequest)request; var s=(HttpServletResponse)response;
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        try {
            Acceso actor=auth.validarToken(WebSeguridad.token(r));
            if(r.getServletPath().equals("/app/admin")&&!actor.tiene(TipoUsuario.ADMINISTRADOR))
                throw new ErrorAcceso(403,"Se requiere administrador");
            if(!r.getMethod().equals("GET")&&!r.getMethod().equals("HEAD")&&!WebSeguridad.csrf(r))
                throw new ErrorAcceso(403,"Formulario vencido: recargá la página");
            r.setAttribute("actor",actor);
            chain.doFilter(r,s);
        } catch(ErrorAcceso e) {
            if(e.getEstado()==401) {
                r.getSession().invalidate(); s.sendRedirect(r.getContextPath()+"/login");
            } else WebSeguridad.error(s,e.getEstado(),e.getMessage());
        }
    }
}
