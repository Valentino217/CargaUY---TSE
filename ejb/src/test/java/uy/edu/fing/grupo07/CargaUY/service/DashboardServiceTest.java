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
}
