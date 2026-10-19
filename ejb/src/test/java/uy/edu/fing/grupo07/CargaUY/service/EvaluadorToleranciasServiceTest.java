package uy.edu.fing.grupo07.CargaUY.service;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EvaluadorToleranciasServiceTest {
 private EvaluadorToleranciasService servicio(ReglasLecturaService reglas)throws Exception{
  var s=new EvaluadorToleranciasService();Field f=EvaluadorToleranciasService.class.getDeclaredField("reglas");
  f.setAccessible(true);f.set(s,reglas);return s;
 }
 @Test void cargaUsaLaConfiguracionDeCadaEvaluacion()throws Exception{
  var reglas=mock(ReglasLecturaService.class);
  when(reglas.actuales()).thenReturn(new ReglasLecturaService.Snapshot(500,5,0),new ReglasLecturaService.Snapshot(100,5,1));
  var s=servicio(reglas);
  assertFalse(s.excesoCarga(20200,10000,10000));assertTrue(s.excesoCarga(20200,10000,10000));
 }
 @Test void desvioUsaKilometrosFraccionariosActualizados()throws Exception{
  var reglas=mock(ReglasLecturaService.class);
  when(reglas.actuales()).thenReturn(new ReglasLecturaService.Snapshot(500,1,0),new ReglasLecturaService.Snapshot(500,0.5,1));
  var s=servicio(reglas);assertFalse(s.superaDesvio(0.8));assertTrue(s.superaDesvio(0.8));
 }
}