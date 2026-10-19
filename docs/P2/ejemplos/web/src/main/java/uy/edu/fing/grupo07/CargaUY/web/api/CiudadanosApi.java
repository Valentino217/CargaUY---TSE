package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.service.ConsultaCiudadanoService;

@Path("/ciudadanos") @Produces(MediaType.APPLICATION_JSON)
public class CiudadanosApi {
    @EJB private ConsultaCiudadanoService pdi;
    @Context private ContainerRequestContext req;
    @Context private jakarta.servlet.http.HttpServletRequest http;
    @GET @Path("{ci}/pdi")
    public JsonObject consultar(@PathParam("ci")int ci){
        var r=pdi.consultar((String)req.getProperty("token"),ci,(String)http.getAttribute("correlacion"));
        var json=Json.createObjectBuilder().add("estado",r.estado().name()).add("mensaje",r.mensaje());
        if(r.ci()!=null)json.add("ci",r.ci());
        if(r.nombre()!=null)json.add("nombre",r.nombre()).add("apellido",r.apellido())
            .add("fechaNacimiento",r.fechaNacimiento().toString()).add("vigente",r.vigente());
        return json.build();
    }
}
