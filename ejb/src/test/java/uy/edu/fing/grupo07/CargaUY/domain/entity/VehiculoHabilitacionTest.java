package uy.edu.fing.grupo07.cargauy.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Habilitación de Vehículo, PNC e ITV")
class VehiculoHabilitacionTest {

    private Empresa empresa;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        empresa = new Empresa(1234, "Trans logistics S.A.", "TransLog", "Ruta 5 km 20");
        vehiculo = new Vehiculo(1001, "Scania", "R450", 14000, 30000, true, empresa);
    }

    @Test
    @DisplayName("Vehículo con PNC e ITV vigentes debe estar habilitado")
    void testVehiculoHabilitadoVigente() {
        PNC pnc = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        ITV itv = new ITV(LocalDate.now().plusMonths(3), vehiculo);

        vehiculo.getPncs().add(pnc);
        vehiculo.setItv(itv);

        assertTrue(vehiculo.verificarHabilitacionVigente(),
                "El vehículo debería estar habilitado con PNC e ITV vigentes");
    }

    @Test
    @DisplayName("Vehículo con PNC vencido NO debe estar habilitado")
    void testVehiculoConPncVencido() {
        PNC pnc = new PNC(LocalDate.now().minusDays(1), vehiculo);
        ITV itv = new ITV(LocalDate.now().plusMonths(3), vehiculo);

        vehiculo.getPncs().add(pnc);
        vehiculo.setItv(itv);

        assertFalse(vehiculo.verificarHabilitacionVigente(),
                "El vehículo NO debería estar habilitado con PNC vencido");
    }

    @Test
    @DisplayName("Vehículo con ITV vencido NO debe estar habilitado")
    void testVehiculoConItvVencido() {
        PNC pnc = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        ITV itv = new ITV(LocalDate.now().minusDays(1), vehiculo);

        vehiculo.getPncs().add(pnc);
        vehiculo.setItv(itv);

        assertFalse(vehiculo.verificarHabilitacionVigente(),
                "El vehículo NO debería estar habilitado con ITV vencido");
    }

    @Test
    @DisplayName("Vehículo con estadoHabilitacion en false NO debe estar habilitado")
    void testVehiculoDeshabilitadoAdministrativamente() {
        vehiculo.setEstadoHabilitacion(false);
        PNC pnc = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        ITV itv = new ITV(LocalDate.now().plusMonths(3), vehiculo);

        vehiculo.getPncs().add(pnc);
        vehiculo.setItv(itv);

        assertFalse(vehiculo.verificarHabilitacionVigente(),
                "El vehículo con estado administrativo falso no debe estar habilitado");
    }
}
