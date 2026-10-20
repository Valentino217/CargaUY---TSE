package uy.edu.fing.grupo07.CargaUY.security;

import java.lang.reflect.Field;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

class IntegracionChoferAccesoTest {
    private ControlAcceso servicio(Acceso actor,EntityManager em)throws Exception {
        var sesiones=mock(SesionesService.class);
        when(sesiones.validarToken("token")).thenReturn(actor);
        var control=new ControlAcceso();
        for(var entrada:java.util.Map.of("sesiones",sesiones,"em",em).entrySet()) {
            Field campo=ControlAcceso.class.getDeclaredField(entrada.getKey());
            campo.setAccessible(true);campo.set(control,entrada.getValue());
        }
        return control;
    }
    @Test void choferNoPuedeSuplantarOtraCi()throws Exception {
        var control=servicio(new Acceso(7,12345678,"Chofer",Set.of(TipoUsuario.CHOFER)),mock(EntityManager.class));
        assertEquals(403,assertThrows(ErrorAcceso.class,()->control.consultarChofer("token",null,87654321)).getEstado());
    }
    @Test void choferPuedeConsultarSuIdentidad()throws Exception {
        var control=servicio(new Acceso(7,12345678,"Chofer",Set.of(TipoUsuario.CHOFER)),mock(EntityManager.class));
        assertDoesNotThrow(()->control.consultarChofer("token",7,12345678));
    }
    @Test void noPuedeEnviarEventosParaGuiaAjena()throws Exception {
        var em=mock(EntityManager.class);var guia=new GuiaDeViaje();
        var otro=new Chofer();otro.setId(8);guia.setChofer(otro);
        when(em.find(GuiaDeViaje.class,10L)).thenReturn(guia);
        var control=servicio(new Acceso(7,12345678,"Chofer",Set.of(TipoUsuario.CHOFER)),em);
        assertEquals(403,assertThrows(ErrorAcceso.class,()->control.guiaChofer("token",10L)).getEstado());
    }
    @Test void ciudadanoConRolChoferPuedeUsarSuGuia()throws Exception {
        var em=mock(EntityManager.class);var guia=new GuiaDeViaje();
        var usuario=new Ciudadano();usuario.setId(7);guia.setChofer(usuario);
        when(em.find(GuiaDeViaje.class,10L)).thenReturn(guia);
        var control=servicio(new Acceso(7,12345678,"Persona",Set.of(TipoUsuario.CIUDADANO,TipoUsuario.CHOFER)),em);
        assertDoesNotThrow(()->control.guiaChofer("token",10L));
    }
}
