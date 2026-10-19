package uy.edu.fing.grupo07.CargaUY.security;
import java.lang.reflect.Field;
import java.time.Instant;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.FlujoIdentidad;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FlujosP2Test {
 private FlujosService servicio(EntityManager em)throws Exception{
  var s=new FlujosService();Field f=FlujosService.class.getDeclaredField("em");f.setAccessible(true);f.set(s,em);return s;
 }
 @Test void iniciaConPkceS256YPersistencia()throws Exception{
  var em=mock(EntityManager.class);var inicio=servicio(em).preparar(Cripto.pkce(Cripto.aleatorio()));
  assertEquals(43,inicio.state().length());assertTrue(inicio.url().contains("code_challenge_method=S256"));
  verify(em).persist(any(FlujoIdentidad.class));
 }
 @Test void consumeSoloConVerificadorCorrecto()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().plusSeconds(60));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertEquals("nonce",servicio(em).consumir(state,v));verify(em).remove(f);
 }
 @Test void noConsumeConVerificadorAjeno()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().plusSeconds(60));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertThrows(ErrorAcceso.class,()->servicio(em).consumir(state,Cripto.aleatorio()));
  verify(em,never()).remove(any());
 }
 @Test void flujoVencidoEsRechazado()throws Exception{
  var em=mock(EntityManager.class);String state=Cripto.aleatorio(),v=Cripto.aleatorio();
  var f=new FlujoIdentidad(Cripto.hash(state),Cripto.pkce(v),"nonce",Instant.now().minusSeconds(1));
  when(em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE)).thenReturn(f);
  assertThrows(ErrorAcceso.class,()->servicio(em).consumir(state,v));
 }
 @Test void stateNoRegistradoNoAutoriza()throws Exception{
  assertThrows(ErrorAcceso.class,()->servicio(mock(EntityManager.class)).consumir(Cripto.aleatorio(),Cripto.aleatorio()));
 }
}
