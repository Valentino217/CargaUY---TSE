package uy.edu.fing.grupo07.CargaUY.web;

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
import uy.edu.fing.grupo07.CargaUY.service.DashboardServiceLocal;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - DashboardBean")
class DashboardBeanTest {

    @Mock
    private DashboardServiceLocal dashboardService;

    private DashboardBean bean;

    @BeforeEach
    void setUp() {
        bean = new DashboardBean(dashboardService);
    }

    @Test
    @DisplayName("inicializar carga métricas consolidadas del servicio")
    void testInicializarExitoso() {
        Map<TipoEstado, Long> guias = new EnumMap<>(TipoEstado.class);
        guias.put(TipoEstado.EN_CURSO, 4L);

        Map<EstadoInfraccion, Long> infraccionesPorEstado = new EnumMap<>(EstadoInfraccion.class);
        infraccionesPorEstado.put(EstadoInfraccion.DETECTADA, 2L);

        Map<TipoInfraccion, Long> infraccionesPorTipo = new EnumMap<>(TipoInfraccion.class);
        infraccionesPorTipo.put(TipoInfraccion.EXCESO_DE_CARGA, 2L);

        DashboardResumenDTO resumenDTO = new DashboardResumenDTO(
                guias,
                infraccionesPorEstado,
                infraccionesPorTipo,
                15L
        );
        when(dashboardService.obtenerResumen()).thenReturn(resumenDTO);

        bean.inicializar();

        verify(dashboardService).obtenerResumen();
        assertNotNull(bean.getResumen());
        assertEquals(15L, bean.getTotalPesadas());
        assertEquals(1, bean.getListaGuiasPorEstado().size());
        assertEquals(1, bean.getListaInfraccionesPorEstado().size());
        assertEquals(1, bean.getListaInfraccionesPorTipo().size());
    }

    @Test
    @DisplayName("getters retornan colecciones vacías y ceros cuando el resumen es nulo")
    void testResumenNulo() {
        bean.setResumen(null);

        assertEquals(0L, bean.getTotalPesadas());
        assertEquals(0, bean.getListaGuiasPorEstado().size());
        assertEquals(0, bean.getListaInfraccionesPorEstado().size());
        assertEquals(0, bean.getListaInfraccionesPorTipo().size());
    }
}
