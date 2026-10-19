package uy.edu.fing.grupo07.CargaUY.security;

import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeguridadP2Test {
    private void inyectar(Object destino,String nombre,Object valor)throws Exception{
        Field f=destino.getClass().getDeclaredField(nombre);f.setAccessible(true);f.set(destino,valor);
    }
    private Administrador admin(){
        Administrador u=new Administrador("Admin","admin@example.invalid",LocalDate.of(1990,1,1),
            90000001,"{LDAP}");u.setId(7);u.getRoles().add(TipoUsuario.ADMINISTRADOR);return u;
    }
    @Test void pkceUsaElVectorDeReferencia(){
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Cripto.pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }
    @Test void tokensNoSeRepitenYElHashNoEsElToken(){
        Set<String> tokens=new HashSet<>();
        for(int i=0;i<1000;i++){String t=Cripto.aleatorio();assertEquals(43,t.length());assertTrue(tokens.add(t));
            assertEquals(64,Cripto.hash(t).length());assertNotEquals(t,Cripto.hash(t));}
    }
    @Test void tokenVencidoEsRechazado()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),admin(),Instant.now().minusSeconds(1)));
        assertEquals(401,assertThrows(ErrorAcceso.class,()->s.validarToken(token)).getEstado());
    }
    @Test void bajaInvalidaLaSesionAunqueElTokenNoVencio()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var u=admin();u.setActivo(false);
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),u,Instant.now().plusSeconds(60)));
        assertThrows(ErrorAcceso.class,()->s.validarToken(token));
    }
    @Test void rolesSeLeenDeLaCuentaActual()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var u=admin();
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
            .thenReturn(new SesionAcceso(Cripto.hash(token),u,Instant.now().plusSeconds(60)));
        assertTrue(s.validarToken(token).tiene(TipoUsuario.ADMINISTRADOR));
        u.getRoles().clear();u.getRoles().add(TipoUsuario.AUTORIDAD);
        assertFalse(s.validarToken(token).tiene(TipoUsuario.ADMINISTRADOR));
    }
    @Test void logoutBorraLaSesion()throws Exception{
        var em=mock(EntityManager.class);var s=new SesionesService();inyectar(s,"em",em);
        String token=Cripto.aleatorio();var entidad=new SesionAcceso(Cripto.hash(token),admin(),Instant.now());
        when(em.find(SesionAcceso.class,Cripto.hash(token))).thenReturn(entidad);
        s.logout(token);verify(em).remove(entidad);
    }
    @Test void empresaAjenaSeRechazaEnNegocio()throws Exception{
        var sesiones=mock(SesionesService.class);var control=new ControlAcceso();var em=mock(EntityManager.class);
        inyectar(control,"sesiones",sesiones);inyectar(control,"em",em);
        when(sesiones.validarToken("token")).thenReturn(new Acceso(7,12345678,"Ana",Set.of(TipoUsuario.CIUDADANO)));
        when(em.find(Empresa.class,100)).thenReturn(new Empresa(100,"SA","Empresa","Montevideo"));
        assertEquals(403,assertThrows(ErrorAcceso.class,()->control.empresa("token",100,true)).getEstado());
    }
    @Test void passwordVacioNoHaceBindAnonimo(){
        assertEquals(401,assertThrows(ErrorAcceso.class,()->new LdapClient().autenticar("martin.admin","")).getEstado());
    }
    @Test void caidaLdapNoEmiteSesion()throws Exception{
        var auth=new AutenticacionService();var ldap=mock(LdapClient.class);var sesiones=mock(SesionesService.class);
        inyectar(auth,"ldap",ldap);inyectar(auth,"sesiones",sesiones);
        doThrow(new ErrorAcceso(503,"Caído")).when(ldap).autenticar("uid","password");
        assertThrows(ErrorAcceso.class,()->auth.login("uid","password"));
        verifyNoInteractions(sesiones);
    }
}
