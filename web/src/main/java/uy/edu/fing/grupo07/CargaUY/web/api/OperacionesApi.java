package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.json.*;
import uy.edu.fing.grupo07.CargaUY.web.CorrelacionFilter;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.service.ConsultaCiudadanoService;

@Path("/ops") @Produces(MediaType.APPLICATION_JSON)
public class OperacionesApi {
    @EJB private ControlAcceso control;
    @EJB private ClientePdi pdi;
    @Context private HttpServletRequest req;
    @GET @Path("metrics")
    public JsonObject metricas(){
        control.admin((String)req.getAttribute("token"));long[] p=pdi.metricas();
        return Json.createObjectBuilder().add("solicitudes",CorrelacionFilter.solicitudes.sum())
            .add("errores",CorrelacionFilter.errores.sum()).add("duracionTotalNanos",CorrelacionFilter.nanos.sum())
            .add("pdiIntentos",p[0]).add("pdiFallas",p[1]).add("pdiRechazos",p[2]).build();
    }
}