package uy.edu.fing.grupo07.CargaUY.web.api;
import java.io.IOException;
import java.security.Principal;
import jakarta.annotation.Priority;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import uy.edu.fing.grupo07.CargaUY.security.*;

@Provider @PreMatching @Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {
    @EJB private AutenticacionService auth;
    @Context private jakarta.servlet.http.HttpServletRequest http;
    @Override public void filter(ContainerRequestContext r) throws IOException {
        if(!uy.edu.fing.grupo07.CargaUY.web.WebSeguridad.transporte(http)) { r.abortWith(Response.status(403).build()); return; }
        String p=r.getUriInfo().getPath();
        if((p.equals("auth/login")||p.equals("auth/gub/iniciar")||p.equals("auth/gub/terminar"))&&r.getMethod().equals("POST")
                ||(p.equals("public/health")||p.equals("public/ready"))&&r.getMethod().equals("GET")) return;
        String h=r.getHeaderString("Authorization");
        String token=h!=null&&h.startsWith("Bearer ")?h.substring(7):null;
        try {
            Acceso actor=auth.validarToken(token); r.setProperty("token",token);
            r.setSecurityContext(new SecurityContext() {
                public Principal getUserPrincipal() { return () -> Integer.toString(actor.id()); }
                public boolean isUserInRole(String rol) {
                    try{return actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.valueOf(rol));}
                    catch(IllegalArgumentException e){return false;}
                }
                public boolean isSecure(){return http.isSecure();}
                public String getAuthenticationScheme(){return "Bearer";}
            });
        } catch(ErrorAcceso e) {
            r.abortWith(Response.status(e.getEstado()).header("WWW-Authenticate","Bearer")
                .entity(java.util.Map.of("error",e.getMessage())).build());
        }
    }
}
