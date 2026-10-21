package uy.edu.fing.grupo07.CargaUY.security;

import java.lang.reflect.Field;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

class FuncionesEmpresaTest {
    EntityManager em;ControlAcceso control;IdentidadesService servicio;
    Empresa empresa;Ciudadano usuario;VinculoEmpresa vinculo;
    @BeforeEach void preparar()throws Exception {
        em=mock(EntityManager.class);control=mock(ControlAcceso.class);servicio=new IdentidadesService();
        for(var entrada:java.util.Map.of("em",em,"control",control).entrySet()){
            Field campo=IdentidadesService.class.getDeclaredField(entrada.getKey());campo.setAccessible(true);campo.set(servicio,entrada.getValue());
        }
        empresa=new Empresa(101,"Empresa","Empresa","Dirección");
        usuario=new Ciudadano();usuario.setId(7);usuario.getRoles().add(TipoUsuario.CIUDADANO);usuario.getRoles().add(TipoUsuario.CHOFER);
        vinculo=new VinculoEmpresa(usuario,empresa);vinculo.setResponsable(true);vinculo.setChofer(true);
        when(em.find(Empresa.class,101)).thenReturn(empresa);when(em.find(Usuario.class,7)).thenReturn(usuario);
        when(em.find(VinculoEmpresa.class,"101:7")).thenReturn(vinculo);
    }
    @Test void desvincularChoferConservaSuFuncionDeResponsable(){
        servicio.funcionEmpresa("token",101,7,TipoUsuario.CHOFER,false);
        assertTrue(vinculo.isResponsable());assertFalse(vinculo.isChofer());
        verify(em).lock(empresa,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void desvincularCiudadanoConservaSuFuncionDeChofer(){
        servicio.funcionEmpresa("token",101,7,TipoUsuario.CIUDADANO,false);
        assertFalse(vinculo.isResponsable());assertTrue(vinculo.isChofer());
    }
    @Test void vincularChoferNoQuitaElResponsable(){
        vinculo.setChofer(false);
        servicio.funcionEmpresa("token",101,7,TipoUsuario.CHOFER,true);
        assertTrue(vinculo.isResponsable());assertTrue(vinculo.isChofer());
    }
    @Test void noSeOtorgaElRolAlVincular(){
        usuario.getRoles().remove(TipoUsuario.CHOFER);vinculo.setChofer(false);
        assertEquals(409,assertThrows(ErrorAcceso.class,()->servicio.funcionEmpresa("token",101,7,TipoUsuario.CHOFER,true)).getEstado());
        assertFalse(vinculo.isChofer());assertTrue(vinculo.isResponsable());
        verify(em,never()).persist(any());
    }
}
