package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.service.*;
import uy.edu.fing.grupo07.CargaUY.integracion.ClientePdi;

@WebServlet("/p1/pruebas")
public class P1PruebasServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    @EJB private GestionPerfilesService perfiles;
    @EJB private GestionPlataformaService plataforma;
    @EJB private ClientePdi pdi;

    private boolean habilitado(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (!Boolean.getBoolean("p1.pruebas")
                || !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
            resp.sendError(404);
            return false;
        }
        return true;
    }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        if (!habilitado(req, resp)) return;
        HttpSession session = req.getSession();
        if (session.getAttribute("p1csrf") == null)
            session.setAttribute("p1csrf", UUID.randomUUID().toString());
        req.setAttribute("csrf", session.getAttribute("p1csrf"));
        resp.setHeader("Cache-Control", "no-store");
        req.getRequestDispatcher("/WEB-INF/p1/pruebas.jsp").forward(req, resp);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (!habilitado(req, resp)) return;
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/plain; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        String enviado = req.getParameter("csrf");
        String esperado = session == null ? null : (String) session.getAttribute("p1csrf");
        if (esperado == null || enviado == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), enviado.getBytes(StandardCharsets.UTF_8))) {
            resp.sendError(403);
            return;
        }
        try {
            Object resultado = ejecutar(req);
            resp.getWriter().println(resultado == null ? "Operación completada" : resultado);
        } catch (ErrorNegocio | IllegalArgumentException e) {
            resp.setStatus(400);
            resp.getWriter().println(e.getMessage());
        } catch (EJBException e) {
            // En particular, una carrera de unicidad o de @Version puede llegar aquí.
            getServletContext().log("Fallo de operación P1", e);
            resp.setStatus(409);
            resp.getWriter().println("No se pudo guardar. Revisá duplicados o cambios concurrentes.");
        }
    }
    private Object ejecutar(HttpServletRequest r) {
        String accion = r.getParameter("accion");
        if (accion == null) throw new ErrorNegocio("Acción obligatoria");
        switch (accion) {
            case "crearUsuario": return perfiles.crearUsuario(datos(r, true));
            case "listarUsuarios": return perfiles.listarUsuarios(tipoOpcional(r));
            case "consultarUsuario": return perfiles.consultarUsuario(entero(r, "id"));
            case "modificarUsuario": perfiles.modificarUsuario(entero(r, "id"), datos(r, false)); break;
            case "bajaUsuario": perfiles.cambiarEstadoUsuario(entero(r, "id"), false); break;
            case "reactivarUsuario": perfiles.cambiarEstadoUsuario(entero(r, "id"), true); break;
            case "crearEmpresa": perfiles.crearEmpresa(entero(r, "numero"), r.getParameter("razon"),
                    r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "consultarEmpresa": return perfiles.consultarEmpresa(entero(r, "numero"));
            case "listarEmpresas": return perfiles.listarEmpresas();
            case "modificarEmpresa": perfiles.modificarEmpresa(entero(r, "numero"),
                    r.getParameter("razon"), r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "bajaEmpresa": perfiles.cambiarEstadoEmpresa(entero(r, "numero"), false); break;
            case "reactivarEmpresa": perfiles.cambiarEstadoEmpresa(entero(r, "numero"), true); break;
            case "asociar": perfiles.asociarCiudadano(entero(r, "numero"), entero(r, "id")); break;
            case "desasociar": perfiles.desasociarCiudadano(entero(r, "numero"), entero(r, "id")); break;
            case "crearRubro": return plataforma.crearRubro(r.getParameter("nombreRubro"), r.getParameter("descripcion"));
            case "listarRubros": return plataforma.listarRubros();
            case "modificarRubro": plataforma.modificarRubro(entero(r, "codigo"),
                    r.getParameter("nombreRubro"), r.getParameter("descripcion")); break;
            case "eliminarRubro": plataforma.eliminarRubro(entero(r, "codigo")); break;
            case "crearTipoCarga": return plataforma.crearTipoCarga(r.getParameter("descripcion"));
            case "listarTiposCarga": return plataforma.listarTiposCarga();
            case "modificarTipoCarga": plataforma.modificarTipoCarga(entero(r, "codigo"), r.getParameter("descripcion")); break;
            case "eliminarTipoCarga": plataforma.eliminarTipoCarga(entero(r, "codigo")); break;
            case "consultarRegla": return plataforma.consultarRegla();
            case "crearRegla": plataforma.crearRegla(entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm"))); break;
            case "editarRegla": plataforma.editarRegla(entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm")),
                    Long.parseLong(r.getParameter("version"))); break;
            case "consultarPdi": return pdi.consultar(entero(r, "ci"));
            default: throw new ErrorNegocio("Acción desconocida");
        }
        return null;
    }
    private int entero(HttpServletRequest r, String nombre) {
        return Integer.parseInt(r.getParameter(nombre));
    }
    private TipoUsuario tipoOpcional(HttpServletRequest r) {
        String tipo = r.getParameter("tipo");
        return tipo == null || tipo.isBlank() ? null : TipoUsuario.valueOf(tipo);
    }
    private DatosUsuario datos(HttpServletRequest r, boolean alta) {
        String nro = r.getParameter("nroFuncionario");
        return new DatosUsuario(tipoOpcional(r), r.getParameter("nombre"), r.getParameter("mail"),
            LocalDate.parse(r.getParameter("fechaNac")), entero(r, "ci"),
            alta ? r.getParameter("password") : null,
            nro == null || nro.isBlank() ? null : Integer.valueOf(nro),
            r.getParameter("departamento"), r.getParameter("cargo"));
    }
}
