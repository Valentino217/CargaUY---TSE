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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - VehiculoEJB (Habilitación por Fecha y Eliminación Segura)")
class VehiculoEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Long> qGuias;

    @Mock
    private TypedQuery<Long> qInfracciones;

    @Mock
    private TypedQuery<Long> qPesadas;

    private VehiculoEJB vehiculoEJB;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        vehiculoEJB = new VehiculoEJB(em);
        Empresa emp = new Empresa(10, "Transportes S.A.", "TransSA", "Ruta 1");
        vehiculo = new Vehiculo("STP 1234", "Volvo", "FH", 12000, 30000, true, emp);
    }

    @Test
    @DisplayName("estaAptoParaOperar con fechaReferencia evalúa ITV y PNC en la fecha de la guía")
    void testEstaAptoParaOperarPorFechaGuia() {
        LocalDate hoy = LocalDate.of(2026, 5, 10);
        vehiculo.getItv().add(new ITV(LocalDate.of(2026, 5, 20), vehiculo));
        vehiculo.getPncs().add(new PNC(LocalDate.of(2026, 5, 25), vehiculo));

        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);

        assertTrue(vehiculoEJB.estaAptoParaOperar("STP 1234", hoy));
        assertTrue(vehiculoEJB.estaAptoParaOperar("STP 1234", LocalDate.of(2026, 5, 20)));
        assertFalse(vehiculoEJB.estaAptoParaOperar("STP 1234", LocalDate.of(2026, 5, 21)));
        assertFalse(vehiculoEJB.estaAptoParaOperar("   ", hoy));
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

        vehiculoEJB.eliminar(vehiculo);

        verify(em, times(1)).remove(vehiculo);
    }
}
