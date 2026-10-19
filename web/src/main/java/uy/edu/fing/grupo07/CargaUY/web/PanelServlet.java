package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.time.LocalDate;
import jakarta.ejb.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.service.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet(urlPatterns={"/app/admin","/app/empresa","/app/perfil"})
public class PanelServlet extends HttpServlet {
    @EJB private GestionPerfilesService perfiles;
    @EJB private GestionPlataformaService plataforma;
    @EJB private ConsultaCiudadanoService pdi;
    @EJB private IdentidadesService identidades;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException{
        WebSeguridad.preparar(r,s);
        r.getRequestDispatcher("/WEB-INF/p2/panel.jsp").forward(r,s);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException{
        r.setCharacterEncoding("UTF-8");
        try { Object resultado=ejecutar(r); r.setAttribute("resultado",
                resultado==null?"Operación completada":resultado.toString()); }
        catch(ErrorAcceso e){s.setStatus(e.getEstado());r.setAttribute("error",e.getMessage());}
        catch(ErrorNegocio|IllegalArgumentException e){s.setStatus(400);r.setAttribute("error",e.getMessage());}
        catch(EJBException e){
            boolean conflicto=false;
            for(Throwable causa=e;causa!=null;causa=causa.getCause())
                if(causa instanceof jakarta.persistence.OptimisticLockException
                    || causa instanceof java.sql.SQLException sql && java.util.Arrays.asList("23505","23503","23514").contains(sql.getSQLState()))
                    conflicto=true;
            s.setStatus(conflicto?409:500);
            r.setAttribute("error",conflicto?"Conflicto al guardar; recargá y revisá referencias":"Error interno; revisar el registro del servidor");
            getServletContext().log("Fallo de operación; correlacion="+r.getAttribute("correlacion"),e);
        }
        doGet(r,s);
    }
    private Object ejecutar(HttpServletRequest r) {
        String accion = r.getParameter("accion");
        if (accion == null) throw new ErrorNegocio("Acción obligatoria");
        String token=WebSeguridad.token(r);
        if(r.getServletPath().equals("/app/perfil") &&
                !java.util.Set.of("consultarUsuario","modificarUsuario").contains(accion))
            throw new ErrorAcceso(403,"Acción no disponible en perfil");
        if(r.getServletPath().equals("/app/empresa") &&
                !java.util.Set.of("listarEmpresas","consultarEmpresa","crearEmpresa","modificarEmpresa",
                    "asociar","desasociar","vinculo","listarVinculos","consultarPdi").contains(accion))
            throw new ErrorAcceso(403,"Acción no disponible en empresas");
        switch (accion) {
            case "crearUsuario": return perfiles.crearUsuario(token, datos(r, true));
            case "listarUsuarios": return perfiles.listarUsuarios(token, tipoOpcional(r), pagina(r), tam(r));
            case "consultarUsuario": return perfiles.consultarUsuario(token, entero(r, "id"));
            case "modificarUsuario": perfiles.modificarUsuario(token, entero(r, "id"), Long.parseLong(r.getParameter("version")), datos(r, false)); break;
            case "bajaUsuario": perfiles.cambiarEstadoUsuario(token, entero(r, "id"), false); break;
            case "reactivarUsuario": perfiles.cambiarEstadoUsuario(token, entero(r, "id"), true); break;
            case "crearEmpresa": perfiles.crearEmpresa(token, entero(r, "numero"), r.getParameter("razon"),
                    r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "consultarEmpresa": return perfiles.consultarEmpresa(token, entero(r, "numero"));
            case "listarEmpresas": return perfiles.listarEmpresas(token, pagina(r), tam(r));
            case "modificarEmpresa": perfiles.modificarEmpresa(token, entero(r, "numero"), Long.parseLong(r.getParameter("version")),
                    r.getParameter("razon"), r.getParameter("nombrePublico"), r.getParameter("direccion")); break;
            case "bajaEmpresa": perfiles.cambiarEstadoEmpresa(token, entero(r, "numero"), false); break;
            case "reactivarEmpresa": perfiles.cambiarEstadoEmpresa(token, entero(r, "numero"), true); break;
            case "asociar": perfiles.asociarCiudadano(token, entero(r, "numero"), entero(r, "id")); break;
            case "desasociar": perfiles.desasociarCiudadano(token, entero(r, "numero"), entero(r, "id")); break;
            case "crearRubro": return plataforma.crearRubro(token, r.getParameter("nombreRubro"), r.getParameter("descripcion"));
            case "listarRubros": return plataforma.listarRubros(token);
            case "modificarRubro": plataforma.modificarRubro(token, entero(r, "codigo"),
                    r.getParameter("nombreRubro"), r.getParameter("descripcion")); break;
            case "eliminarRubro": plataforma.eliminarRubro(token, entero(r, "codigo")); break;
            case "crearTipoCarga": return plataforma.crearTipoCarga(token, r.getParameter("descripcion"));
            case "listarTiposCarga": return plataforma.listarTiposCarga(token);
            case "modificarTipoCarga": plataforma.modificarTipoCarga(token, entero(r, "codigo"), r.getParameter("descripcion")); break;
            case "eliminarTipoCarga": plataforma.eliminarTipoCarga(token, entero(r, "codigo")); break;
            case "consultarRegla": return plataforma.consultarRegla(token);
            case "crearRegla": plataforma.crearRegla(token, entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm"))); break;
            case "editarRegla": plataforma.editarRegla(token, entero(r, "cargaKg"),
                    Double.parseDouble(r.getParameter("desvioKm")),
                    Long.parseLong(r.getParameter("version"))); break;
            case "consultarPdi": return pdi.consultar(token, entero(r, "ci"), (String)r.getAttribute("correlacion"));
            case "listarVinculos": return identidades.listar(token,entero(r,"numero"));
            case "vincularIdentidad": identidades.vincular(token, entero(r,"id"),r.getParameter("origen"),r.getParameter("uid"));break;
            case "roles": identidades.roles(token,entero(r,"id"),java.util.Arrays.stream(
                r.getParameter("roles").split(",")).map(String::trim).map(TipoUsuario::valueOf)
                .collect(java.util.stream.Collectors.toSet()));break;
            case "vinculo": identidades.vinculo(token,entero(r,"numero"),entero(r,"id"),
                r.getParameter("responsable")!=null,r.getParameter("chofer")!=null);break;
            default: throw new ErrorNegocio("Acción desconocida");
        }
        return null;
    }
    private int pagina(HttpServletRequest r) { return Integer.parseInt(r.getParameter("pagina")==null?"0":r.getParameter("pagina")); }
    private int tam(HttpServletRequest r) { return Integer.parseInt(r.getParameter("tam")==null?"20":r.getParameter("tam")); }
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