package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;

class DatosPruebaStartupBeanTest {
    private String anterior;
    @BeforeEach void guardarPropiedad() { anterior=System.getProperty("p3.datos.prueba"); }
    @AfterEach void restaurarPropiedad() {
        if(anterior==null) System.clearProperty("p3.datos.prueba");
        else System.setProperty("p3.datos.prueba",anterior);
    }
    @Test void noTocaLaBasePorDefecto() {
        System.clearProperty("p3.datos.prueba");
        EntityManager em=mock(EntityManager.class);
        new DatosPruebaStartupBean(em).inicializar();
        verifyNoInteractions(em);
    }
    @Test void nuncaReemplazaUsuariosExistentesInclusoSiSeHabilita() {
        System.setProperty("p3.datos.prueba","true");
        EntityManager em=mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<Long> q=mock(TypedQuery.class);
        when(em.createQuery("select count(u) from Usuario u",Long.class)).thenReturn(q);
        when(q.getSingleResult()).thenReturn(3L);
        new DatosPruebaStartupBean(em).inicializar();
        verify(em,never()).createNativeQuery(anyString());
        verify(em,never()).persist(any());
    }
    @Test void deshabilitacionExplicitaNoConsultaNiModifica() {
        System.setProperty("p3.datos.prueba","false");
        EntityManager em=mock(EntityManager.class);
        new DatosPruebaStartupBean(em).inicializar();
        verifyNoInteractions(em);
    }
}
