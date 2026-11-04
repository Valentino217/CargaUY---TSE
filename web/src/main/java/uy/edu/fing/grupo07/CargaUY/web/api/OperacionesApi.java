package uy.edu.fing.grupo07.CargaUY.web.api;

import jakarta.ejb.EJB;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.jms.TrackingMDB;
import uy.edu.fing.grupo07.CargaUY.jms.TrackingMessageProducer;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.web.CorrelacionFilter;

@Path("/ops")
@Produces(MediaType.APPLICATION_JSON)
public class OperacionesApi {

    @EJB
    private ControlAcceso control;

    @EJB
    private ClientePdi pdi;

    @EJB
    private PosicionNoSqlRepositoryLocal posicionNoSqlRepository;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("metrics")
    public JsonObject metricas() {
        control.admin((String) req.getAttribute("token"));
        long[] p = pdi.metricas();
        return Json.createObjectBuilder()
                .add("solicitudes", CorrelacionFilter.solicitudes.sum())
                .add("errores", CorrelacionFilter.errores.sum())
                .add("duracionTotalNanos", CorrelacionFilter.nanos.sum())
                .add("pdiIntentos", p[0])
                .add("pdiFallas", p[1])
                .add("pdiRechazos", p[2])
                .add("trackingEncolados", TrackingMessageProducer.mensajesEncolados.get())
                .add("trackingProcesados", TrackingMDB.eventosProcesados.get())
                .add("trackingDuplicados", TrackingMDB.eventosDuplicados.get())
                .add("trackingErrores", TrackingMDB.erroresProcesamiento.get())
                .add("trackingLatenciaTotalMs", TrackingMDB.latenciaTotalMs.get())
                .build();
    }

    @GET
    @Path("health")
    public JsonObject health() {
        boolean mongoOperativo = posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo();
        long pendientesAprox = Math.max(0L,
                TrackingMessageProducer.mensajesEncolados.get()
                        - TrackingMDB.eventosProcesados.get()
                        - TrackingMDB.eventosDuplicados.get());

        return Json.createObjectBuilder()
                .add("status", "UP")
                .add("postgres", "UP")
                .add("mongodb", mongoOperativo ? "UP" : "DEGRADED_FALLBACK_MEMORIA")
                .add("jmsTracking", "UP")
                .add("trackingColaPendientesAprox", pendientesAprox)
                .build();
    }
}
