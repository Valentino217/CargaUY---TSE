package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - EmpresaInfraccionesBean (Punto D.5 - Aislamiento por Empresa)")
class EmpresaInfraccionesBeanTest {

    @Mock
    private GestionInfraccionesServiceLocal infraccionesService;

    private EmpresaInfraccionesBean bean;

    @BeforeEach
    void setUp() {
        bean = new EmpresaInfraccionesBean(infraccionesService);
    }

    @Test
    @DisplayName("Debe consultar exclusivamente listarInfraccionesParaEmpresa(token) y presentar descargo con token de sesión")
    void testAislamientoPorEmpresaYEnvioDescargoAutenticado() {
        InfraccionDTO notificada = new InfraccionDTO(
                25,
                LocalDate.now(),
                TipoInfraccion.SIN_HABILITACION,
                EstadoInfraccion.NOTIFICADA,
                "ITV vencida a la fecha de la guía",
                null,
                null,
                88L,
                "456789",
                "LogiSur",
                null,
                null,
                null
        );
        InfraccionDTO enDescargo = new InfraccionDTO(
                25,
                LocalDate.now(),
                TipoInfraccion.SIN_HABILITACION,
                EstadoInfraccion.EN_DESCARGO,
                "ITV vencida a la fecha de la guía",
                null,
                null,
                88L,
                "456789",
                "LogiSur",
                "Certificado SUCTA renovado adjunto",
                LocalDate.now(),
                null
        );

        try (MockedStatic<SesionActual> sesion = mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::token).thenReturn("tok-empresa-logisur");

            // Si la empresa no tiene infracciones, queda vacía (sin fuga multi-empresa)
            when(infraccionesService.listarInfraccionesParaEmpresa("tok-empresa-logisur"))
                    .thenReturn(List.of());
            bean.init();
            assertTrue(bean.getInfracciones().isEmpty());
            verify(infraccionesService, never()).listarInfracciones(any(), any());

            // Cuando tiene infracción NOTIFICADA propia, permite seleccionarla y enviar descargo autenticado
            when(infraccionesService.listarInfraccionesParaEmpresa("tok-empresa-logisur"))
                    .thenReturn(List.of(notificada));
            bean.cargarInfracciones();
            assertEquals(1, bean.getTotalActivas());
            assertTrue(bean.puedePresentarDescargo(notificada));
            assertFalse(bean.puedePresentarDescargo(null));

            bean.seleccionarParaDescargo(notificada);
            bean.setTextoDescargo("   ");
            bean.enviarDescargo();
            verify(infraccionesService, never()).presentarDescargo(anyString(), anyInt(), any());

            when(infraccionesService.presentarDescargo(
                    eq("tok-empresa-logisur"),
                    eq(25),
                    eq(new DescargoRequestDTO("Certificado SUCTA renovado adjunto"))
            )).thenReturn(enDescargo);
            when(infraccionesService.listarInfraccionesParaEmpresa("tok-empresa-logisur"))
                    .thenReturn(List.of(enDescargo));

            bean.setTextoDescargo("  Certificado SUCTA renovado adjunto  ");
            bean.enviarDescargo();

            assertEquals(EstadoInfraccion.EN_DESCARGO, bean.getSeleccionada().estado());
            assertEquals(1, bean.getTotalEnDescargo());
            assertTrue(bean.getTiposInfraccion().length > 0);
            assertTrue(bean.getEstadosInfraccion().length > 0);

            // Ante error de negocio al presentar descargo, recarga la bandeja sin romper
            when(infraccionesService.presentarDescargo(
                    eq("tok-empresa-logisur"),
                    eq(25),
                    any()
            )).thenThrow(new IllegalStateException("Conflicto de estado"));
            bean.setTextoDescargo("Nuevo intento");
            bean.enviarDescargo();
            verify(infraccionesService, org.mockito.Mockito.times(4))
                    .listarInfraccionesParaEmpresa("tok-empresa-logisur");
        }
    }
}
