package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.service.*;

@Path("/") @Produces(MediaType.APPLICATION_JSON)
public class ApiP1 {
    @EJB private AutenticacionService auth;
    @EJB private GestionPerfilesService perfiles;
    @EJB private EstadoService estado;
    @Context private ContainerRequestContext request;
    private String token(){return (String)request.getProperty("token");}
    @POST @Path("auth/login") @Consumes(MediaType.APPLICATION_JSON)
    public Response login(JsonObject datos) {
        try {
            var l=auth.login(datos.getString("uid",""),datos.getString("password",""));
            return Response.ok(Json.createObjectBuilder().add("token",l.token())
                .add("vence",l.vence().toString()).build()).header("Cache-Control","no-store").build();
        } catch(ErrorAcceso e){return Response.status(e.getEstado()).entity(
            Json.createObjectBuilder().add("error",e.getMessage()).build()).build();}
    }
    @POST @Path("auth/gub/iniciar") @Consumes(MediaType.APPLICATION_JSON)
    public JsonObject prepararMovil(JsonObject entrada){
        var i=auth.prepararMovil(entrada.getString("challenge",""));
        return Json.createObjectBuilder().add("state",i.state()).add("nonce",i.nonce()).add("url",i.url()).build();
    }
    @POST @Path("auth/gub/terminar") @Consumes(MediaType.APPLICATION_JSON)
    public Response terminarMovil(JsonObject entrada){
        var l=auth.loginMovil(entrada.getString("code",""),entrada.getString("state",""),entrada.getString("verifier",""));
        return Response.ok(Json.createObjectBuilder().add("token",l.token()).add("vence",l.vence().toString()).build())
            .header("Cache-Control","no-store").build();
    }
    @GET @Path("auth/me")
    public JsonObject me(){
        Acceso a=auth.validarToken(token()); JsonArrayBuilder roles=Json.createArrayBuilder();
        a.roles().forEach(r->roles.add(r.name()));
        return Json.createObjectBuilder().add("id",a.id()).add("nombre",a.nombre())
            .add("roles",roles).build();
    }
    @POST @Path("auth/logout")
    public Response salir(){auth.logout(token());return Response.noContent().build();}
    @GET @Path("empresas")
    public JsonArray empresas(@QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tam") @DefaultValue("20") int tam) {
        JsonArrayBuilder lista=Json.createArrayBuilder();
        perfiles.listarEmpresas(token(),pagina,tam).forEach(e->lista.add(Json.createObjectBuilder()
            .add("numero",e.numero()).add("nombre",e.nombrePublico()).add("version",e.version())));
        return lista.build();
    }
    @GET @Path("public/ready")
    public Response ready(){boolean ok=estado.listo();return Response.status(ok?200:503)
        .entity(Json.createObjectBuilder().add("estado",ok?"UP":"DOWN").build()).build();}
    @GET @Path("public/health")
    public JsonObject health(){return Json.createObjectBuilder().add("estado","UP").build();}
}