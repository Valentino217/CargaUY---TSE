package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.RegistroAuditoriaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - DetalleInfraccionBean")
class DetalleInfraccionBeanTest {

    @Mock
    private GestionInfraccionesServiceLocal infraccionesService;

    private DetalleInfraccionBean bean;

    @BeforeEach
    void setUp() {
        bean = new DetalleInfraccionBean(infraccionesService);
    }

    @Test
    @DisplayName("inicializar sin idCaso no realiza llamadas al servicio")
    void testInicializarSinIdCaso() {
        bean.setIdCaso(null);

        bean.inicializar();

        verifyNoInteractions(infraccionesService);
        assertNull(bean.getInfraccion());
    }

    @Test
    @DisplayName("inicializar con idCaso existente carga la infracción correctamente")
    void testInicializarConIdCasoExitoso() {
        bean.setIdCaso(7);
        InfraccionDTO dto = new InfraccionDTO(
                7,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.DETECTADA,
                "Evidencia peso",
                null,
                null,
                12L,
                "456789",
                "Logistica SA",
                null,
                null,
                null
        );
        when(infraccionesService.obtenerInfraccion(7)).thenReturn(dto);

        bean.inicializar();

        verify(infraccionesService).obtenerInfraccion(7);
        assertNotNull(bean.getInfraccion());
        assertEquals(7, bean.getInfraccion().idCaso());
    }

    @Test
    @DisplayName("cargarInfraccion maneja BusinessException sin propagar")
    void testCargarInfraccionErrorNegocio() {
        bean.setIdCaso(99);
        when(infraccionesService.obtenerInfraccion(99)).thenThrow(new BusinessException("Infracción no existe"));

        assertDoesNotThrow(() -> bean.cargarInfraccion());
        assertNull(bean.getInfraccion());
    }

    @Test
    @DisplayName("notificar invoca el servicio y actualiza la entidad")
    void testNotificarExitoso() {
        bean.setIdCaso(7);
        InfraccionDTO dtoNotificada = new InfraccionDTO(
                7,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.NOTIFICADA,
                "Evidencia peso",
                null,
                null,
                12L,
                "456789",
                "Logistica SA",
                null,
                null,
                null
        );
        when(infraccionesService.notificarInfraccion(7)).thenReturn(dtoNotificada);

        bean.notificar();

        verify(infraccionesService).notificarInfraccion(7);
        assertEquals(EstadoInfraccion.NOTIFICADA, bean.getInfraccion().estado());
    }

    @Test
    @DisplayName("presentarDescargo valida texto vacío sin invocar servicio")
    void testPresentarDescargoTextoVacio() {
        bean.setIdCaso(7);
        bean.setTextoDescargo("   ");

        bean.presentarDescargo();

        verify(infraccionesService, never()).presentarDescargo(anyInt(), any());
    }

    @Test
    @DisplayName("presentarDescargo exitoso llama al servicio y limpia el formulario")
    void testPresentarDescargoExitoso() {
        bean.setIdCaso(7);
        bean.setTextoDescargo("Descargo formal con comprobante de calibración.");

        InfraccionDTO dtoEnDescargo = new InfraccionDTO(
                7,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.EN_DESCARGO,
                "Evidencia peso",
                null,
                null,
                12L,
                "456789",
                "Logistica SA",
                "Descargo formal con comprobante de calibración.",
                LocalDate.now(),
                null
        );
        when(infraccionesService.presentarDescargo(eq(7), any(DescargoRequestDTO.class))).thenReturn(dtoEnDescargo);

        bean.presentarDescargo();

        verify(infraccionesService).presentarDescargo(eq(7), any(DescargoRequestDTO.class));
        assertEquals(EstadoInfraccion.EN_DESCARGO, bean.getInfraccion().estado());
        assertEquals("", bean.getTextoDescargo());
    }

    @Test
    @DisplayName("confirmar valida fundamento obligatorio")
    void testConfirmarSinFundamento() {
        bean.setIdCaso(7);
        bean.setFuncionarioId(1);
        bean.setFundamento("");

        bean.confirmar();

        verify(infraccionesService, never()).confirmarInfraccion(anyInt(), any());
    }

    @Test
    @DisplayName("confirmar exitoso llama al servicio y limpia fundamento")
    void testConfirmarExitoso() {
        bean.setIdCaso(7);
        bean.setFuncionarioId(1);
        bean.setFundamento("Descargo rechazado, exceso comprobado.");

        RegistroAuditoriaDTO audit = new RegistroAuditoriaDTO(
                100L,
                "CONFIRMAR_INFRACCION",
                LocalDate.now(),
                LocalDateTime.now(),
                "Inspector Juan",
                "CONFIRMAR_INFRACCION",
                "Descargo rechazado, exceso comprobado."
        );
        InfraccionDTO dtoConfirmada = new InfraccionDTO(
                7,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.CONFIRMADA,
                "Evidencia peso",
                "Descargo rechazado, exceso comprobado.",
                LocalDate.now(),
                12L,
                "456789",
                "Logistica SA",
                "Descargo previo",
                LocalDate.now(),
                audit
        );
        when(infraccionesService.confirmarInfraccion(eq(7), any(ResolucionInfraccionDTO.class))).thenReturn(dtoConfirmada);

        bean.confirmar();

        verify(infraccionesService).confirmarInfraccion(eq(7), any(ResolucionInfraccionDTO.class));
        assertEquals(EstadoInfraccion.CONFIRMADA, bean.getInfraccion().estado());
        assertEquals("", bean.getFundamento());
    }

    @Test
    @DisplayName("desestimar exitoso llama al servicio y limpia fundamento")
    void testDesestimarExitoso() {
        bean.setIdCaso(7);
        bean.setFuncionarioId(1);
        bean.setFundamento("Error de sensor en balanza aceptado.");

        RegistroAuditoriaDTO audit = new RegistroAuditoriaDTO(
                101L,
                "DESESTIMAR_INFRACCION",
                LocalDate.now(),
                LocalDateTime.now(),
                "Inspector Juan",
                "DESESTIMAR_INFRACCION",
                "Error de sensor en balanza aceptado."
        );
        InfraccionDTO dtoDesestimada = new InfraccionDTO(
                7,
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.DESESTIMADA,
                "Evidencia peso",
                "Error de sensor en balanza aceptado.",
                LocalDate.now(),
                12L,
                "456789",
                "Logistica SA",
                "Descargo previo",
                LocalDate.now(),
                audit
        );
        when(infraccionesService.desestimarInfraccion(eq(7), any(ResolucionInfraccionDTO.class))).thenReturn(dtoDesestimada);

        bean.desestimar();

        verify(infraccionesService).desestimarInfraccion(eq(7), any(ResolucionInfraccionDTO.class));
        assertEquals(EstadoInfraccion.DESESTIMADA, bean.getInfraccion().estado());
        assertEquals("", bean.getFundamento());
    }

    @Test
    @DisplayName("metodos booleanos de ayuda representan adecuadamente los estados del ciclo de vida")
    void testBooleanHelpers() {
        assertFalse(bean.isDetectada());
        assertFalse(bean.isPuedePresentarDescargo());
        assertFalse(bean.isPuedeConfirmar());
        assertFalse(bean.isPuedeDesestimar());
        assertFalse(bean.isResuelta());

        // DETECTADA
        InfraccionDTO detectada = new InfraccionDTO(1, LocalDate.now(), TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.DETECTADA, null, null, null, 1L, null, null, null, null, null);
        bean.setInfraccion(detectada);
        assertTrue(bean.isDetectada());
        assertFalse(bean.isPuedePresentarDescargo());
        assertFalse(bean.isPuedeConfirmar());
        assertTrue(bean.isPuedeDesestimar());
        assertFalse(bean.isResuelta());

        // NOTIFICADA
        InfraccionDTO notificada = new InfraccionDTO(1, LocalDate.now(), TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.NOTIFICADA, null, null, null, 1L, null, null, null, null, null);
        bean.setInfraccion(notificada);
        assertFalse(bean.isDetectada());
        assertTrue(bean.isPuedePresentarDescargo());
        assertTrue(bean.isPuedeConfirmar());
        assertTrue(bean.isPuedeDesestimar());
        assertFalse(bean.isResuelta());

        // CONFIRMADA
        InfraccionDTO confirmada = new InfraccionDTO(1, LocalDate.now(), TipoInfraccion.EXCESO_DE_CARGA, EstadoInfraccion.CONFIRMADA, null, null, null, 1L, null, null, null, null, null);
        bean.setInfraccion(confirmada);
        assertFalse(bean.isDetectada());
        assertFalse(bean.isPuedePresentarDescargo());
        assertFalse(bean.isPuedeConfirmar());
        assertFalse(bean.isPuedeDesestimar());
        assertTrue(bean.isResuelta());
    }
}
