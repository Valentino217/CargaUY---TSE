package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;

import java.util.Collections;
import java.util.List;

/**
 * Endpoints REST para la ingesta y consulta de pesadas desde estaciones de balanza periféricas.
 */
@Path("/balanza")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BalanzaResource {

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    public BalanzaResource() {
    }

    public BalanzaResource(GestionBalanzaServiceLocal balanzaService) {
        this.balanzaService = balanzaService;
    }

    /**
     * Registra manualmente o vía integración una pesada de balanza.
     */
    @POST
    @Path("/pesaje")
    public Response registrarPesaje(PesadaBalanzaDTO dto) {
        if (dto == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", "El cuerpo de la pesada no puede ser nulo."))
                    .build();
        }
        try {
            PesadaBalanzaDTO resultado = balanzaService.registrarPesada(dto);
            return Response.status(Response.Status.CREATED).entity(resultado).build();
        } catch (BusinessException be) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", be.getMessage()))
                    .build();
        }
    }

    /**
     * Consume el mock de balanza periférica y persiste el resultado en la guía activa correspondiente.
     */
    @POST
    @Path("/mock-sync")
    public Response sincronizarDesdeMock() {
        try {
            PesadaBalanzaDTO resultado = balanzaService.registrarPesadaDesdeMock();
            return Response.status(Response.Status.CREATED).entity(resultado).build();
        } catch (BusinessException be) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", be.getMessage()))
                    .build();
        }
    }

    /**
     * Consulta el historial de pesadas registradas para una guía de viaje.
     */
    @GET
    @Path("/guia/{guiaId}")
    public Response listarPesadasPorGuia(@PathParam("guiaId") Long guiaId) {
        if (guiaId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Collections.singletonMap("error", "guiaId es obligatorio."))
                    .build();
        }
        try {
            List<PesadaBalanzaDTO> pesadas = balanzaService.listarPesadasPorGuia(guiaId);
            return Response.ok(pesadas).build();
        } catch (BusinessException be) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Collections.singletonMap("error", be.getMessage()))
                    .build();
        }
    }
}
