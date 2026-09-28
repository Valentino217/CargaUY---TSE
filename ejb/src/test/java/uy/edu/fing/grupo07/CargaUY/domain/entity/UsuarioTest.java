package uy.edu.fing.grupo07.CargaUY.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Jerarquía de Usuario")
class UsuarioTest {

    @Test
    @DisplayName("Debe crear Funcionario con atributos heredados y específicos")
    void testCrearFuncionario() {
        Funcionario f = new Funcionario("Carlos Gomez", "carlos@fing.edu.uy",
                LocalDate.of(1980, 4, 12), 12345678, "hashed_pwd", 101, "Fiscalización");

        assertEquals("Carlos Gomez", f.getNombre());
        assertEquals("carlos@fing.edu.uy", f.getMail());
        assertEquals(12345678, f.getCi());
        assertEquals(101, f.getNroFuncionario());
        assertEquals("Fiscalización", f.getDepartamento());
    }

    @Test
    @DisplayName("Debe validar igualdad por CI")
    void testIgualdadUsuarioPorCi() {
        Ciudadano c1 = new Ciudadano("Maria Perez", "maria@mail.com",
                LocalDate.of(1990, 1, 1), 45678901, "hash1");
        Ciudadano c2 = new Ciudadano("Maria Perez Mod", "maria2@mail.com",
                LocalDate.of(1990, 1, 1), 45678901, "hash2");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    @DisplayName("Debe crear Chofer y asociar con lista de guías")
    void testCrearChofer() {
        Chofer chofer = new Chofer("Juan Conductor", "juan@transportes.uy",
                LocalDate.of(1985, 6, 20), 34567890, "hash");

        assertNotNull(chofer.getGuias());
        assertTrue(chofer.getGuias().isEmpty());
    }
}
