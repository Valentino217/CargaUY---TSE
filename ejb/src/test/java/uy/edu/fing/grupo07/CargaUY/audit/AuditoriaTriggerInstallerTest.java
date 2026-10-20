package uy.edu.fing.grupo07.CargaUY.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

@DisplayName("AuditoriaTriggerInstaller - instalación del trigger append-only")
class AuditoriaTriggerInstallerTest {

    @Test
    @DisplayName("Crea la función, borra el trigger previo y lo vuelve a crear, en ese orden")
    void instalaFuncionYTriggerEnOrden() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);

        new AuditoriaTriggerInstaller(em).instalar();

        InOrder orden = inOrder(em, query);
        orden.verify(em).createNativeQuery(AuditoriaTriggerInstaller.SQL_FUNCION);
        orden.verify(query).executeUpdate();
        orden.verify(em).createNativeQuery(AuditoriaTriggerInstaller.SQL_DROP_TRIGGER);
        orden.verify(query).executeUpdate();
        orden.verify(em).createNativeQuery(AuditoriaTriggerInstaller.SQL_CREATE_TRIGGER);
        orden.verify(query).executeUpdate();
    }

    @Test
    @DisplayName("Si la BD falla, no propaga la excepción (no bloquea el despliegue)")
    void noPropagaErroresDeBaseDeDatos() {
        EntityManager em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenThrow(new PersistenceException("sin postgres"));

        assertDoesNotThrow(() -> new AuditoriaTriggerInstaller(em).instalar());
    }
}
