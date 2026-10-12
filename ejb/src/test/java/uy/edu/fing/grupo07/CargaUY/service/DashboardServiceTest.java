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
}
