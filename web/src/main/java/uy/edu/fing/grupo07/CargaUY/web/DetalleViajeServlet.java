package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;

import java.io.IOException;
import java.util.List;

/**
 * Servlet encargado de consultar y despachar los detalles completos de un viaje,
 * incluyendo su línea cronológica de eventos y las pesadas de control en balanza.
 */
@WebServlet(name = "DetalleViajeServlet", urlPatterns = {"/DetalleViajeServlet", "/detalle-viaje"})
public class DetalleViajeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionEventosServiceLocal eventosService;

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    public DetalleViajeServlet() {
        super();
    }

    public DetalleViajeServlet(GestionEventosServiceLocal eventosService, GestionBalanzaServiceLocal balanzaService) {
        this.eventosService = eventosService;
        this.balanzaService = balanzaService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String guiaIdParam = request.getParameter("guiaId");

        if (guiaIdParam == null || guiaIdParam.isBlank()) {
            request.getRequestDispatcher("/detalleViaje.jsp").forward(request, response);
            return;
        }

        try {
            Long guiaId = Long.parseLong(guiaIdParam.trim());
            List<EventoViajeDTO> eventos = eventosService.listarEventosPorGuia(guiaId);
            List<PesadaBalanzaDTO> pesadas = balanzaService.listarPesadasPorGuia(guiaId);

            request.setAttribute("guiaId", guiaId);
            request.setAttribute("eventos", eventos);
            request.setAttribute("pesadas", pesadas);
        } catch (NumberFormatException nfe) {
            request.setAttribute("error", "El identificador de la guía debe ser numérico.");
        } catch (Exception e) {
            request.setAttribute("error", "Error al consultar viaje: " + e.getMessage());
        }

        request.getRequestDispatcher("/detalleViaje.jsp").forward(request, response);
    }
}
