package uy.edu.fing.grupo07.CargaUY.web.api;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.integration.TrackingClient;
import uy.edu.fing.grupo07.CargaUY.jms.TrackingMDB;
import uy.edu.fing.grupo07.CargaUY.jms.TrackingMessageProducer;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.service.EstadoService;
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

    @EJB
    private EstadoService estadoService;

    @Inject
    private BalanzaClient balanzaClient;

    @Inject
    private TrackingClient trackingClient;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("metrics")
    public JsonObject metricas() {
        control.admin(req != null ? (String) req.getAttribute("token") : null);
        long[] p = pdi != null ? pdi.metricas() : new long[]{0L, 0L, 0L};
        long[] b = balanzaClient != null ? balanzaClient.metricas() : new long[]{0L, 0L, 0L};
        long[] t = trackingClient != null ? trackingClient.metricas() : new long[]{0L, 0L, 0L};
        boolean balanzaAbierto = balanzaClient != null && balanzaClient.estaCircuitoAbierto();
        boolean trackingAbierto = trackingClient != null && trackingClient.estaCircuitoAbierto();
        return Json.createObjectBuilder()
                .add("solicitudes", CorrelacionFilter.solicitudes.sum())
                .add("errores", CorrelacionFilter.errores.sum())
                .add("errores4xx", CorrelacionFilter.errores4xx.sum())
                .add("errores5xx", CorrelacionFilter.errores5xx.sum())
                .add("duracionTotalNanos", CorrelacionFilter.nanos.sum())
                .add("pdiIntentos", p[0])
                .add("pdiFallas", p[1])
                .add("pdiRechazos", p[2])
                .add("balanzaLlamadas", b[0])
                .add("balanzaFallas", b[1])
                .add("balanzaRechazos", b[2])
                .add("balanzaCircuitoAbierto", balanzaAbierto)
                .add("trackingLlamadas", t[0])
                .add("trackingFallas", t[1])
                .add("trackingRechazos", t[2])
                .add("trackingCircuitoAbierto", trackingAbierto)
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
        control.admin(req != null ? (String) req.getAttribute("token") : null);
        boolean postgresOk = estadoService != null && estadoService.listo();
        boolean mongoOperativo = posicionNoSqlRepository != null && posicionNoSqlRepository.estaOperativo();
        boolean jmsOperativo = estadoService != null && estadoService.jmsListo();
        boolean balanzaAbierto = balanzaClient != null && balanzaClient.estaCircuitoAbierto();
        boolean trackingAbierto = trackingClient != null && trackingClient.estaCircuitoAbierto();
        String estadoGeneral = (!postgresOk || !jmsOperativo)
                ? "DOWN"
                : ((mongoOperativo && !balanzaAbierto && !trackingAbierto) ? "UP" : "DEGRADED");
        long pendientesAprox = Math.max(0L,
                TrackingMessageProducer.mensajesEncolados.get()
                        - TrackingMDB.eventosProcesados.get()
                        - TrackingMDB.eventosDuplicados.get());

        return Json.createObjectBuilder()
                .add("status", estadoGeneral)
                .add("postgres", postgresOk ? "UP" : "DOWN")
                .add("mongodb", mongoOperativo ? "UP" : "DEGRADED_FALLBACK_MEMORIA")
                .add("jmsTracking", jmsOperativo ? "UP" : "DOWN")
                .add("balanzaCircuito", balanzaAbierto ? "OPEN" : "CLOSED")
                .add("trackingCircuito", trackingAbierto ? "OPEN" : "CLOSED")
                .add("trackingColaPendientesAprox", pendientesAprox)
                .build();
    }
}
