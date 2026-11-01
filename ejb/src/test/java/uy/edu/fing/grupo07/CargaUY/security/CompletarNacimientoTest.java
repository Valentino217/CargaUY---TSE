package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.persistence.*;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompletarNacimientoTest {
    private EntityManager em;
    private SesionesService servicio;
    private Ciudadano usuario;
    private String token;
    @BeforeEach void preparar()throws Exception {
        em=mock(EntityManager.class);servicio=new SesionesService();
        var f=SesionesService.class.getDeclaredField("em");f.setAccessible(true);f.set(servicio,em);
        usuario=new Ciudadano("Ana","ana@example.invalid",null,80000001,"{GUB}");
        usuario.setId(7);usuario.getRoles().add(TipoUsuario.CIUDADANO);
        token=Cripto.aleatorio();
        when(em.find(SesionAcceso.class,Cripto.hash(token)))
                .thenReturn(new SesionAcceso(Cripto.hash(token),usuario,Instant.now().plusSeconds(60)));
        when(em.find(Usuario.class,7)).thenReturn(usuario);
        when(em.find(Usuario.class,7,LockModeType.PESSIMISTIC_WRITE)).thenReturn(usuario);
    }
    @Test void apareceSoloMientrasFaltaLaFecha() {
        assertTrue(servicio.nacimientoPendiente(token));
        usuario.setFechaNac(LocalDate.of(1990,1,1));
        assertFalse(servicio.nacimientoPendiente(token));
    }
    @Test void guardaSoloLaFechaDelUsuarioAutenticado() {
        var fecha=LocalDate.of(1990,1,1);
        servicio.completarNacimiento(token,fecha);
        assertEquals(fecha,usuario.getFechaNac());
        assertEquals(Set.of(TipoUsuario.CIUDADANO),usuario.getRoles());
        assertFalse(servicio.nacimientoPendiente(token));verify(em).flush();
    }
    @Test void rechazaFechaVaciaYFuturaSinGuardar() {
        assertThrows(ErrorAcceso.class,()->servicio.completarNacimiento(token,null));
        assertThrows(ErrorAcceso.class,()->servicio.completarNacimiento(token,LocalDate.now().plusDays(1)));
        assertNull(usuario.getFechaNac());verify(em,never()).flush();
    }
    @Test void noSobrescribeFechaYaGuardadaEnOtraPestana() {
        usuario.setFechaNac(LocalDate.of(1990,1,1));
        assertEquals(409,assertThrows(ErrorAcceso.class,()->servicio.completarNacimiento(token,
                LocalDate.of(2000,1,1))).getEstado());
        assertEquals(LocalDate.of(1990,1,1),usuario.getFechaNac());
    }
    @Test void noPermiteGuardarSinSesion() {
        assertThrows(ErrorAcceso.class,()->servicio.completarNacimiento(null,LocalDate.of(1990,1,1)));
        verify(em,never()).flush();
    }
}
