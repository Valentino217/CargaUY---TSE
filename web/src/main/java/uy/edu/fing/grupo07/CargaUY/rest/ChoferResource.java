package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.SyncResultDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.util.Collections;
import java.util.List;

/**
 * Endpoints REST para operaciones del chofer y sincronización de eventos de viaje.
 */
@Path("/chofer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChoferResource {

    @EJB
    private GestionEventosServiceLocal eventosService;

    public ChoferResource() {
    }

    // Constructor para tests con mock
    ChoferResource(GestionEventosServiceLocal eventosService) {
        this.eventosService = eventosService;
    }

    /**
     * Sincroniza un lote de eventos registrados offline por el chofer.
     * Cada evento cuenta con un UUID para garantizar idempotencia.
     * Retorna una lista con el estado individual de cada evento (ACCEPTED, DUPLICATE o ERROR).
     */
    @POST
    @Path("/eventos/sync")
    public Response sincronizarEventos(List<EventoViajeDTO> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.emptyList())
                    .build();
        }

        List<SyncResultDTO> resultados = eventosService.guardarEventosLote(eventos);
        return Response.ok(resultados).build();
    }

    /**
     * Retorna los eventos registrados para una guía, ordenados cronológicamente por tiempo.
     */
    @GET
    @Path("/eventos/{guiaId}")
    public Response listarEventos(@PathParam("guiaId") Long guiaId) {
        try {
            List<EventoViajeDTO> eventos = eventosService.listarEventosPorGuia(guiaId);
            return Response.ok(eventos).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Collections.singletonMap("error", e.getMessage()))
                    .build();
        }
    }
}
