package uy.edu.fing.grupo07.CargaUY.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionEventosServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GuiaDeViajeEJBLocal;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - DetalleViajeBean")
class DetalleViajeBeanTest {

    @Mock
    private GestionEventosServiceLocal eventosService;

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    @Mock
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    private DetalleViajeBean bean;

    @BeforeEach
    void setUp() {
        bean = new DetalleViajeBean(eventosService, balanzaService, guiaDeViajeEJB);
    }

    @Test
    @DisplayName("init sin parámetro carga guías y selecciona por defecto la primera en curso")
    void testInitCargaGuiasYSeleccionaPorDefecto() {
        GuiaDeViaje g1 = new GuiaDeViaje();
        g1.setId(10L);
        g1.setEstado(TipoEstado.SIN_INICIAR);

        GuiaDeViaje g2 = new GuiaDeViaje();
        g2.setId(20L);
        g2.setEstado(TipoEstado.EN_CURSO);

        List<GuiaDeViaje> guias = new ArrayList<>();
        guias.add(g1);
        guias.add(g2);

        when(guiaDeViajeEJB.listarGuias()).thenReturn(guias);
        when(guiaDeViajeEJB.buscarGuias(20L)).thenReturn(g2);
        when(eventosService.listarEventosPorGuia(20L)).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(20L)).thenReturn(Collections.emptyList());

        bean.init();

        assertEquals(2, bean.getGuiasDisponibles().size());
        assertEquals(20L, bean.getGuiaIdSeleccionada());
        assertNotNull(bean.getGuiaActual());
        assertEquals(20L, bean.getGuiaActual().getId());
        assertEquals(0, bean.getTotalEventos());
        assertEquals(0, bean.getTotalPesadas());
    }

    @Test
    @DisplayName("consultarViaje con guiaId nulo resetea los datos del viaje")
    void testConsultarViajeGuiaIdNulo() {
        bean.setGuiaIdSeleccionada(null);
        bean.consultarViaje();

        assertNull(bean.getGuiaActual());
        assertTrue(bean.getEventos().isEmpty());
        assertTrue(bean.getPesadas().isEmpty());
        verifyNoInteractions(eventosService);
        verifyNoInteractions(balanzaService);
    }

    @Test
    @DisplayName("consultarViaje exitoso carga guía, eventos y pesadas")
    void testConsultarViajeExitoso() {
        bean.setGuiaIdSeleccionada(5L);

        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(5L);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");
        guia.setEstado(TipoEstado.EN_CURSO);

        EventoViajeDTO ev = new EventoViajeDTO("uuid-1", LocalDateTime.now(), TipoEvento.INICIO, -34.9, -56.1, 5L, null);
        PesadaBalanzaDTO pes = new PesadaBalanzaDTO(101, LocalDate.now(), LocalTime.now(), 41000, "STP 1234", 5L);

        when(guiaDeViajeEJB.buscarGuias(5L)).thenReturn(guia);
        when(eventosService.listarEventosPorGuia(5L)).thenReturn(List.of(ev));
        when(balanzaService.listarPesadasPorGuia(5L)).thenReturn(List.of(pes));

        bean.consultarViaje();

        assertNotNull(bean.getGuiaActual());
        assertEquals(5L, bean.getGuiaActual().getId());
        assertEquals(1, bean.getTotalEventos());
        assertEquals(1, bean.getTotalPesadas());
        assertEquals(ev, bean.getEventos().get(0));
        assertEquals(pes, bean.getPesadas().get(0));
    }

    @Test
    @DisplayName("sincronizarMockBalanza exitoso consulta mock y refresca datos de la guía impactada")
    void testSincronizarMockBalanzaExitoso() {
        PesadaBalanzaDTO nueva = new PesadaBalanzaDTO(99, LocalDate.now(), LocalTime.now(), 44000, "STP 9999", 7L);
        when(balanzaService.registrarPesadaDesdeMock(any())).thenReturn(nueva);

        GuiaDeViaje guia7 = new GuiaDeViaje();
        guia7.setId(7L);
        when(guiaDeViajeEJB.buscarGuias(7L)).thenReturn(guia7);
        when(eventosService.listarEventosPorGuia(7L)).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(7L)).thenReturn(List.of(nueva));

        bean.sincronizarMockBalanza();

        verify(balanzaService, times(1)).registrarPesadaDesdeMock(any());
        assertEquals(7L, bean.getGuiaIdSeleccionada());
        assertNotNull(bean.getGuiaActual());
        assertEquals(1, bean.getTotalPesadas());
    }

    @Test
    @DisplayName("reportarIncidente exitoso crea DTO y actualiza la lista de eventos")
    void testReportarIncidenteExitoso() {
        bean.setGuiaIdSeleccionada(3L);
        bean.setNuevoIncidenteDescripcion("Rotura de eje en ruta 5");
        bean.setNuevoIncidenteFoto("foto_eje.jpg");
        bean.setNuevoIncidenteLatitud(-33.5);
        bean.setNuevoIncidenteLongitud(-56.2);

        EventoViajeDTO evResult = new EventoViajeDTO(
                "u-inc",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                -33.5,
                -56.2,
                3L,
                new IncidenteDTO("foto_eje.jpg", "Rotura de eje en ruta 5")
        );

        when(eventosService.reportarIncidente(any(ReportarIncidenteDTO.class))).thenReturn(evResult);
        when(eventosService.listarEventosPorGuia(3L)).thenReturn(List.of(evResult));

        bean.reportarIncidente();

        verify(eventosService, times(1)).reportarIncidente(any(ReportarIncidenteDTO.class));
        assertNull(bean.getNuevoIncidenteDescripcion());
        assertNull(bean.getNuevoIncidenteFoto());
        assertEquals(1, bean.getTotalEventos());
    }

    @Test
    @DisplayName("reportarIncidente sin descripción o sin guía no invoca el servicio")
    void testReportarIncidenteValidaciones() {
        bean.setGuiaIdSeleccionada(null);
        bean.setNuevoIncidenteDescripcion("Incidente");
        bean.reportarIncidente();
        verifyNoInteractions(eventosService);

        bean.setGuiaIdSeleccionada(3L);
        bean.setNuevoIncidenteDescripcion("   ");
        bean.reportarIncidente();
        verifyNoInteractions(eventosService);
    }

    @Test
    @DisplayName("helpers de formato y badges retornan clases CSS esperadas")
    void testHelpersFormatoYBadges() {
        GuiaDeViaje g = new GuiaDeViaje();
        g.setId(42L);
        g.setOrigen("Montevideo");
        g.setDestino("Salto");
        g.setEstado(TipoEstado.EN_CURSO);
        Vehiculo v = new Vehiculo();
        v.setMatricula("AAA 1111");
        g.setVehiculo(v);

        String etiqueta = bean.obtenerEtiquetaGuia(g);
        assertTrue(etiqueta.contains("#42"));
        assertTrue(etiqueta.contains("Montevideo"));
        assertTrue(etiqueta.contains("Salto"));
        assertTrue(etiqueta.contains("AAA 1111"));
        assertTrue(etiqueta.contains("EN_CURSO"));

        assertEquals("", bean.obtenerEtiquetaGuia(null));

        assertEquals("badge badge-EN_CURSO", bean.obtenerClaseBadgeEstado(TipoEstado.EN_CURSO));
        assertEquals("badge badge-SIN_INICIAR", bean.obtenerClaseBadgeEstado(null));

        assertEquals("badge badge-INCIDENTE", bean.obtenerClaseBadgeEvento(TipoEvento.INCIDENTE));
        assertEquals("badge badge-INICIO", bean.obtenerClaseBadgeEvento(TipoEvento.INICIO));
        assertEquals("badge badge-otro", bean.obtenerClaseBadgeEvento(null));
    }

    @Test
    @DisplayName("getters seguros de chofer, vehiculo y empresa manejan nulos sin lanzar excepcion")
    void testGettersSegurosManejanNulos() {
        bean.setGuiaActual(null);
        assertEquals("No asignado", bean.getChoferNombreCompleto());
        assertEquals("No asignado", bean.getVehiculoMatricula());
        assertEquals("-", bean.getEmpresaRazonSocial());

        GuiaDeViaje guia = new GuiaDeViaje();
        uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer chofer = new uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer();
        chofer.setNombre("Carlos Pereira");
        chofer.setCi(12345678);
        guia.setChofer(chofer);

        Vehiculo v = new Vehiculo();
        v.setMatricula("STP 1234");
        guia.setVehiculo(v);

        uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa emp = new uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa();
        emp.setRazonSocial("TransLog S.A.");
        guia.setEmpresa(emp);

        bean.setGuiaActual(guia);
        assertEquals("Carlos Pereira (CI: 12345678)", bean.getChoferNombreCompleto());
        assertEquals("STP 1234", bean.getVehiculoMatricula());
        assertEquals("TransLog S.A.", bean.getEmpresaRazonSocial());
    }
}
