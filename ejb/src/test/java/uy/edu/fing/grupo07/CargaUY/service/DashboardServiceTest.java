package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - DashboardService")
class DashboardServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Object[]> queryFilasGuias;

    @Mock
    private TypedQuery<Object[]> queryFilasInfraccionesEstado;

    @Mock
    private TypedQuery<Object[]> queryFilasInfraccionesTipo;

    @Mock
    private TypedQuery<Long> queryTotalPesadas;

    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(em);
    }

    @Test
    @DisplayName("Debe inicializar todos los estados/tipos con cero y cargar las métricas consolidadas")
    void testObtenerResumenConsolidaMetricasCorrectamente() {
        List<Object[]> filasGuias = new ArrayList<>();
        filasGuias.add(new Object[]{TipoEstado.EN_CURSO, 5L});
        filasGuias.add(new Object[]{TipoEstado.FINALIZADO, 2L});

        List<Object[]> filasInfEstado = new ArrayList<>();
        filasInfEstado.add(new Object[]{EstadoInfraccion.DETECTADA, 3L});
        filasInfEstado.add(new Object[]{EstadoInfraccion.CONFIRMADA, 1L});

        List<Object[]> filasInfTipo = new ArrayList<>();
        filasInfTipo.add(new Object[]{TipoInfraccion.EXCESO_DE_CARGA, 4L});

        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Object[].class))).thenReturn(queryFilasGuias);
        when(queryFilasGuias.getResultList()).thenReturn(filasGuias);

        when(em.createQuery(contains("i.estado, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesEstado);
        when(queryFilasInfraccionesEstado.getResultList()).thenReturn(filasInfEstado);

        when(em.createQuery(contains("i.tipo, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesTipo);
        when(queryFilasInfraccionesTipo.getResultList()).thenReturn(filasInfTipo);

        when(em.createQuery(contains("FROM PesadaBalanza"), eq(Long.class))).thenReturn(queryTotalPesadas);
        when(queryTotalPesadas.getSingleResult()).thenReturn(15L);

        DashboardResumenDTO resumen = service.obtenerResumen();

        assertNotNull(resumen);
        assertEquals(15L, resumen.totalPesadas());

        assertEquals(TipoEstado.values().length, resumen.guiasPorEstado().size());
        assertEquals(5L, resumen.guiasPorEstado().get(TipoEstado.EN_CURSO));
        assertEquals(2L, resumen.guiasPorEstado().get(TipoEstado.FINALIZADO));
        assertEquals(0L, resumen.guiasPorEstado().get(TipoEstado.SIN_INICIAR));

        assertEquals(EstadoInfraccion.values().length, resumen.infraccionesPorEstado().size());
        assertEquals(3L, resumen.infraccionesPorEstado().get(EstadoInfraccion.DETECTADA));
        assertEquals(1L, resumen.infraccionesPorEstado().get(EstadoInfraccion.CONFIRMADA));
        assertEquals(0L, resumen.infraccionesPorEstado().get(EstadoInfraccion.NOTIFICADA));
        assertEquals(0L, resumen.infraccionesPorEstado().get(EstadoInfraccion.EN_DESCARGO));
        assertEquals(0L, resumen.infraccionesPorEstado().get(EstadoInfraccion.DESESTIMADA));

        assertEquals(TipoInfraccion.values().length, resumen.infraccionesPorTipo().size());
        assertEquals(4L, resumen.infraccionesPorTipo().get(TipoInfraccion.EXCESO_DE_CARGA));
        assertEquals(0L, resumen.infraccionesPorTipo().get(TipoInfraccion.SIN_HABILITACION));
    }

    @Test
    @DisplayName("Debe filtrar métricas del dashboard por rubro, ubicación y rango de fechas")
    void testObtenerResumenFiltradoYDatosPrueba() {
        uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje g1 = new uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje();
        g1.setEstado(TipoEstado.EN_CURSO);
        g1.setFecha(java.time.LocalDate.of(2026, 5, 10));
        g1.setRubroCliente("Madera");
        g1.setOrigen("Montevideo");
        g1.setDestino("Rivera");
        g1.setVolumenCarga(35.0f);

        uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje g2 = new uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje();
        g2.setEstado(TipoEstado.FINALIZADO);
        g2.setFecha(java.time.LocalDate.of(2026, 3, 1));
        g2.setRubroCliente("Granos");
        g2.setOrigen("Salto");
        g2.setDestino("Colonia");
        g2.setVolumenCarga(20.0f);

        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje> qGuias = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT g FROM GuiaDeViaje g"), eq(uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(g1, g2));

        uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion inf1 = new uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion(
                java.time.LocalDate.of(2026, 5, 10),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.CONFIRMADA,
                g1,
                "Exceso"
        );
        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion> qInf = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT i FROM Infraccion i"), eq(uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion.class))).thenReturn(qInf);
        when(qInf.getResultList()).thenReturn(List.of(inf1));

        uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza p1 = new uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza(
                java.time.LocalDate.of(2026, 5, 10),
                java.time.LocalTime.NOON,
                42000,
                g1
        );
        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza> qPes = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT p FROM PesadaBalanza p"), eq(uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza.class))).thenReturn(qPes);
        when(qPes.getResultList()).thenReturn(List.of(p1));

        DashboardResumenDTO filtrado = service.obtenerResumenFiltrado(
                "Madera",
                "Montevideo",
                java.time.LocalDate.of(2026, 5, 1),
                java.time.LocalDate.of(2026, 5, 31)
        );
        assertEquals(1L, filtrado.guiasPorEstado().get(TipoEstado.EN_CURSO));
        assertEquals(1L, filtrado.infraccionesPorEstado().get(EstadoInfraccion.CONFIRMADA));
        assertEquals(1L, filtrado.totalPesadas());
        assertEquals(100.0, filtrado.tasaConfirmadasSobreDetectadas());

        // Probar DatosPruebaStartupBean con propiedad p3.datos.prueba=true
        String prev = System.getProperty("p3.datos.prueba");
        try {
            System.setProperty("p3.datos.prueba", "true");
            @SuppressWarnings("unchecked")
            TypedQuery<Long> qUsuarios = org.mockito.Mockito.mock(TypedQuery.class);
            when(em.createQuery(contains("select count(u) from Usuario u"), eq(Long.class))).thenReturn(qUsuarios);
            when(qUsuarios.getSingleResult()).thenReturn(0L);
            DatosPruebaStartupBean startup = new DatosPruebaStartupBean(em);
            startup.inicializar();
        } finally {
            if (prev == null) {
                System.clearProperty("p3.datos.prueba");
            } else {
                System.setProperty("p3.datos.prueba", prev);
            }
        }
    }

    @Test
    @DisplayName("Resumen público expone solo agregados y obtenerResumenPortal/obtenerResumen exigen control")
    void testResumenPublicoYPortalTokenizado() throws Exception {
        List<Object[]> filasGuias = new ArrayList<>();
        filasGuias.add(new Object[]{TipoEstado.EN_CURSO, 4L});
        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Object[].class))).thenReturn(queryFilasGuias);
        when(queryFilasGuias.getResultList()).thenReturn(filasGuias);
        when(em.createQuery(contains("FROM PesadaBalanza"), eq(Long.class))).thenReturn(queryTotalPesadas);
        when(queryTotalPesadas.getSingleResult()).thenReturn(9L);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qEmp = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("FROM Empresa e WHERE e.activa = true"), eq(Long.class))).thenReturn(qEmp);
        when(qEmp.getSingleResult()).thenReturn(3L);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qVeh = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("FROM Vehiculo v WHERE v.estadoHabilitacion = true"), eq(Long.class)))
                .thenReturn(qVeh);
        when(qVeh.getSingleResult()).thenReturn(7L);

        DashboardResumenDTO publico = service.obtenerResumenPublico();
        assertEquals(4L, publico.guiasPorEstado().get(TipoEstado.EN_CURSO));
        assertEquals(9L, publico.totalPesadas());
        assertEquals(3L, publico.totalEmpresasHabilitadas());
        assertEquals(7L, publico.totalVehiculosHabilitados());
        assertEquals(0, publico.infraccionesPorEstado().size());
        assertEquals(0, publico.volumenPorRubro().size());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.obtenerResumenPortal("tok")
        );

        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        java.lang.reflect.Field fCtrl = DashboardServiceImpl.class.getDeclaredField("control");
        fCtrl.setAccessible(true);
        fCtrl.set(service, control);

        when(em.createQuery(contains("i.estado, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesEstado);
        when(queryFilasInfraccionesEstado.getResultList()).thenReturn(List.of());
        when(em.createQuery(contains("i.tipo, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesTipo);
        when(queryFilasInfraccionesTipo.getResultList()).thenReturn(List.of());

        uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa empPortal =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa();
        empPortal.setNroEmpresa(101);
        empPortal.setActiva(true);
        uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo vehPortal =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo();
        vehPortal.setMatricula("STP 1234");
        vehPortal.setEstadoHabilitacion(true);
        uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje guiaPortal =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje();
        guiaPortal.setFecha(java.time.LocalDate.of(2026, 11, 13));
        guiaPortal.setRubroCliente("Forestal");
        guiaPortal.setVolumenCarga(30000.0f);
        guiaPortal.setEmpresa(empPortal);
        guiaPortal.setVehiculo(vehPortal);
        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje> qGuiasPortal =
                org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT g FROM GuiaDeViaje g ORDER BY g.fecha ASC"),
                eq(uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje.class))).thenReturn(qGuiasPortal);
        when(qGuiasPortal.getResultList()).thenReturn(List.of(guiaPortal));

        DashboardResumenDTO portal = service.obtenerResumenPortal("tok-portal");
        assertEquals(30000.0, portal.volumenPorRubro().get("Forestal"));
        assertEquals(1L, portal.viajesPorPeriodo().get("2026-11"));
        org.mockito.Mockito.verify(control).exigir("tok-portal");

        service.obtenerResumen("tok-admin");
        org.mockito.Mockito.verify(control).exigir(
                "tok-admin",
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO
        );
    }

    @Test
    @DisplayName("Filtro por ubicación considera corredorVial e incluye infracciones y pesadas sin guía por vehículo")
    void testFiltroCorredorVialYRegistrosSinGuia() {
        java.time.LocalDate fecha = java.time.LocalDate.of(2026, 6, 15);
        uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje guiaCorredor =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje();
        guiaCorredor.setEstado(TipoEstado.EN_CURSO);
        guiaCorredor.setFecha(fecha);
        guiaCorredor.setRubroCliente("Forestal");
        guiaCorredor.setOrigen("Tacuarembó");
        guiaCorredor.setDestino("Fray Bentos");
        guiaCorredor.setCorredorVial("Corredor Ruta 5");
        guiaCorredor.setVolumenCarga(28.0f);

        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje> qGuias =
                org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT g FROM GuiaDeViaje g"),
                eq(uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(guiaCorredor));

        uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa empCoincide =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa(100, "Forestal Sur S.A.", "ForestalSur", "Ruta 5 Km 210");
        empCoincide.setActiva(true);
        empCoincide.setRubros(List.of(new uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro("Forestal", "Madera y celulosa")));
        uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo vehCoincide =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo("STP5001", "Scania", "G", 9000, 27000, true, empCoincide);

        uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa empOtra =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa(101, "Granos Norte S.A.", "GranosNorte", "Ruta 3 Km 90");
        empOtra.setActiva(true);
        empOtra.setRubros(List.of(new uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro("Granos", "Cereales")));
        uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo vehOtra =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo("STP3002", "Volvo", "FH", 9000, 27000, true, empOtra);

        uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion infSinGuiaOk =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion(
                        fecha, TipoInfraccion.VIAJE_SIN_GUIA, EstadoInfraccion.DETECTADA, vehCoincide, "Sin guía en Ruta 5");
        uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion infSinGuiaFuera =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion(
                        fecha, TipoInfraccion.VIAJE_SIN_GUIA, EstadoInfraccion.DETECTADA, vehOtra, "Sin guía en Ruta 3");

        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion> qInf =
                org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT i FROM Infraccion i"),
                eq(uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion.class))).thenReturn(qInf);
        when(qInf.getResultList()).thenReturn(List.of(infSinGuiaOk, infSinGuiaFuera));

        uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza pesadaSinGuiaOk =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza(
                        fecha, java.time.LocalTime.NOON, 39500, null, vehCoincide);
        uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza pesadaSinGuiaFuera =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza(
                        fecha, java.time.LocalTime.NOON, 39500, null, vehOtra);

        @SuppressWarnings("unchecked")
        TypedQuery<uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza> qPes =
                org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT p FROM PesadaBalanza p"),
                eq(uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza.class))).thenReturn(qPes);
        when(qPes.getResultList()).thenReturn(List.of(pesadaSinGuiaOk, pesadaSinGuiaFuera));

        DashboardResumenDTO res = service.obtenerResumenFiltrado(
                "Forestal",
                null,
                "Ruta 5",
                java.time.LocalDate.of(2026, 6, 1),
                java.time.LocalDate.of(2026, 6, 30)
        );

        assertEquals(1L, res.guiasPorEstado().get(TipoEstado.EN_CURSO));
        assertEquals(1L, res.infraccionesPorTipo().get(TipoInfraccion.VIAJE_SIN_GUIA));
        assertEquals(1L, res.infraccionesPorEstado().get(EstadoInfraccion.DETECTADA));
        assertEquals(1L, res.totalPesadas());
        assertEquals(1L, res.totalEmpresasHabilitadas());
        assertEquals(1L, res.totalVehiculosHabilitados());
    }

    @Test
    @DisplayName("Catálogos disponibles y overloads tokenizados de DashboardService validan control de acceso")
    void testCatalogosDisponiblesYOverloadsTokenizados() throws Exception {
        @SuppressWarnings("unchecked")
        TypedQuery<String> qStr = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.anyString(), eq(String.class))).thenReturn(qStr);
        when(qStr.getResultList()).thenReturn(List.of("Madera", "Montevideo", "Rivera"));

        List<String> rubros = service.listarRubrosDisponibles();
        org.junit.jupiter.api.Assertions.assertTrue(rubros.contains("Madera"));
        List<String> tipos = service.listarTiposCargaDisponibles();
        org.junit.jupiter.api.Assertions.assertFalse(tipos.isEmpty());
        List<String> ubicaciones = service.listarUbicacionesDisponibles();
        org.junit.jupiter.api.Assertions.assertTrue(ubicaciones.contains("Montevideo"));

        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        java.lang.reflect.Field fCtrl = DashboardServiceImpl.class.getDeclaredField("control");
        fCtrl.setAccessible(true);
        fCtrl.set(service, control);

        service.listarRubrosDisponibles("tok-aut");
        service.listarTiposCargaDisponibles("tok-aut");
        service.listarUbicacionesDisponibles("tok-aut");

        when(em.createQuery(contains("FROM GuiaDeViaje"), eq(Object[].class))).thenReturn(queryFilasGuias);
        when(queryFilasGuias.getResultList()).thenReturn(List.of());
        when(em.createQuery(contains("i.estado, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesEstado);
        when(queryFilasInfraccionesEstado.getResultList()).thenReturn(List.of());
        when(em.createQuery(contains("i.tipo, COUNT(i) FROM Infraccion"), eq(Object[].class)))
                .thenReturn(queryFilasInfraccionesTipo);
        when(queryFilasInfraccionesTipo.getResultList()).thenReturn(List.of());
        when(em.createQuery(contains("FROM PesadaBalanza"), eq(Long.class))).thenReturn(queryTotalPesadas);
        when(queryTotalPesadas.getSingleResult()).thenReturn(0L);

        service.obtenerResumenFiltrado("tok-aut", null, null, null, null, null);
        org.mockito.Mockito.verify(control, org.mockito.Mockito.times(4)).exigir(
                "tok-aut",
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD,
                uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO
        );
    }
}
