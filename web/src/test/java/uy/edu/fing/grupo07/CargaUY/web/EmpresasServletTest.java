package uy.edu.fing.grupo07.CargaUY.web;

import java.lang.reflect.Field;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.service.GestionPerfilesService;

class EmpresasServletTest {
    @Test void altaUtilizaVariosRolesSeleccionados()throws Exception{
        params.putAll(Map.of("seccion","altaCiudadano","accion","registrarCiudadano","tipo","ADMINISTRADOR",
            "nombre","Ana","mail","ana@example.com","fechaNac","1990-01-01","ci","12345678"));
        when(req.getParameterValues("roles")).thenReturn(new String[]{"CIUDADANO","CHOFER"});
        when(perfiles.registrarConRoles(eq("token"),any(DatosUsuario.class),eq(Set.of(TipoUsuario.CIUDADANO,TipoUsuario.CHOFER)))).thenReturn(77);
        servlet.doPost(req,res);
        verify(perfiles).registrarConRoles(eq("token"),any(DatosUsuario.class),eq(Set.of(TipoUsuario.CIUDADANO,TipoUsuario.CHOFER)));
        verify(ses).setAttribute("mensajeEmpresas","Usuario registrado con ID 77 y roles [CIUDADANO, CHOFER].");
        verify(res).setStatus(303);
    }
    @Test void ciudadanoNoPuedeRegistrarOtroCiudadano()throws Exception{
        params.putAll(Map.of("seccion","altaCiudadano","accion","registrarCiudadano"));
        when(req.getAttribute("actor")).thenReturn(new Acceso(7,80000001,"Ciudadano",Set.of(TipoUsuario.CIUDADANO)));
        servlet.doPost(req,res);verify(res).setStatus(403);verifyNoInteractions(perfiles,identidades);
    }
    EmpresasServlet servlet;GestionPerfilesService perfiles;IdentidadesService identidades;
    HttpServletRequest req;HttpServletResponse res;HttpSession ses;Map<String,String> params;
    @BeforeEach void preparar()throws Exception{
        servlet=new EmpresasServlet();perfiles=mock(GestionPerfilesService.class);identidades=mock(IdentidadesService.class);
        for(var entrada:Map.of("perfiles",perfiles,"identidades",identidades).entrySet()){
            Field campo=EmpresasServlet.class.getDeclaredField(entrada.getKey());campo.setAccessible(true);campo.set(servlet,entrada.getValue());
        }
        params=new HashMap<>();req=mock(HttpServletRequest.class);res=mock(HttpServletResponse.class);ses=mock(HttpSession.class);
        when(req.getParameter(anyString())).thenAnswer(i->params.get(i.getArgument(0)));
        when(req.getSession()).thenReturn(ses);when(req.getSession(false)).thenReturn(ses);when(ses.getAttribute("token")).thenReturn("token");
        when(req.getContextPath()).thenReturn("/CargaUY-web");
        when(req.getAttribute("actor")).thenReturn(new Acceso(1,90000001,"Operador",Set.of(TipoUsuario.FUNCIONARIO)));
        when(req.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
    }
    @Test void altaRedirigeTrasGuardarParaNoRepetirElPost()throws Exception{
        params.putAll(Map.of("seccion","registrar","accion","crearEmpresa","numero","101","razon","Razón","nombrePublico","Nombre","direccion","Dirección"));
        servlet.doPost(req,res);
        verify(perfiles).crearEmpresa("token",101,"Razón","Nombre","Dirección");
        verify(res).setStatus(303);verify(res).setHeader("Location","/CargaUY-web/app/empresa?seccion=registrar&numero=101");
    }
    @Test void ciudadanoNoPuedeAbrirRegistroDeEmpresas()throws Exception{
        params.put("seccion","registrar");when(req.getAttribute("actor")).thenReturn(new Acceso(7,80000001,"Ciudadano",Set.of(TipoUsuario.CIUDADANO)));
        servlet.doGet(req,res);verify(res).setStatus(403);verifyNoInteractions(perfiles,identidades);
    }
    @Test void noSeAceptaUnaAccionDeOtraSeccion()throws Exception{
        params.putAll(Map.of("seccion","choferes","accion","crearEmpresa"));
        servlet.doPost(req,res);verify(res).setStatus(400);verifyNoInteractions(perfiles,identidades);
    }
    @Test void vincularChoferUsaLaFuncionIndependiente()throws Exception{
        params.putAll(Map.of("seccion","choferes","accion","vincularChofer","numero","101","id","7"));
        when(perfiles.consultarEmpresa("token",101)).thenReturn(new EmpresaResumen(101,"Empresa","Empresa","Dirección",true,List.of(),0));
        servlet.doPost(req,res);
        verify(identidades).funcionEmpresa("token",101,7,TipoUsuario.CHOFER,true);
        verify(identidades,never()).vinculo(anyString(),anyInt(),anyInt(),anyBoolean(),anyBoolean());
        verify(res).setStatus(303);
    }
}
