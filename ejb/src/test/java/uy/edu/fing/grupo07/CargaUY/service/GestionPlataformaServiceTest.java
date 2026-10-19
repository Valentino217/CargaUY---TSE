package uy.edu.fing.grupo07.CargaUY.service;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionPlataformaServiceTest {
    private GestionPlataformaService servicio(EntityManager em) throws Exception {
        GestionPlataformaService s = new GestionPlataformaService();
        Field f = GestionPlataformaService.class.getDeclaredField("em");
        f.setAccessible(true); f.set(s, em);
        Field seguridad=GestionPlataformaService.class.getDeclaredField("control");
        seguridad.setAccessible(true);seguridad.set(s,mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class));
        return s;
    }
    @Test void rechazaUnaVersionVieja() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        assertThrows(ErrorNegocio.class, () -> servicio(em).editarRegla("token", 100, 0.5, 99));
        assertEquals(500, regla.getUmbralCarga());
    }
    @Test void permiteKilometrosFraccionarios() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        servicio(em).editarRegla("token", 100, 0.5, 0);
        assertEquals(0.5, regla.getUmbralTracking());
        verify(em).flush();
    }
    @Test void rechazaNaN() throws Exception {
        EntityManager em = mock(EntityManager.class);
        assertThrows(ErrorNegocio.class, () -> servicio(em).crearRegla("token", 500, Double.NaN));
        verify(em, never()).persist(any());
    }
}