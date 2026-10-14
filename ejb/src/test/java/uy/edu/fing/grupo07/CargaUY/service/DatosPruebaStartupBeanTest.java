package uy.edu.fing.grupo07.CargaUY.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;

@DisplayName("DatosPruebaStartupBean - Inicialización de datos de prueba al arranque")
class DatosPruebaStartupBeanTest {

    @Test
    @DisplayName("Limpia la base de datos y carga los datos de prueba en cada arranque")
    void inicializaYLimpiaDatosAlArranque() {
        EntityManager entityManager = mock(EntityManager.class);
        Query consultaTruncate = mock(Query.class);

        when(entityManager.createNativeQuery(anyString())).thenReturn(consultaTruncate);

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);
        startupBean.inicializar();

        verify(entityManager, atLeastOnce()).createNativeQuery(anyString());
        verify(consultaTruncate, atLeastOnce()).executeUpdate();
        verify(entityManager, atLeastOnce()).persist(any(GuiaDeViaje.class));
    }

    @Test
    @DisplayName("Tolera fallo en el truncate si las tablas aún no existen y continúa con la carga")
    void toleraFalloEnTruncateYContinua() {
        EntityManager entityManager = mock(EntityManager.class);

        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new RuntimeException("Error simulado: tablas aún no creadas"));

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);
        startupBean.inicializar();

        verify(entityManager, atLeastOnce()).persist(any(GuiaDeViaje.class));
    }

    @Test
    @DisplayName("No propaga excepciones si ocurre un error fatal durante la inicialización")
    void noPropagaExcepcionSiFallaPersistencia() {
        EntityManager entityManager = mock(EntityManager.class);

        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new PersistenceException("Error simulado de conexión"));

        DatosPruebaStartupBean startupBean = new DatosPruebaStartupBean(entityManager);

        assertDoesNotThrow(() -> {
            startupBean.inicializar();
        });
    }
}
