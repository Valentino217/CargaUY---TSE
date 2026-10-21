package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;

/** Modelo de presentación de las vistas Facelets. Las operaciones y su autorización
 * permanecen en los controladores y servicios existentes. No conserva credenciales. */
@Named("plataformaVista") @RequestScoped
public class PlataformaVista {
    private static final Map<String,String> TITULOS=Map.of(
        "listar","Listado de empresas","registrar","Registrar empresa","consultar","Consultar empresa",
        "editar","Editar empresa","ciudadanos","Vincular ciudadano","choferes","Vincular chofer",
        "vinculos","Consultar vínculos","estado","Activar o dar de baja","pdi","Consultar ciudadano",
        "altaCiudadano","Registrar usuario");
    private HttpServletRequest request(){return (HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest();}
    public String getRuta(){
        Object original=request().getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        return original==null?request().getServletPath():original.toString();
    }
    public Acceso getActor(){return (Acceso)request().getAttribute("actor");}
    public boolean tiene(String rol){return getActor()!=null&&getActor().tiene(TipoUsuario.valueOf(rol));}
    public boolean isOperador(){return tiene("ADMINISTRADOR")||tiene("FUNCIONARIO");}
    public boolean isEditar(){return isOperador()||tiene("CIUDADANO");}
    public String getSeccion(){return Objects.toString(request().getAttribute("seccion"),"listar");}
    public String titulo(String seccion){return TITULOS.getOrDefault(seccion,"Empresas");}
    public List<String> getSecciones(){return List.of("listar","registrar","consultar","editar","altaCiudadano","ciudadanos","choferes","vinculos","estado","pdi").stream()
        .filter(s->!(Set.of("registrar","altaCiudadano","ciudadanos","choferes","estado").contains(s)&&!isOperador())&&(!s.equals("editar")||isEditar())).toList();}
    public boolean isBuscarEmpresa(){return Set.of("consultar","editar","ciudadanos","choferes","vinculos","estado").contains(getSeccion());}
    public String valor(String campo,Object defecto){return Objects.toString(request().getParameter(campo),Objects.toString(defecto,""));}
    public boolean seleccionado(String rol){String[] roles=request().getParameterValues("roles");return roles!=null&&Arrays.asList(roles).contains(rol);}
    public List<String> getRoles(){return tiene("ADMINISTRADOR")?List.of("CIUDADANO","CHOFER","FUNCIONARIO","AUTORIDAD","ADMINISTRADOR"):List.of("CIUDADANO","CHOFER");}
    public String getHoy(){return LocalDate.now().toString();}
    public List<String> getAcciones(){return getRuta().equals("/app/admin")?List.of(
        "crearUsuario","listarUsuarios","consultarUsuario","modificarUsuario","bajaUsuario","reactivarUsuario",
        "crearEmpresa","consultarEmpresa","listarEmpresas","modificarEmpresa","bajaEmpresa","reactivarEmpresa",
        "asociar","desasociar","vincularIdentidad","roles","vinculo","listarVinculos","crearRubro","listarRubros","modificarRubro",
        "eliminarRubro","crearTipoCarga","listarTiposCarga","modificarTipoCarga","eliminarTipoCarga","consultarRegla",
        "crearRegla","editarRegla","consultarPdi"):List.of("consultarUsuario","modificarUsuario");}
    public String etiqueta(String accion){return accion.replaceAll("([a-z])([A-Z])","$1 $2");}
}
