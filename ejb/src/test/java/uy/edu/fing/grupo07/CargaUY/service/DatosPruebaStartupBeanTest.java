package uy.edu.fing.grupo07.CargaUY.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;

@DisplayName("DatosPruebaStartupBean - Inicialización de datos de prueba al arranque")
class DatosPruebaStartupBeanTest {

    @Test
    @DisplayName("Carga datos de prueba cuando la base de datos no contiene guías")
    void inicializaDatosCuandoBaseDeDatosEstaVacia() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> consultaConteo = mock(TypedQuery.class);

        when(entityManager.createQuery("SELECT COUNT(g) FROM GuiaDeViaje g", Long.class))
                .thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(0L);

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);
        startupBean.inicializar();

        verify(entityManager, atLeastOnce()).persist(any(GuiaDeViaje.class));
    }

    @Test
    @DisplayName("Omite la carga si ya existen guías registradas en la base de datos")
    void omiteCargaCuandoYaExistenGuias() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> consultaConteo = mock(TypedQuery.class);

        when(entityManager.createQuery("SELECT COUNT(g) FROM GuiaDeViaje g", Long.class))
                .thenReturn(consultaConteo);
        when(consultaConteo.getSingleResult()).thenReturn(3L);

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);
        startupBean.inicializar();

        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("No propaga excepciones si ocurre un error de persistencia durante el arranque")
    void noPropagaExcepcionSiFallaBaseDeDatos() {
        EntityManager entityManager = mock(EntityManager.class);

        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenThrow(new PersistenceException("Error simulado de conexión a la base de datos"));

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);

        assertDoesNotThrow(() -> {
            startupBean.inicializar();
        });
    }
}
