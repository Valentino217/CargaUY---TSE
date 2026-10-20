package uy.edu.fing.grupo07.CargaUY.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - RegistrarPesajeBean")
class RegistrarPesajeBeanTest {

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    @Mock
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    @Mock
    private MotorFiscalizacionLocal motorFiscalizacion;

    private RegistrarPesajeBean bean;

    @BeforeEach
    void setUp() {
        bean = new RegistrarPesajeBean(balanzaService, guiaDeViajeEJB, motorFiscalizacion);
    }

    @Test
    @DisplayName("init debe cargar guías disponibles y seleccionar por defecto una guía en curso")
    void testInitCargaGuias() {
        GuiaDeViaje guia1 = new GuiaDeViaje();
        guia1.setId(10L);
        guia1.setEstado(TipoEstado.EN_CURSO);

        List<GuiaDeViaje> guias = new ArrayList<>();
        guias.add(guia1);

        when(guiaDeViajeEJB.listarGuias()).thenReturn(guias);
        when(balanzaService.listarPesadasPorGuia(10L)).thenReturn(Collections.emptyList());

        bean.init();

        assertEquals(1, bean.getGuiasDisponibles().size());
        assertEquals(10L, bean.getGuiaIdSeleccionada());
        assertNotNull(bean.getFecha());
        assertNotNull(bean.getHora());
        assertTrue(bean.isEvaluarInmediato());
    }

    @Test
    @DisplayName("registrarPesaje exitoso con evaluación inmediata invoca balanza y motor de fiscalización")
    void testRegistrarPesajeExitosoConEvaluacion() {
        bean.setGuiaIdSeleccionada(5L);
        bean.setPesoRegistrado(43500);
        bean.setFecha(LocalDate.now());
        bean.setHora(LocalTime.of(14, 30));
        bean.setEvaluarInmediato(true);

        PesadaBalanzaDTO pesadaRetornada = new PesadaBalanzaDTO(
                1,
                LocalDate.now(),
                LocalTime.of(14, 30),
                43500,
                "STP 1234",
                5L
        );

        when(balanzaService.registrarPesada(any(PesadaBalanzaDTO.class))).thenReturn(pesadaRetornada);
        when(motorFiscalizacion.evaluarGuia(5L)).thenReturn(Collections.singletonList(TipoInfraccion.EXCESO_DE_CARGA));
        when(balanzaService.listarPesadasPorGuia(5L)).thenReturn(Collections.singletonList(pesadaRetornada));

        bean.registrarPesaje();

        verify(balanzaService, times(1)).registrarPesada(any(PesadaBalanzaDTO.class));
        verify(motorFiscalizacion, times(1)).evaluarGuia(5L);
        assertEquals(1, bean.getUltimasInfraccionesDetectadas().size());
        assertEquals(TipoInfraccion.EXCESO_DE_CARGA, bean.getUltimasInfraccionesDetectadas().get(0));
    }

    @Test
    @DisplayName("registrarPesaje con peso nulo o negativo no llama a los servicios")
    void testRegistrarPesajeInvalido() {
        bean.setGuiaIdSeleccionada(5L);
        bean.setPesoRegistrado(-100);

        bean.registrarPesaje();

        verify(balanzaService, never()).registrarPesada(any());
        verify(motorFiscalizacion, never()).evaluarGuia(any());
    }

    @Test
    @DisplayName("registrarPesaje sin evaluación inmediata registra en balanza pero no llama directamente al motor")
    void testRegistrarPesajeSinEvaluacionInmediata() {
        bean.setGuiaIdSeleccionada(8L);
        bean.setPesoRegistrado(35000);
        bean.setEvaluarInmediato(false);

        PesadaBalanzaDTO pesadaRetornada = new PesadaBalanzaDTO(
                2,
                LocalDate.now(),
                LocalTime.now(),
                35000,
                "ABC 9876",
                8L
        );

        when(balanzaService.registrarPesada(any(PesadaBalanzaDTO.class))).thenReturn(pesadaRetornada);
        when(balanzaService.listarPesadasPorGuia(8L)).thenReturn(Collections.singletonList(pesadaRetornada));

        bean.registrarPesaje();

        verify(balanzaService, times(1)).registrarPesada(any(PesadaBalanzaDTO.class));
        verify(motorFiscalizacion, never()).evaluarGuia(any());
    }
}
