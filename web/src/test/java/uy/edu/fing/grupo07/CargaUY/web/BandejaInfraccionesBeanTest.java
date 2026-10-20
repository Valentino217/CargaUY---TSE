package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - BandejaInfraccionesBean")
class BandejaInfraccionesBeanTest {

    @Mock
    private GestionInfraccionesServiceLocal infraccionesService;

    @Mock
    private MotorFiscalizacionLocal motorFiscalizacion;

    @Mock
    private FiscalizacionProducerLocal fiscalizacionProducer;

    private BandejaInfraccionesBean bean;

    @BeforeEach
    void setUp() {
        bean = new BandejaInfraccionesBean(
                infraccionesService,
                motorFiscalizacion,
                fiscalizacionProducer
        );
    }

    @Test
    @DisplayName("inicializar debe cargar las infracciones existentes sin filtros")
    void testInicializarCargaInfracciones() {
        InfraccionDTO dto = new InfraccionDTO(
                1,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.DETECTADA,
                "Evidencia 1",
                null,
                null,
                5L,
                "123456",
                "Empresa SRL",
                null,
                null,
                null
        );
        when(infraccionesService.listarInfracciones(null, null)).thenReturn(List.of(dto));

        bean.inicializar();

        verify(infraccionesService).listarInfracciones(null, null);
        assertEquals(1, bean.getInfracciones().size());
        assertEquals(1, bean.getInfracciones().get(0).idCaso());
    }

    @Test
    @DisplayName("filtrar debe invocar el servicio con los filtros seleccionados")
    void testFiltrarConValores() {
        bean.setFiltroEstado(EstadoInfraccion.DETECTADA);
        bean.setFiltroTipo(TipoInfraccion.EXCESO_DE_CARGA);
        when(infraccionesService.listarInfracciones(EstadoInfraccion.DETECTADA, TipoInfraccion.EXCESO_DE_CARGA))
                .thenReturn(Collections.emptyList());

        bean.filtrar();

        verify(infraccionesService).listarInfracciones(EstadoInfraccion.DETECTADA, TipoInfraccion.EXCESO_DE_CARGA);
        assertTrue(bean.getInfracciones().isEmpty());
    }

    @Test
    @DisplayName("limpiarFiltros debe resetear estado y tipo y recargar la lista")
    void testLimpiarFiltros() {
        bean.setFiltroEstado(EstadoInfraccion.NOTIFICADA);
        bean.setFiltroTipo(TipoInfraccion.SIN_HABILITACION);

        bean.limpiarFiltros();

        assertNull(bean.getFiltroEstado());
        assertNull(bean.getFiltroTipo());
        verify(infraccionesService).listarInfracciones(null, null);
    }

    @Test
    @DisplayName("ejecutarFiscalizacion sin guiaId no debe llamar a los servicios")
    void testEjecutarFiscalizacionSinGuiaId() {
        bean.setGuiaIdFiscalizar(null);

        bean.ejecutarFiscalizacion();

        verifyNoInteractions(motorFiscalizacion);
        verifyNoInteractions(fiscalizacionProducer);
    }

    @Test
    @DisplayName("ejecutarFiscalizacion en modo asincrono debe delegar en fiscalizacionProducer")
    void testEjecutarFiscalizacionModoAsync() {
        bean.setGuiaIdFiscalizar(10L);
        bean.setModoFiscalizacion("async");

        bean.ejecutarFiscalizacion();

        verify(fiscalizacionProducer).solicitarEvaluacion(10L);
        verifyNoInteractions(motorFiscalizacion);
    }

    @Test
    @DisplayName("ejecutarFiscalizacion en modo sincrono sin detecciones debe llamar al motor y recargar")
    void testEjecutarFiscalizacionModoSyncSinDetecciones() {
        bean.setGuiaIdFiscalizar(10L);
        bean.setModoFiscalizacion("sync");
        when(motorFiscalizacion.evaluarGuia(10L)).thenReturn(Collections.emptyList());

        bean.ejecutarFiscalizacion();

        verify(motorFiscalizacion).evaluarGuia(10L);
        verify(infraccionesService).listarInfracciones(null, null);
    }

    @Test
    @DisplayName("ejecutarFiscalizacion en modo sincrono con detecciones debe llamar al motor y recargar")
    void testEjecutarFiscalizacionModoSyncConDetecciones() {
        bean.setGuiaIdFiscalizar(10L);
        bean.setModoFiscalizacion("sync");
        when(motorFiscalizacion.evaluarGuia(10L)).thenReturn(List.of(TipoInfraccion.EXCESO_DE_CARGA));

        bean.ejecutarFiscalizacion();

        verify(motorFiscalizacion).evaluarGuia(10L);
        verify(infraccionesService).listarInfracciones(null, null);
    }

    @Test
    @DisplayName("ejecutarFiscalizacion maneja adecuadamente excepciones de negocio")
    void testEjecutarFiscalizacionBusinessException() {
        bean.setGuiaIdFiscalizar(999L);
        bean.setModoFiscalizacion("sync");
        when(motorFiscalizacion.evaluarGuia(999L)).thenThrow(new BusinessException("Guía no existe"));

        assertDoesNotThrow(() -> bean.ejecutarFiscalizacion());
        verify(motorFiscalizacion).evaluarGuia(999L);
    }

    @Test
    @DisplayName("getters de opciones enum deben retornar todos los valores")
    void testOpcionesEnum() {
        assertEquals(EstadoInfraccion.values().length, bean.getEstadosDisponibles().length);
        assertEquals(TipoInfraccion.values().length, bean.getTiposDisponibles().length);
    }
}
