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
    @EJB private ControlAcceso control;
    @Context private jakarta.servlet.http.HttpServletRequest http;
    @Override public void filter(ContainerRequestContext r) throws IOException {
        if(!uy.edu.fing.grupo07.CargaUY.web.WebSeguridad.transporte(http)) { r.abortWith(Response.status(403).build()); return; }
        if("OPTIONS".equalsIgnoreCase(r.getMethod())) return;
        String p=r.getUriInfo().getPath().replaceFirst("^/","");
        if((p.equals("auth/login")||p.equals("auth/gub/iniciar")||p.equals("auth/gub/terminar")||p.equals("auth/dev-chofer"))&&r.getMethod().equals("POST")
                ||(p.equals("public/health")||p.equals("public/ready"))&&r.getMethod().equals("GET")) return;
        if(p.equals("chofer/eventos/faas")&&r.getMethod().equals("POST")&&validarSecretoFaas(r)) {
            if(r.getLength()>1_048_576) { r.abortWith(Response.status(413).type(MediaType.APPLICATION_JSON_TYPE).entity(java.util.Map.of("error","Lote demasiado grande")).build()); return; }
            return;
        }
        String h=r.getHeaderString("Authorization");
        String token=h!=null&&h.startsWith("Bearer ")?h.substring(7):null;
        try {
            Acceso actor=auth.validarToken(token);
            validarP3(r,p,token,actor);
            r.setProperty("token",token);
            http.setAttribute("token",token);
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
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(java.util.Map.of("error",e.getMessage())).build());
        }
    }
    private boolean validarSecretoFaas(ContainerRequestContext r) {
        String faasHeader=r.getHeaderString("X-FaaS-Secret");
        if(faasHeader==null||faasHeader.isBlank()) return false;
        String secret=System.getProperty("cargauy.faas.secret");
        if(secret==null||secret.isBlank()) secret=System.getenv("FAAS_SHARED_SECRET");
        return secret!=null&&!secret.isBlank()&&secret.trim().equals(faasHeader.trim());
    }
    private void validarP3(ContainerRequestContext r,String p,String token,Acceso actor) throws IOException {
        if(p.startsWith("balanza/")) {
            if(!actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)
                && !actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO)
                && !(r.getMethod().equals("GET")&&actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD)))
                throw new ErrorAcceso(403,"Se requiere un operador de fiscalización");
        }
        if(!p.startsWith("chofer/")) return;
        boolean esOperadorFiscalizacion = actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)
                || actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        if(p.equals("chofer/eventos/faas") && esOperadorFiscalizacion) return;
        var parametros=r.getUriInfo().getQueryParameters();
        try {
            if (!(esOperadorFiscalizacion && p.startsWith("chofer/eventos/"))) {
                control.consultarChofer(token,parametros.getFirst("choferId")==null?null:Integer.valueOf(parametros.getFirst("choferId")),
                    parametros.getFirst("ci")==null?null:Integer.valueOf(parametros.getFirst("ci")));
            }
            if(p.startsWith("chofer/eventos/")&&!p.equals("chofer/eventos/sync")&&!p.equals("chofer/eventos/faas")) {
                if (!esOperadorFiscalizacion) {
                    control.guiaChofer(token,Long.valueOf(p.substring("chofer/eventos/".length())));
                } else {
                    Long.valueOf(p.substring("chofer/eventos/".length()));
                }
            }
            if(r.getMethod().equals("POST")&&(p.equals("chofer/eventos/sync")||p.equals("chofer/eventos/faas")||p.equals("chofer/incidente"))) {
                byte[] body=r.getEntityStream().readNBytes(1_048_577);
                if(body.length>1_048_576) throw new ErrorAcceso(413,"Lote demasiado grande");
                r.setEntityStream(new java.io.ByteArrayInputStream(body));
                boolean admiteSinGuiaPorMatricula = p.equals("chofer/eventos/sync") || p.equals("chofer/eventos/faas");
                try(var reader=jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(body))) {
                    var json=reader.readValue();
                    if(json instanceof jakarta.json.JsonArray lote) {
                        for(var elemento:lote) {
                            if(!(elemento instanceof jakarta.json.JsonObject evento)) throw new ErrorAcceso(400,"Evento inválido");
                            validarEventoJson(token, actor, evento, admiteSinGuiaPorMatricula);
                        }
                    } else if(json instanceof jakarta.json.JsonObject evento) {
                        validarEventoJson(token, actor, evento, admiteSinGuiaPorMatricula);
                    } else throw new ErrorAcceso(400,"Solicitud inválida");
                }
            }
        } catch(NumberFormatException|jakarta.json.JsonException|ClassCastException|ArithmeticException e) {
            throw new ErrorAcceso(400,"Identificador o JSON inválido");
        }
    }
    private void validarEventoJson(String token, Acceso actor, jakarta.json.JsonObject evento, boolean admiteSinGuiaPorMatricula) {
        var id = (evento.containsKey("guiaId") && !evento.isNull("guiaId")) ? evento.getJsonNumber("guiaId") : null;
        boolean tieneMatricula = evento.containsKey("matricula") && !evento.isNull("matricula")
                && !evento.getString("matricula").isBlank();
        boolean esOperadorFiscalizacion = actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)
                || actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        if (admiteSinGuiaPorMatricula && id == null && tieneMatricula
                && (esOperadorFiscalizacion || actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CHOFER))) {
            return;
        }
        if (esOperadorFiscalizacion && id != null) {
            id.longValueExact();
            return;
        }
        try {
            control.guiaChofer(token, id == null ? null : id.longValueExact());
        } catch (ErrorAcceso e) {
            if (e.getEstado() == 404 && admiteSinGuiaPorMatricula && tieneMatricula) {
                return;
            }
            throw e;
        }
    }
}
