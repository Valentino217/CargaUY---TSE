package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.TipoCarga;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;
import uy.edu.fing.grupo07.CargaUY.service.DashboardServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJBLocal;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - PortalPublicoBean")
class PortalPublicoBeanTest {

    private PortalPublicoBean crearBean(
            DashboardServiceLocal dashboardService,
            VehiculoEJBLocal vehiculoEJB,
            GuiaDeViajeEJBLocal guiaDeViajeEJB
    ) throws Exception {
        PortalPublicoBean bean = new PortalPublicoBean();
        for (Map.Entry<String, Object> entry : Map.of(
                "dashboardService", dashboardService,
                "vehiculoEJB", vehiculoEJB,
                "guiaDeViajeEJB", guiaDeViajeEJB
        ).entrySet()) {
            Field f = PortalPublicoBean.class.getDeclaredField(entry.getKey());
            f.setAccessible(true);
            f.set(bean, entry.getValue());
        }
        return bean;
    }

    @Test
    @DisplayName("Sin sesión autenticada carga solo KPIs agregados públicos y omite nóminas de portal")
    void testSinSesionCargaSoloResumenPublico() throws Exception {
        DashboardServiceLocal dashboard = mock(DashboardServiceLocal.class);
        VehiculoEJBLocal vehiculoEJB = mock(VehiculoEJBLocal.class);
        GuiaDeViajeEJBLocal guiaEJB = mock(GuiaDeViajeEJBLocal.class);

        DashboardResumenDTO resumenPublico = new DashboardResumenDTO(
                Map.of(TipoEstado.EN_CURSO, 5L, TipoEstado.SIN_INICIAR, 2L),
                Map.of(),
                Map.of(),
                12L,
                4L,
                9L,
                Map.of(),
                Map.of(),
                0.0
        );
        when(dashboard.obtenerResumenPublico()).thenReturn(resumenPublico);

        PortalPublicoBean bean = crearBean(dashboard, vehiculoEJB, guiaEJB);
        try (MockedStatic<SesionActual> sesion = Mockito.mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::token).thenReturn(null);
            bean.cargarDatosPublicos();
        }

        verify(dashboard).obtenerResumenPublico();
        verify(vehiculoEJB, never()).listarEmpresasParaPortal(anyString());
        verify(guiaEJB, never()).listarParaPortal(anyString());
        assertTrue(bean.getEmpresasPublicas().isEmpty());
        assertTrue(bean.getVehiculosPublicos().isEmpty());
        assertTrue(bean.getGuiasPublicas().isEmpty());
        assertEquals(4, bean.getTotalEmpresasActivas());
        assertEquals(9, bean.getTotalVehiculosRegistrados());
        assertEquals(5L, bean.getTotalViajesEnCurso());
        assertEquals(12L, bean.getTotalPesadas());

        List<Map.Entry<TipoEstado, Long>> estados = bean.getGuiasPorEstadoEntries();
        assertEquals(1, estados.size());
        assertEquals(TipoEstado.EN_CURSO, estados.get(0).getKey());
    }

    @Test
    @DisplayName("Agrupaciones con menos de 3 registros se omiten silenciosamente sin fila sintética")
    void testOmisionSilenciosaGruposMenoresATres() throws Exception {
        DashboardServiceLocal dashboard = mock(DashboardServiceLocal.class);
        VehiculoEJBLocal vehiculoEJB = mock(VehiculoEJBLocal.class);
        GuiaDeViajeEJBLocal guiaEJB = mock(GuiaDeViajeEJBLocal.class);

        Empresa emp = new Empresa(10, "Logistica Sur S.A.", "Logistica Sur", "Ruta 5");
        emp.setActiva(true);

        Vehiculo vHabilitado = new Vehiculo("STP1", "Scania", "R", 9000, 15000, true, emp);
        vHabilitado.setItv(List.of(new ITV(LocalDate.now().plusDays(30), vHabilitado)));
        vHabilitado.getPncs().add(new PNC(LocalDate.now().plusDays(30), "PNC-1", vHabilitado));

        Vehiculo vVencido = new Vehiculo("STP2", "Volvo", "FH", 9000, 15000, true, emp);
        vVencido.setItv(List.of(new ITV(LocalDate.now().minusDays(2), vVencido)));
        vVencido.getPncs().add(new PNC(LocalDate.now().plusDays(30), "PNC-2", vVencido));

        Vehiculo vSinPermisos = new Vehiculo("STP3", "Iveco", "S", 9000, 15000, true, emp);

        TipoCarga madera = new TipoCarga("Rollizos");
        TipoCarga granel = new TipoCarga("Soja");

        GuiaDeViaje g1 = new GuiaDeViaje();
        g1.setRubroCliente("Forestal");
        g1.setTiposCarga(List.of(madera));
        g1.setVolumenCarga(10.0f);
        g1.setEstado(TipoEstado.FINALIZADO);

        GuiaDeViaje g2 = new GuiaDeViaje();
        g2.setRubroCliente("Forestal");
        g2.setTiposCarga(List.of(madera));
        g2.setVolumenCarga(12.5f);
        g2.setEstado(TipoEstado.FINALIZADO);

        GuiaDeViaje g3 = new GuiaDeViaje();
        g3.setRubroCliente("Forestal");
        g3.setTiposCarga(List.of(madera));
        g3.setVolumenCarga(7.5f);
        g3.setEstado(TipoEstado.FINALIZADO);

        GuiaDeViaje gMinoritaria1 = new GuiaDeViaje();
        gMinoritaria1.setRubroCliente("Agricola");
        gMinoritaria1.setTiposCarga(List.of(granel));
        gMinoritaria1.setVolumenCarga(15.0f);
        gMinoritaria1.setEstado(TipoEstado.EN_CURSO);

        GuiaDeViaje gMinoritaria2 = new GuiaDeViaje();
        gMinoritaria2.setRubroCliente("Agricola");
        gMinoritaria2.setTiposCarga(List.of(granel));
        gMinoritaria2.setVolumenCarga(18.0f);
        gMinoritaria2.setEstado(TipoEstado.EN_CURSO);

        when(vehiculoEJB.listarEmpresasParaPortal("tok-portal")).thenReturn(List.of(emp));
        when(vehiculoEJB.listarParaPortal("tok-portal")).thenReturn(List.of(vHabilitado, vVencido, vSinPermisos));
        when(guiaEJB.listarParaPortal("tok-portal")).thenReturn(List.of(g1, g2, g3, gMinoritaria1, gMinoritaria2));
        when(dashboard.obtenerResumenPortal("tok-portal")).thenReturn(new DashboardResumenDTO(
                Map.of(TipoEstado.FINALIZADO, 3L, TipoEstado.EN_CURSO, 2L),
                Map.of(),
                Map.of(),
                3L,
                1L,
                3L,
                Map.of(),
                Map.of(),
                0.0
        ));

        PortalPublicoBean bean = crearBean(dashboard, vehiculoEJB, guiaEJB);
        try (MockedStatic<SesionActual> sesion = Mockito.mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::token).thenReturn("tok-portal");
            bean.cargarDatosPublicos();
        }

        List<PortalPublicoBean.EstadisticaCorredorPublico> actividad = bean.getEstadisticasActividadAgregada();
        assertEquals(1, actividad.size(), "El grupo Agricola (2 registros < 3) debe omitirse sin fila sintética");
        assertEquals("Forestal", actividad.get(0).getRubro());
        assertEquals(3L, actividad.get(0).getCantidadViajes());
        assertEquals(30.0, actividad.get(0).getVolumenTotal());

        assertEquals(3L, bean.contarVehiculosEmpresa(10));
        assertEquals(1L, bean.contarVehiculosHabilitadosEmpresa(10));

        List<PortalPublicoBean.EstadisticaFlotaPublica> flota = bean.getEstadisticasFlotaAgregada();
        assertEquals(1, flota.size());
        assertEquals(3L, flota.get(0).getCantidadUnidades());
        assertEquals(1L, flota.get(0).getUnidadesHabilitadas());
    }
}
