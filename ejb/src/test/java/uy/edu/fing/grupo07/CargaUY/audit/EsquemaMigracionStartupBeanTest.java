package uy.edu.fing.grupo07.CargaUY.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - EsquemaMigracionStartupBean")
class EsquemaMigracionStartupBeanTest {

    @Mock
    private EntityManager em;

    @Mock
    private Query nativeQuery;

    @Test
    @DisplayName("Debe ejecutar todas las sentencias DDL idempotentes de migración incremental")
    void testMigrarEsquemaEjecutaTodasLasSentencias() {
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(0);

        EsquemaMigracionStartupBean bean = new EsquemaMigracionStartupBean(em);
        bean.migrarEsquema();

        verify(em, times(EsquemaMigracionStartupBean.SENTENCIAS_DDL.size())).createNativeQuery(anyString());
        verify(nativeQuery, times(EsquemaMigracionStartupBean.SENTENCIAS_DDL.size())).executeUpdate();
    }

    @Test
    @DisplayName("No debe abortar el arranque si ocurre una excepción de dialecto SQL no PostgreSQL")
    void testMigrarEsquemaToleraFalloDialecto() {
        when(em.createNativeQuery(anyString())).thenThrow(new RuntimeException("H2 syntax mismatch"));

        EsquemaMigracionStartupBean bean = new EsquemaMigracionStartupBean(em);
        assertDoesNotThrow(bean::migrarEsquema);
    }
}
