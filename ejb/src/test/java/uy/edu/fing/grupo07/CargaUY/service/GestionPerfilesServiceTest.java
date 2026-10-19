package uy.edu.fing.grupo07.CargaUY.service;

import java.lang.reflect.Field;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionPerfilesServiceTest {
    private GestionPerfilesService servicio(EntityManager em) throws Exception {
        GestionPerfilesService s = new GestionPerfilesService();
        Field f = GestionPerfilesService.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(s, em);
        Field seguridad=GestionPerfilesService.class.getDeclaredField("control");
        seguridad.setAccessible(true);
        var control=mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        when(control.admin("token")).thenReturn(new uy.edu.fing.grupo07.CargaUY.security.Acceso(
            99,90000001,"Admin",java.util.Set.of(TipoUsuario.ADMINISTRADOR)));
        seguridad.set(s,control);
        Field sesiones=GestionPerfilesService.class.getDeclaredField("sesiones");
        sesiones.setAccessible(true);sesiones.set(s,mock(uy.edu.fing.grupo07.CargaUY.security.SesionesService.class));
        return s;
    }
    @Test void asociacionRepetidaNoDuplicaNingunLado() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Empresa empresa = new Empresa(10, "Empresa SA", "Empresa", "Montevideo");
        Ciudadano ciudadano = new Ciudadano("Ana", "ana@example.com", LocalDate.of(1990,1,1),
            12345678, "hash-de-prueba");
        ciudadano.setId(7);
        ciudadano.getRoles().add(TipoUsuario.CIUDADANO);
        when(em.find(Empresa.class, 10)).thenReturn(empresa);
        when(em.find(Ciudadano.class, 7)).thenReturn(ciudadano);
        GestionPerfilesService s = servicio(em);
        s.asociarCiudadano("token", 10, 7);
        s.asociarCiudadano("token", 10, 7);
        assertEquals(1, empresa.getCiudadanos().size());
        assertEquals(1, ciudadano.getEmpresas().size());
        s.desasociarCiudadano("token", 10, 7);
        assertTrue(empresa.getCiudadanos().isEmpty());
        assertTrue(ciudadano.getEmpresas().isEmpty());
    }
    @Test void ciudadanoInactivoNoPuedeAsociarse() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Empresa e = new Empresa(10, "Empresa SA", "Empresa", "Montevideo");
        Ciudadano c = new Ciudadano();
        c.setActivo(false);
        when(em.find(Empresa.class, 10)).thenReturn(e);
        when(em.find(Ciudadano.class, 7)).thenReturn(c);
        assertThrows(ErrorNegocio.class, () -> servicio(em).asociarCiudadano("token", 10, 7));
        assertTrue(e.getCiudadanos().isEmpty());
    }
    @Test void bajaNoEliminaElRegistro() throws Exception {
        EntityManager em = mock(EntityManager.class);
        Ciudadano c = new Ciudadano();
        when(em.find(Usuario.class, 7)).thenReturn(c);
        servicio(em).cambiarEstadoUsuario("token", 7, false);
        assertFalse(c.isActivo());
        verify(em, never()).remove(any());
    }
    @Test void resumenNoIncluyeCredencial() {
        Ciudadano c = new Ciudadano("Ana", "ana@example.com", LocalDate.of(1990,1,1),
            12345678, "secreto-que-no-se-debe-mostrar");
        assertFalse(UsuarioResumen.desde(c).toString().contains(c.getPassword()));
    }

    @Test @SuppressWarnings("unchecked")
    void altaGuardaHashEnLugarDeContrasenaPlana() throws Exception {
        EntityManager em = mock(EntityManager.class);
        jakarta.persistence.TypedQuery<Long> q = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery("select count(u) from Usuario u where u.ci = :ci", Long.class)).thenReturn(q);
        when(q.setParameter("ci", 12345678)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(0L);
        java.util.concurrent.atomic.AtomicReference<Usuario> guardado =
            new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocacion -> {
            Usuario u = invocacion.getArgument(0); u.setId(7); guardado.set(u); return null;
        }).when(em).persist(any(Usuario.class));
        DatosUsuario datos = new DatosUsuario(TipoUsuario.CIUDADANO, "Ana",
            "ana@example.com", LocalDate.of(1990,1,1), 12345678,
            "ClavePrueba123!", null, null, null);
        assertEquals(7, servicio(em).crearUsuario("token", datos));
        assertNotEquals(datos.password(), guardado.get().getPassword());
        assertTrue(at.favre.lib.crypto.bcrypt.BCrypt.verifyer()
            .verify(datos.password().toCharArray(), guardado.get().getPassword()).verified);
    }
    @Test @SuppressWarnings("unchecked")
    void altaRechazaCedulaDuplicada() throws Exception {
        EntityManager em = mock(EntityManager.class);
        jakarta.persistence.TypedQuery<Long> q = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery("select count(u) from Usuario u where u.ci = :ci", Long.class)).thenReturn(q);
        when(q.setParameter("ci", 12345678)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(1L);
        DatosUsuario datos = new DatosUsuario(TipoUsuario.CIUDADANO, "Ana",
            "ana@example.com", LocalDate.of(1990,1,1), 12345678,
            "ClavePrueba123!", null, null, null);
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearUsuario("token", datos));
        verify(em, never()).persist(any());
    }
}