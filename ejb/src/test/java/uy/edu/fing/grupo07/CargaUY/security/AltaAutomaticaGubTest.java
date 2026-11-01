package uy.edu.fing.grupo07.CargaUY.security;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AltaAutomaticaGubTest {
    private EntityManager em;
    private SesionesService servicio;
    private TypedQuery<Usuario> identidad;
    private TypedQuery<Long> coincidencias;

    @BeforeEach @SuppressWarnings("unchecked")
    void preparar() throws Exception {
        em=mock(EntityManager.class); servicio=new SesionesService();
        var campo=SesionesService.class.getDeclaredField("em");
        campo.setAccessible(true); campo.set(servicio,em);
        identidad=mock(TypedQuery.class); coincidencias=mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(Usuario.class))).thenReturn(identidad);
        when(identidad.setParameter(eq("uid"),anyString())).thenReturn(identidad);
        when(identidad.getResultStream()).thenAnswer(inv->Stream.empty());
        when(em.createQuery(anyString(),eq(Long.class))).thenReturn(coincidencias);
        when(coincidencias.setParameter(eq("ci"),anyInt())).thenReturn(coincidencias);
        when(coincidencias.getSingleResult()).thenReturn(0L);
        doAnswer(inv->{Object objeto=inv.getArgument(0);if(objeto instanceof Usuario u)u.setId(42);return null;})
            .when(em).persist(any());
    }

    private GubOidcClient.Identidad datos() {
        return new GubOidcClient.Identidad("identidad-verificada",80000001,"Ana","ana@example.invalid",null);
    }

    @Test void primerIngresoCreaCiudadanoSinInventarNacimientoYEmiteSesion() {
        var login=servicio.emitirGub(datos());
        var captor=ArgumentCaptor.forClass(Ciudadano.class);
        verify(em).persist(captor.capture());
        Ciudadano nuevo=captor.getValue();
        assertNull(nuevo.getFechaNac());
        assertEquals("GUB",nuevo.getOrigenIdentidad());
        assertEquals("identidad-verificada",nuevo.getUidExterno());
        assertEquals(Set.of(TipoUsuario.CIUDADANO),nuevo.getRoles());
        assertEquals("{GUB}",nuevo.getPassword());
        assertEquals(42,login.usuario().id());
        assertEquals(43,login.token().length());
        verify(em).persist(any(SesionAcceso.class));
    }

    @Test void ingresoSiguienteReutilizaPerfilVinculadoSinOtorgarRolesNuevos() {
        var existente=ciudadano();
        existente.getRoles().add(TipoUsuario.CHOFER);
        when(identidad.getResultStream()).thenAnswer(inv->Stream.of(existente));
        var login=servicio.emitirGub(datos());
        assertEquals(7,login.usuario().id());
        assertEquals(Set.of(TipoUsuario.CIUDADANO,TipoUsuario.CHOFER),login.usuario().roles());
        verify(em,never()).persist(any(Usuario.class));
        verify(em,never()).createQuery(anyString(),eq(Long.class));
    }

    @Test void ciPreexistenteNoSeVinculaAutomaticamente() {
        when(coincidencias.getSingleResult()).thenReturn(1L);
        assertEquals(409,assertThrows(ErrorAcceso.class,()->servicio.emitirGub(datos())).getEstado());
        verify(em,never()).persist(any());
    }

    @Test void cuentaInactivaNoSeReactivaConElIngreso() {
        var existente=ciudadano();existente.setActivo(false);
        when(identidad.getResultStream()).thenAnswer(inv->Stream.of(existente));
        assertEquals(401,assertThrows(ErrorAcceso.class,()->servicio.emitirGub(datos())).getEstado());
        assertFalse(existente.isActivo());verify(em,never()).persist(any());
    }

    @Test void cuentaInternaNoPuedeIngresarPorGub() {
        var existente=ciudadano();existente.getRoles().add(TipoUsuario.ADMINISTRADOR);
        when(identidad.getResultStream()).thenAnswer(inv->Stream.of(existente));
        assertEquals(403,assertThrows(ErrorAcceso.class,()->servicio.emitirGub(datos())).getEstado());
        verify(em,never()).persist(any());
    }

    @Test void ciDeIdentidadDistintaSeRechaza() {
        var existente=ciudadano();existente.setCi(80000002);
        when(identidad.getResultStream()).thenAnswer(inv->Stream.of(existente));
        assertEquals(409,assertThrows(ErrorAcceso.class,()->servicio.emitirGub(datos())).getEstado());
        verify(em,never()).persist(any());
    }

    private Ciudadano ciudadano() {
        var u=new Ciudadano("Ana","ana@example.invalid",LocalDate.of(1990,1,1),80000001,"{GUB}");
        u.setId(7);u.setOrigenIdentidad("GUB");u.setUidExterno("identidad-verificada");
        u.getRoles().add(TipoUsuario.CIUDADANO);return u;
    }
}
