package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Ciudadano;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.VinculoEmpresa;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - ControlAcceso (vehiculoChofer)")
class ControlAccesoTest {

    private ControlAcceso crearControl(Acceso actor, EntityManager em) throws Exception {
        SesionesService sesiones = mock(SesionesService.class);
        when(sesiones.validarToken("token")).thenReturn(actor);
        ControlAcceso control = new ControlAcceso();
        Field fSesiones = ControlAcceso.class.getDeclaredField("sesiones");
        fSesiones.setAccessible(true);
        fSesiones.set(control, sesiones);
        Field fEm = ControlAcceso.class.getDeclaredField("em");
        fEm.setAccessible(true);
        fEm.set(control, em);
        return control;
    }

    @Test
    @DisplayName("Administrador accede a vehiculoChofer sin requerir vínculo de empresa")
    void testAdministradorPermitido() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ControlAcceso control = crearControl(
                new Acceso(1, 90000001, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)),
                em
        );

        assertDoesNotThrow(() -> control.vehiculoChofer("token", "STP1234"));
        verifyNoInteractions(em);
    }

    @Test
    @DisplayName("Chofer con matrícula vacía recibe 400")
    void testMatriculaObligatoriaParaChofer() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ControlAcceso control = crearControl(
                new Acceso(7, 12345678, "Chofer", Set.of(TipoUsuario.CHOFER)),
                em
        );

        ErrorAcceso ex = assertThrows(ErrorAcceso.class, () -> control.vehiculoChofer("token", "  "));
        assertEquals(400, ex.getEstado());
    }

    @Test
    @DisplayName("Chofer recibe 403 si vehículo inexistente, sin empresa o empresa inactiva")
    void testVehiculoSinEmpresaOInactiva() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ControlAcceso control = crearControl(
                new Acceso(7, 12345678, "Chofer", Set.of(TipoUsuario.CHOFER)),
                em
        );

        when(em.find(Vehiculo.class, "NOEXISTE")).thenReturn(null);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> control.vehiculoChofer("token", "noexiste")).getEstado());

        Empresa inactiva = new Empresa(10, "Inactiva S.A.", "Inactiva", "Dir");
        inactiva.setActiva(false);
        Vehiculo vInactiva = new Vehiculo("STP1000", "Scania", "R", 8000, 20000, true, inactiva);
        Ciudadano chofer = new Ciudadano();
        chofer.setId(7);
        VinculoEmpresa vinculo = new VinculoEmpresa(chofer, inactiva);
        vinculo.setChofer(true);

        when(em.find(Vehiculo.class, "STP1000")).thenReturn(vInactiva);
        when(em.find(VinculoEmpresa.class, VinculoEmpresa.clave(10, 7))).thenReturn(vinculo);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> control.vehiculoChofer("token", "stp1000")).getEstado());
    }

    @Test
    @DisplayName("Chofer sin vínculo de chofer activo recibe 403 y chofer vinculado válido pasa")
    void testVinculoChoferValidoEInvalido() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ControlAcceso control = crearControl(
                new Acceso(7, 12345678, "Chofer", Set.of(TipoUsuario.CHOFER)),
                em
        );

        Empresa activa = new Empresa(20, "TransActiva S.A.", "TransActiva", "Ruta 5");
        activa.setActiva(true);
        Vehiculo vehiculo = new Vehiculo("STP2020", "Volvo", "FH", 9000, 28000, true, activa);
        Ciudadano chofer = new Ciudadano();
        chofer.setId(7);

        when(em.find(Vehiculo.class, "STP2020")).thenReturn(vehiculo);
        when(em.find(VinculoEmpresa.class, VinculoEmpresa.clave(20, 7))).thenReturn(null);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> control.vehiculoChofer("token", " stp2020 ")).getEstado());

        VinculoEmpresa soloResponsable = new VinculoEmpresa(chofer, activa);
        soloResponsable.setResponsable(true);
        soloResponsable.setChofer(false);
        when(em.find(VinculoEmpresa.class, VinculoEmpresa.clave(20, 7))).thenReturn(soloResponsable);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> control.vehiculoChofer("token", "STP2020")).getEstado());

        soloResponsable.setChofer(true);
        assertDoesNotThrow(() -> control.vehiculoChofer("token", " stp2020 "));
    }
}
