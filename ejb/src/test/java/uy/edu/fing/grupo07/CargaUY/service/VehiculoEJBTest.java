package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - VehiculoEJB")
class VehiculoEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private ControlAcceso control;

    @Mock
    private TypedQuery<Long> qGuias;

    @Mock
    private TypedQuery<Long> qInfracciones;

    @Mock
    private TypedQuery<Long> qPesadas;

    @Mock
    private TypedQuery<Vehiculo> qVehiculos;

    @Mock
    private TypedQuery<Empresa> qEmpresas;

    private VehiculoEJB vehiculoEJB;
    private Empresa emp;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() throws Exception {
        vehiculoEJB = new VehiculoEJB();
        Field emField = VehiculoEJB.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(vehiculoEJB, em);

        Field controlField = VehiculoEJB.class.getDeclaredField("control");
        controlField.setAccessible(true);
        controlField.set(vehiculoEJB, control);

        emp = new Empresa(10, "Transportes S.A.", "TransSA", "Ruta 1");
        vehiculo = new Vehiculo("STP 1234", "Volvo", "FH", 12000, 30000, true, emp);
    }

    @Test
    @DisplayName("listarParaActor como ADMINISTRADOR delega a listarVehiculos")
    void testListarParaActorAdmin() {
        when(control.exigir("token")).thenReturn(new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)));
        when(em.createQuery(anyString(), eq(Vehiculo.class))).thenReturn(qVehiculos);
        when(qVehiculos.getResultList()).thenReturn(List.of(vehiculo));

        List<Vehiculo> res = vehiculoEJB.listarParaActor("token");
        assertEquals(1, res.size());
        assertEquals("STP 1234", res.get(0).getMatricula());
    }

    @Test
    @DisplayName("listarParaActor como CIUDADANO filtra por vínculo responsable")
    void testListarParaActorCiudadano() {
        when(control.exigir("token")).thenReturn(new Acceso(7, 22222222, "Ciudadano", Set.of(TipoUsuario.CIUDADANO)));
        when(em.createQuery(contains("VinculoEmpresa"), eq(Vehiculo.class))).thenReturn(qVehiculos);
        when(qVehiculos.setParameter("id", 7)).thenReturn(qVehiculos);
        when(qVehiculos.getResultList()).thenReturn(List.of(vehiculo));

        List<Vehiculo> res = vehiculoEJB.listarParaActor("token");
        assertEquals(1, res.size());
        verify(qVehiculos).setParameter("id", 7);
    }

    @Test
    @DisplayName("listarEmpresasParaActor como ADMINISTRADOR y como CIUDADANO")
    void testListarEmpresasParaActor() {
        when(control.exigir("tokenAdmin")).thenReturn(new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)));
        when(em.createQuery(contains("ORDER BY e.razonSocial"), eq(Empresa.class))).thenReturn(qEmpresas);
        when(qEmpresas.getResultList()).thenReturn(List.of(emp));

        assertEquals(1, vehiculoEJB.listarEmpresasParaActor("tokenAdmin").size());

        when(control.exigir("tokenCiu")).thenReturn(new Acceso(7, 22222222, "Ciudadano", Set.of(TipoUsuario.CIUDADANO)));
        when(em.createQuery(contains("VinculoEmpresa"), eq(Empresa.class))).thenReturn(qEmpresas);
        when(qEmpresas.setParameter("id", 7)).thenReturn(qEmpresas);
        when(qEmpresas.getResultList()).thenReturn(List.of(emp));

        assertEquals(1, vehiculoEJB.listarEmpresasParaActor("tokenCiu").size());
    }

    @Test
    @DisplayName("listarVehiculosPorEmpresa con token valida acceso y retorna lista")
    void testListarVehiculosPorEmpresaConToken() {
        when(em.createQuery(contains("v.empresa.nroEmpresa = :nroEmpresa"), eq(Vehiculo.class))).thenReturn(qVehiculos);
        when(qVehiculos.setParameter("nroEmpresa", 10)).thenReturn(qVehiculos);
        when(qVehiculos.getResultList()).thenReturn(List.of(vehiculo));

        List<Vehiculo> res = vehiculoEJB.listarVehiculosPorEmpresa("token", 10);
        assertEquals(1, res.size());
        verify(control).empresa("token", 10, false);
    }

    @Test
    @DisplayName("buscar con token y sin token maneja nulos y normalización a mayúsculas")
    void testBuscarVehiculo() {
        assertNull(vehiculoEJB.buscar(null));
        assertNull(vehiculoEJB.buscar("token", "NOEXISTE"));

        when(em.find(Vehiculo.class, "stp 1234")).thenReturn(null);
        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);

        Vehiculo encontrado = vehiculoEJB.buscar("token", "stp 1234");
        assertNotNull(encontrado);
        assertEquals("STP 1234", encontrado.getMatricula());
        verify(control).empresa("token", 10, false);
    }

    @Test
    @DisplayName("crear con token valida matrícula obligatoria, duplicados y normaliza")
    void testCrearConToken() {
        assertThrows(IllegalArgumentException.class, () -> vehiculoEJB.crear("token", null));
        Vehiculo sinMat = new Vehiculo("   ", "Volvo", "FH", 1000, 2000, true, emp);
        assertThrows(IllegalArgumentException.class, () -> vehiculoEJB.crear("token", sinMat));

        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);
        Vehiculo dup = new Vehiculo(" stp 1234 ", "Volvo", "FH", 1000, 2000, true, emp);
        assertThrows(IllegalArgumentException.class, () -> vehiculoEJB.crear("token", dup));

        when(em.find(Vehiculo.class, "NUE 9999")).thenReturn(null);
        Vehiculo nuevo = new Vehiculo(" nue 9999 ", "Scania", "R450", 11000, 28000, true, emp);
        vehiculoEJB.crear("token", nuevo);
        assertEquals("NUE 9999", nuevo.getMatricula());
        verify(control, atLeastOnce()).empresa("token", 10, true);
        verify(em).persist(nuevo);
    }

    @Test
    @DisplayName("modificar con token y sin token actualiza campos, certificados nuevos y valida guía EN_CURSO")
    void testModificarVehiculo() {
        when(em.find(Vehiculo.class, "NOEXISTE")).thenReturn(null);
        Vehiculo inexistente = new Vehiculo("NOEXISTE", "Volvo", "FH", 1000, 2000, true, emp);
        assertThrows(IllegalArgumentException.class, () -> vehiculoEJB.modificar("token", inexistente));

        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);
        when(em.createQuery(contains("g.estado = :enCurso"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter("mat", "STP 1234")).thenReturn(qGuias);
        when(qGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> vehiculoEJB.modificar("token", vehiculo));
        assertThrows(IllegalStateException.class, () -> vehiculoEJB.modificar(vehiculo));

        // Caso exitoso con PNC e ITV nuevos (nro == 0) y existentes (nro > 0)
        when(qGuias.getSingleResult()).thenReturn(0L);
        Vehiculo editado = new Vehiculo("STP 1234", "Mercedes", "Actros", 13000, 32000, false, emp);
        PNC pncNuevo = new PNC(LocalDate.now().plusDays(30), null);
        pncNuevo.setNro(0);
        PNC pncExistente = new PNC(LocalDate.now().plusDays(10), vehiculo);
        pncExistente.setNro(5);
        editado.setPncs(new ArrayList<>(List.of(pncNuevo, pncExistente)));

        ITV itvNuevo = new ITV(LocalDate.now().plusDays(60), null);
        itvNuevo.setNroCertificado(0);
        ITV itvExistente = new ITV(LocalDate.now().plusDays(15), vehiculo);
        itvExistente.setNroCertificado(8);
        editado.setItv(new ArrayList<>(List.of(itvNuevo, itvExistente)));

        vehiculoEJB.modificar("token", editado);

        assertEquals("Mercedes", vehiculo.getMarca());
        assertEquals("Actros", vehiculo.getModelo());
        assertEquals(13000, vehiculo.getPeso());
        assertEquals(32000, vehiculo.getCapacidadCarga());
        assertFalse(vehiculo.isEstadoHabilitacion());
        assertEquals(1, vehiculo.getPncs().size());
        assertEquals(1, vehiculo.getItv().size());

        // Modificar directamente cuando no existe en BD -> hace merge
        when(em.find(Vehiculo.class, "OTR 1111")).thenReturn(null);
        when(qGuias.setParameter("mat", "OTR 1111")).thenReturn(qGuias);
        Vehiculo otro = new Vehiculo("OTR 1111", "MAN", "TGX", 10000, 25000, true, emp);
        vehiculoEJB.modificar(otro);
        verify(em).merge(otro);
    }

    @Test
    @DisplayName("tieneGuiaEnCurso retorna false para matrícula nula o vacía")
    void testTieneGuiaEnCursoVacia() {
        assertFalse(vehiculoEJB.tieneGuiaEnCurso(null));
        assertFalse(vehiculoEJB.tieneGuiaEnCurso("   "));
    }

    @Test
    @DisplayName("eliminar con token y sin token valida guías, infracciones y pesadas")
    void testEliminarConTokenYSinToken() {
        when(em.find(Vehiculo.class, "NOEXISTE")).thenReturn(null);
        Vehiculo inexistente = new Vehiculo("NOEXISTE", "Volvo", "FH", 1000, 2000, true, emp);
        assertThrows(IllegalArgumentException.class, () -> vehiculoEJB.eliminar("token", inexistente));

        // Bloquea si tiene guías asociadas
        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter("mat", "STP 1234")).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> vehiculoEJB.eliminar(vehiculo));
    }

    @Test
    @DisplayName("eliminar bloquea baja si el vehículo tiene expedientes VIAJE_SIN_GUIA asociados")
    void testEliminarBloqueaSiTieneInfraccionesSinGuia() {
        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter("mat", "STP 1234")).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(0L);

        when(em.createQuery(contains("FROM Infraccion"), eq(Long.class))).thenReturn(qInfracciones);
        when(qInfracciones.setParameter("mat", "STP 1234")).thenReturn(qInfracciones);
        when(qInfracciones.getSingleResult()).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> vehiculoEJB.eliminar(vehiculo));
        assertTrue(ex.getMessage().contains("expediente(s) de infracción"));
        verify(em, never()).remove(any());
    }

    @Test
    @DisplayName("eliminar bloquea baja si el vehículo tiene pesajes sin guía asociados")
    void testEliminarBloqueaSiTienePesadasSinGuia() {
        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter("mat", "STP 1234")).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(0L);

        when(em.createQuery(contains("FROM Infraccion"), eq(Long.class))).thenReturn(qInfracciones);
        when(qInfracciones.setParameter("mat", "STP 1234")).thenReturn(qInfracciones);
        when(qInfracciones.getSingleResult()).thenReturn(0L);

        when(em.createQuery(contains("FROM PesadaBalanza"), eq(Long.class))).thenReturn(qPesadas);
        when(qPesadas.setParameter("mat", "STP 1234")).thenReturn(qPesadas);
        when(qPesadas.getSingleResult()).thenReturn(2L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> vehiculoEJB.eliminar(vehiculo));
        assertTrue(ex.getMessage().contains("pesaje en balanza"));
        verify(em, never()).remove(any());
    }

    @Test
    @DisplayName("eliminar remueve vehículo cuando no tiene guías, infracciones ni pesadas")
    void testEliminarExitosoSinReferencias() {
        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);
        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter("mat", "STP 1234")).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(0L);

        when(em.createQuery(contains("FROM Infraccion"), eq(Long.class))).thenReturn(qInfracciones);
        when(qInfracciones.setParameter("mat", "STP 1234")).thenReturn(qInfracciones);
        when(qInfracciones.getSingleResult()).thenReturn(0L);

        when(em.createQuery(contains("FROM PesadaBalanza"), eq(Long.class))).thenReturn(qPesadas);
        when(qPesadas.setParameter("mat", "STP 1234")).thenReturn(qPesadas);
        when(qPesadas.getSingleResult()).thenReturn(0L);

        when(em.merge(vehiculo)).thenReturn(vehiculo);

        vehiculoEJB.eliminar("token", vehiculo);

        verify(control).empresa("token", 10, true);
        verify(em, times(1)).remove(vehiculo);
    }

    @Test
    @DisplayName("listarVehiculosPorCiudadano y listarEmpresasPorCiudadano ejecutan consultas")
    void testConsultasPorCiudadano() throws Exception {
        when(em.createQuery(contains(":ciudadanoId"), eq(Vehiculo.class))).thenReturn(qVehiculos);
        when(qVehiculos.setParameter("ciudadanoId", 5)).thenReturn(qVehiculos);
        when(qVehiculos.getResultList()).thenReturn(List.of(vehiculo));
        assertEquals(1, vehiculoEJB.listarVehiculosPorCiudadano(5).size());

        when(em.createQuery(contains(":ciudadanoId"), eq(Empresa.class))).thenReturn(qEmpresas);
        when(qEmpresas.setParameter("ciudadanoId", 5)).thenReturn(qEmpresas);
        when(qEmpresas.getResultList()).thenReturn(List.of(emp));
        assertEquals(1, vehiculoEJB.listarEmpresasPorCiudadano(5).size());

        Method m = VehiculoEJB.class.getDeclaredMethod("listarVehiculosInternalUnused");
        m.setAccessible(true);
        when(em.createQuery(contains("LEFT JOIN FETCH v.empresa"), eq(Vehiculo.class))).thenReturn(qVehiculos);
        m.invoke(vehiculoEJB);
    }

    @Test
    @DisplayName("estaAptoParaOperar con fechaReferencia y hoy evalúa ITV y PNC")
    void testEstaAptoParaOperarPorFechaGuia() {
        VehiculoEJB ejbConConstructor = new VehiculoEJB(em);
        LocalDate hoy = LocalDate.now();
        vehiculo.getItv().add(new ITV(hoy.plusDays(10), vehiculo));
        vehiculo.getPncs().add(new PNC(hoy.plusDays(15), vehiculo));

        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);

        assertTrue(ejbConConstructor.estaAptoParaOperar("STP 1234"));
        assertTrue(ejbConConstructor.estaAptoParaOperar("STP 1234", hoy.plusDays(10)));
        assertFalse(ejbConConstructor.estaAptoParaOperar("STP 1234", hoy.plusDays(11)));
        assertFalse(ejbConConstructor.estaAptoParaOperar("   ", hoy));
        assertFalse(ejbConConstructor.estaAptoParaOperar(null, hoy));
    }
}
