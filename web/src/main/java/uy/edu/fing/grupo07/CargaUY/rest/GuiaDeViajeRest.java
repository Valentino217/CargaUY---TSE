package uy.edu.fing.grupo07.CargaUY.rest;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJB;
import uy.edu.fing.grupo07.CargaUY.dto.GuiaResumenDTO;

@Path("/viajes")
public class GuiaDeViajeRest {
	
	@EJB
	private GuiaDeViajeEJB guiaDeViajeEJB;	
	
	
	@GET
	@Path("/{cedula}")
	@Produces(MediaType.APPLICATION_JSON)
	public List<GuiaResumenDTO> buscarViajes(
	        @PathParam("cedula") int cedula) {

	    return guiaDeViajeEJB
	            .listarGuiasResumenPorCedulaChofer(cedula);
	}
}
