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

    @EJB
    private uy.edu.fing.grupo07.CargaUY.jms.TrackingProducerLocal trackingProducer;

    public ChoferResource() {
    }

    // Constructor para tests con mock
    ChoferResource(GestionEventosServiceLocal eventosService) {
        this.eventosService = eventosService;
    }

    ChoferResource(GestionEventosServiceLocal eventosService, uy.edu.fing.grupo07.CargaUY.jms.TrackingProducerLocal trackingProducer) {
        this.eventosService = eventosService;
        this.trackingProducer = trackingProducer;
    }

    /**
     * Sincroniza un lote de eventos registrados offline por el chofer.
     * Si el productor JMS está disponible, encola asíncronamente en TrackingQueue
     * absorbiendo picos de carga (AC012) tras validar deduplicación inmediata.
     * Retorna una lista con el estado individual de cada evento (ACCEPTED o DUPLICATE).
     */
    @POST
    @Path("/eventos/sync")
    public Response sincronizarEventos(List<EventoViajeDTO> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.emptyList())
                    .build();
        }

        if (trackingProducer != null) {
            java.util.List<SyncResultDTO> resultados = new java.util.ArrayList<>();
            for (EventoViajeDTO ev : eventos) {
                if (ev.uuid() != null && eventosService.existeEvento(ev.uuid())) {
                    resultados.add(SyncResultDTO.duplicate(ev.uuid()));
                } else {
                    trackingProducer.enviarEvento(ev);
                    resultados.add(SyncResultDTO.accepted(ev.uuid()));
                }
            }
            return Response.ok(resultados).build();
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

    /**
     * Obtiene la guía de viaje activa asignada a un chofer (por ID o por CI).
     */
    @GET
    @Path("/guia-asignada")
    public Response obtenerGuiaAsignada(
            @QueryParam("choferId") Integer choferId,
            @QueryParam("ci") Integer ci) {
        if (choferId == null && ci == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", "Debe indicar choferId o ci como parámetro de consulta."))
                    .build();
        }
        uy.edu.fing.grupo07.CargaUY.dto.GuiaResumenDTO guia = eventosService.obtenerGuiaAsignadaChofer(choferId, ci);
        if (guia == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Collections.singletonMap("mensaje", "No se encontró una guía activa para el chofer indicado."))
                    .build();
        }
        return Response.ok(guia).build();
    }

    /**
     * Reporta un incidente en ruta asociado a la guía del chofer.
     */
    @POST
    @Path("/incidente")
    public Response reportarIncidente(uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO dto) {
        if (dto == null || dto.guiaId() == null || dto.descripcion() == null || dto.descripcion().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", "guiaId y descripcion son obligatorios para reportar un incidente."))
                    .build();
        }
        try {
            EventoViajeDTO evento = eventosService.reportarIncidente(dto);
            return Response.status(Response.Status.CREATED).entity(evento).build();
        } catch (uy.edu.fing.grupo07.CargaUY.exception.BusinessException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", e.getMessage()))
                    .build();
        }
    }
}
