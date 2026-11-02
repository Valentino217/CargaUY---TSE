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
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - BandejaFiscalizacionBean (Punto D.5)")
class BandejaFiscalizacionBeanTest {

    @Mock
    private GestionInfraccionesServiceLocal infraccionesService;

    private BandejaFiscalizacionBean bean;

    @BeforeEach
    void setUp() {
        bean = new BandejaFiscalizacionBean(infraccionesService);
    }

    private InfraccionDTO crearCaso(int id, EstadoInfraccion estado) {
        return new InfraccionDTO(
                id,
                LocalDate.now(),
                TipoInfraccion.VIAJE_SIN_GUIA,
                estado,
                "Evidencia balanza",
                null,
                null,
                null,
                "123456",
                "TransSur",
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Debe cargar casos y ejecutar notificar, confirmar y desestimar delegando SesionActual.token()")
    void testOperacionesDeleganTokenDeSesionAutenticada() {
        InfraccionDTO detectada = crearCaso(10, EstadoInfraccion.DETECTADA);
        InfraccionDTO notificada = crearCaso(10, EstadoInfraccion.NOTIFICADA);
        InfraccionDTO confirmada = crearCaso(10, EstadoInfraccion.CONFIRMADA);
        InfraccionDTO desestimada = crearCaso(11, EstadoInfraccion.DESESTIMADA);

        try (MockedStatic<SesionActual> sesion = mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::token).thenReturn("tok-func-autenticado");

            when(infraccionesService.listarInfracciones("tok-func-autenticado", null, null))
                    .thenReturn(List.of(detectada, confirmada, desestimada));

            bean.init();
            assertEquals(3, bean.getTotalCasos());
            assertEquals(1, bean.getTotalPendientesRevision());
            assertEquals(1, bean.getTotalConfirmadas());
            assertEquals(1, bean.getTotalDesestimadas());

            bean.seleccionarCaso(detectada);
            assertFalse(bean.isCasoResuelto());

            // No permite confirmar directamente desde DETECTADA sin notificar antes
            bean.setFundamentosResolucion("Fundamento prematuro");
            bean.confirmarSancion();
            verify(infraccionesService, never()).confirmarInfraccion(anyString(), anyInt(), anyString());

            // Notificar empresa usando token de sesión
            when(infraccionesService.notificarInfraccion("tok-func-autenticado", 10)).thenReturn(notificada);
            bean.notificarEmpresa();
            assertEquals(EstadoInfraccion.NOTIFICADA, bean.getCasoSeleccionado().estado());

            // Rechaza fundamento en blanco
            bean.setFundamentosResolucion("   ");
            bean.confirmarSancion();
            verify(infraccionesService, never()).confirmarInfraccion(anyString(), anyInt(), anyString());

            // Confirmar sanción con token autenticado y fundamento válido
            when(infraccionesService.confirmarInfraccion("tok-func-autenticado", 10, "Exceso probado"))
                    .thenReturn(confirmada);
            bean.setFundamentosResolucion("  Exceso probado  ");
            bean.confirmarSancion();
            assertEquals(EstadoInfraccion.CONFIRMADA, bean.getCasoSeleccionado().estado());
            assertTrue(bean.isCasoResuelto());

            // Desestimar otro caso con token autenticado
            bean.seleccionarCaso(notificada);
            bean.setFundamentosResolucion("  Error de sensor en balanza  ");
            when(infraccionesService.desestimarInfraccion("tok-func-autenticado", 10, "Error de sensor en balanza"))
                    .thenReturn(desestimada);
            bean.desestimarCaso();
            assertEquals(EstadoInfraccion.DESESTIMADA, bean.getCasoSeleccionado().estado());
        }
    }
}
