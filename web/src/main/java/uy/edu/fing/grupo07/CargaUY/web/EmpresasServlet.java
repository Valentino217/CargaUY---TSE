package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import jakarta.ejb.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.service.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

/** Cada sección tiene su formulario y solo acepta sus propias operaciones. */
@WebServlet("/app/empresa")
public class EmpresasServlet extends HttpServlet {
    @EJB private GestionPerfilesService perfiles;
    @EJB private IdentidadesService identidades;
    @EJB private ConsultaCiudadanoService pdi;
    private static final Map<String,Set<String>> ACCIONES=Map.of(
        "listar",Set.of(),"registrar",Set.of("crearEmpresa"),
        "consultar",Set.of("buscarEmpresa"),"editar",Set.of("buscarEmpresa","modificarEmpresa"),
        "ciudadanos",Set.of("vincularCiudadano","desvincularCiudadano"),
        "choferes",Set.of("vincularChofer","desvincularChofer"),
        "vinculos",Set.of("buscarVinculos"),"estado",Set.of("buscarEmpresa","activarEmpresa","desactivarEmpresa"),
        "pdi",Set.of("consultarPdi"),"altaCiudadano",Set.of("registrarCiudadano"));

    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException {
        WebSeguridad.preparar(r,s);
        try {
            String seccion=seccion(r);r.setAttribute("seccion",seccion);
            preparar(r,seccion);
            Object mensaje=r.getSession().getAttribute("mensajeEmpresas");
            if(mensaje!=null){r.setAttribute("mensaje",mensaje);r.getSession().removeAttribute("mensajeEmpresas");}
        } catch(ErrorAcceso e){s.setStatus(e.getEstado());r.setAttribute("error",e.getMessage());}
          catch(ErrorNegocio|IllegalArgumentException e){s.setStatus(400);r.setAttribute("error",e.getMessage());}
          catch(EJBException e){fallo(r,s,e);}
        mostrar(r,s);
    }

    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException {
        r.setCharacterEncoding("UTF-8");WebSeguridad.preparar(r,s);
        try {
            String seccion=seccion(r);r.setAttribute("seccion",seccion);
            String accion=r.getParameter("accion");
            if(!ACCIONES.get(seccion).contains(accion==null?"":accion))
                throw new ErrorAcceso(400,"Operación inválida para esta sección");
            String token=WebSeguridad.token(r);
            if(accion.equals("registrarCiudadano")){
                int id;
                String[] valores=r.getParameterValues("roles");
                Set<TipoUsuario> roles=java.util.EnumSet.noneOf(TipoUsuario.class);
                if(valores!=null)for(String valor:valores){
                    try{roles.add(TipoUsuario.valueOf(valor));}
                    catch(IllegalArgumentException e){throw new IllegalArgumentException("Rol inválido");}
                }
                try {
                    String nro=r.getParameter("nroFuncionario");
                    var datos=new uy.edu.fing.grupo07.CargaUY.dto.DatosUsuario(null,r.getParameter("nombre"),r.getParameter("mail"),
                        java.time.LocalDate.parse(r.getParameter("fechaNac")==null?"":r.getParameter("fechaNac")),positivo(r,"ci"),null,
                        nro==null||nro.isBlank()?null:Integer.valueOf(nro),r.getParameter("departamento"),r.getParameter("cargo"));
                    id=perfiles.registrarConRoles(token,datos,roles);
                } catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Revisá la fecha de nacimiento.");}
                r.getSession().setAttribute("mensajeEmpresas","Usuario registrado con ID "+id+" y roles "+roles+".");
                s.setStatus(303);s.setHeader("Location",r.getContextPath()+"/app/empresa?seccion=altaCiudadano");return;
            }
            if(accion.equals("buscarEmpresa")||accion.equals("buscarVinculos")){
                preparar(r,seccion);mostrar(r,s);return;
            }
            if(accion.equals("consultarPdi")){
                r.setAttribute("ciudadanoPdi",pdi.consultar(token,positivo(r,"ci"),(String)r.getAttribute("correlacion")));
                mostrar(r,s);return;
            }
            preparar(r,seccion); // Conserva el contexto y el formulario si una escritura es rechazada.
            int empresa=positivo(r,"numero");
            String mensaje;
            switch(accion){
                case "crearEmpresa" -> {
                    perfiles.crearEmpresa(token,empresa,r.getParameter("razon"),r.getParameter("nombrePublico"),r.getParameter("direccion"));
                    mensaje="Empresa registrada correctamente.";
                }
                case "modificarEmpresa" -> {
                    long version=Long.parseLong(r.getParameter("version"));
                    if(version<0)throw new IllegalArgumentException("Versión inválida");
                    perfiles.modificarEmpresa(token,empresa,version,r.getParameter("razon"),r.getParameter("nombrePublico"),r.getParameter("direccion"));
                    mensaje="Datos de la empresa actualizados.";
                }
                case "vincularCiudadano","desvincularCiudadano","vincularChofer","desvincularChofer" -> {
                    boolean ciudadano=seccion.equals("ciudadanos"),habilitar=accion.startsWith("vincular");
                    identidades.funcionEmpresa(token,empresa,positivo(r,"id"),ciudadano?TipoUsuario.CIUDADANO:TipoUsuario.CHOFER,habilitar);
                    mensaje=habilitar?"Vínculo registrado correctamente.":"Función desvinculada correctamente.";
                }
                case "activarEmpresa","desactivarEmpresa" -> {
                    perfiles.cambiarEstadoEmpresa(token,empresa,accion.equals("activarEmpresa"));
                    mensaje=accion.equals("activarEmpresa")?"Empresa activada.":"Empresa dada de baja.";
                }
                default -> throw new ErrorAcceso(400,"Operación desconocida");
            }
            r.getSession().setAttribute("mensajeEmpresas",mensaje);
            s.setStatus(303);
            s.setHeader("Location",r.getContextPath()+"/app/empresa?seccion="+seccion+"&numero="+empresa);
        } catch(ErrorAcceso e){s.setStatus(e.getEstado());r.setAttribute("error",e.getMessage());mostrar(r,s);}
          catch(ErrorNegocio|IllegalArgumentException e){s.setStatus(400);r.setAttribute("error",e.getMessage());mostrar(r,s);}
          catch(EJBException e){fallo(r,s,e);mostrar(r,s);}
    }

    private String seccion(HttpServletRequest r){
        String seccion=r.getParameter("seccion");if(seccion==null)seccion="listar";
        if(!ACCIONES.containsKey(seccion))throw new ErrorAcceso(400,"Sección desconocida");
        Acceso actor=(Acceso)r.getAttribute("actor");
        if(actor==null)throw new ErrorAcceso(401,"Sesión no autenticada");
        boolean operador=actor.tiene(TipoUsuario.ADMINISTRADOR)||actor.tiene(TipoUsuario.FUNCIONARIO);
        if(Set.of("registrar","altaCiudadano","ciudadanos","choferes","estado").contains(seccion)&&!operador
            ||seccion.equals("editar")&&!operador&&!actor.tiene(TipoUsuario.CIUDADANO))
            throw new ErrorAcceso(403,"No tenés permisos para esta operación");
        return seccion;
    }
    private void preparar(HttpServletRequest r,String seccion){
        String token=WebSeguridad.token(r);
        if(seccion.equals("listar")){
            int pagina=entero(r,"pagina",0,0,Integer.MAX_VALUE),tam=entero(r,"tam",20,1,100);
            r.setAttribute("pagina",pagina);r.setAttribute("tam",tam);
            r.setAttribute("empresas",perfiles.listarEmpresas(token,pagina,tam));return;
        }
        if(r.getParameter("numero")==null||r.getParameter("numero").isBlank())return;
        int numero=positivo(r,"numero");
        if(Set.of("consultar","editar","estado","vinculos","ciudadanos","choferes").contains(seccion)){
            r.setAttribute("empresa",perfiles.consultarEmpresa(token,numero));
            if(Set.of("vinculos","ciudadanos","choferes").contains(seccion))
                r.setAttribute("vinculos",identidades.listar(token,numero));
        }
    }
    private void fallo(HttpServletRequest r,HttpServletResponse s,EJBException error){
        boolean conflicto=false;
        for(Throwable t=error;t!=null;t=t.getCause())
            if(t instanceof jakarta.persistence.OptimisticLockException
                ||t instanceof java.sql.SQLException sql&&Set.of("23505","23503","23514").contains(sql.getSQLState()))conflicto=true;
        s.setStatus(conflicto?409:500);
        r.setAttribute("error",conflicto?"El registro cambió o existe una referencia en conflicto. Volvé a consultar la empresa.":"No se pudo completar la operación. Revisá el registro del servidor.");
        getServletContext().log("Error en empresas; correlacion="+r.getAttribute("correlacion"),error);
    }
    private void mostrar(HttpServletRequest r,HttpServletResponse s)throws IOException,ServletException{
        if(r.getAttribute("seccion")==null)r.setAttribute("seccion","listar");
        r.getRequestDispatcher("/WEB-INF/p2/empresas.xhtml").forward(r,s);
    }
    private int positivo(HttpServletRequest r,String campo){return entero(r,campo,null,1,Integer.MAX_VALUE);}
    private int entero(HttpServletRequest r,String campo,Integer defecto,int min,int max){
        String valor=r.getParameter(campo);int numero;
        try{numero=valor==null||valor.isBlank()?java.util.Objects.requireNonNull(defecto):Integer.parseInt(valor);}
        catch(IllegalArgumentException|NullPointerException e){throw new IllegalArgumentException("Revisá el campo "+campo+".");}
        if(numero<min||numero>max)throw new IllegalArgumentException("Valor fuera de rango en "+campo+".");return numero;
    }
}
