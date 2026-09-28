package uy.edu.fing.grupo07.CargaUY.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Inmutabilidad de RegistroAuditoria (R005 / AC004)")
class RegistroAuditoriaTest {

    @Test
    @DisplayName("Debe registrar auditoría con datos completos y marca de tiempo")
    void testCreacionRegistroAuditoria() {
        LocalDate hoy = LocalDate.now();
        RegistroAuditoria audit = new RegistroAuditoria(
                "CONFIRMADA", hoy, "funcionario_admin", "CONFIRMAR_INFRACCION",
                "Exceso de peso corroborado mediante báscula fiscalizadora");

        assertEquals("CONFIRMADA", audit.getResolucion());
        assertEquals(hoy, audit.getFecha());
        assertEquals("funcionario_admin", audit.getUsuario());
        assertEquals("CONFIRMAR_INFRACCION", audit.getAccion());
        assertEquals("Exceso de peso corroborado mediante báscula fiscalizadora", audit.getFundamento());
        assertNotNull(audit.getTimestampCreacion(), "La fecha/hora de creación no debe ser nula");
    }

    @Test
    @DisplayName("RegistroAuditoria NO debe tener ningún setter público (Inmutabilidad garantizada)")
    void testInmutabilidadSinSetters() {
        Method[] metodos = RegistroAuditoria.class.getDeclaredMethods();
        for (Method m : metodos) {
            if (m.getName().startsWith("set") && Modifier.isPublic(m.getModifiers())) {
                fail("La clase RegistroAuditoria no debe tener setters públicos para garantizar append-only: " + m.getName());
            }
        }
    }
}
