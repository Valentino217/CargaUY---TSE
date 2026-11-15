package uy.edu.fing.grupo07.CargaUY.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

    private static final String TOKEN_DETALLE = "tok-detalle-viaje";

    @Mock
    private GestionEventosServiceLocal eventosService;

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    @Mock
    private GuiaDeViajeEJBLocal guiaDeViajeEJB;

    private MockedStatic<SesionActual> sesionActualMock;
    private DetalleViajeBean bean;

    @BeforeEach
    void setUp() {
        sesionActualMock = mockStatic(SesionActual.class);
        sesionActualMock.when(SesionActual::token).thenReturn(TOKEN_DETALLE);
        bean = new DetalleViajeBean(eventosService, balanzaService, guiaDeViajeEJB);
    }

    @AfterEach
    void tearDown() {
        if (sesionActualMock != null) {
            sesionActualMock.close();
        }
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

        when(guiaDeViajeEJB.listarParaOperador(TOKEN_DETALLE)).thenReturn(guias);
        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 20L)).thenReturn(g2);
        when(eventosService.listarEventosPorGuia(TOKEN_DETALLE, 20L)).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(TOKEN_DETALLE, 20L)).thenReturn(Collections.emptyList());

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

        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 5L)).thenReturn(guia);
        when(eventosService.listarEventosPorGuia(TOKEN_DETALLE, 5L)).thenReturn(List.of(ev));
        when(balanzaService.listarPesadasPorGuia(TOKEN_DETALLE, 5L)).thenReturn(List.of(pes));

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
        bean.setGuiaIdSeleccionada(7L);
        PesadaBalanzaDTO nueva = new PesadaBalanzaDTO(99, LocalDate.now(), LocalTime.now(), 44000, "STP 9999", 7L);
        when(balanzaService.registrarPesadaDesdeMock(TOKEN_DETALLE, 7L)).thenReturn(nueva);

        GuiaDeViaje guia7 = new GuiaDeViaje();
        guia7.setId(7L);
        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 7L)).thenReturn(guia7);
        when(eventosService.listarEventosPorGuia(TOKEN_DETALLE, 7L)).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(TOKEN_DETALLE, 7L)).thenReturn(List.of(nueva));

        bean.sincronizarMockBalanza();

        verify(balanzaService, times(1)).registrarPesadaDesdeMock(TOKEN_DETALLE, 7L);
        verify(balanzaService, never()).registrarPesadaDesdeMock();
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

        when(eventosService.reportarIncidente(eq(TOKEN_DETALLE), any(ReportarIncidenteDTO.class))).thenReturn(evResult);
        when(eventosService.listarEventosPorGuia(TOKEN_DETALLE, 3L)).thenReturn(List.of(evResult));

        bean.reportarIncidente();

        verify(eventosService, times(1)).reportarIncidente(eq(TOKEN_DETALLE), any(ReportarIncidenteDTO.class));
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

    @Test
    @DisplayName("enviarTelemetriaFaas con matrícula manual sin guía EN_CURSO dispara fiscalización VIAJE_SIN_GUIA")
    void testEnviarTelemetriaFaasPorMatriculaSinGuiaActiva() {
        uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal motorMock =
                mock(uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal.class);
        bean.setMotorFiscalizacion(motorMock);
        bean.setGuiaIdSeleccionada(null);
        bean.setTelemetriaMatricula("  STP 4321  ");
        assertEquals("  STP 4321  ", bean.getTelemetriaMatricula());
        bean.setTelemetriaLatitud(-33.5200);
        bean.setTelemetriaLongitud(-55.8100);
        bean.setEvaluarDesvioInmediato(true);

        when(eventosService.guardarEvento(eq(TOKEN_DETALLE), any(EventoViajeDTO.class))).thenThrow(
                new uy.edu.fing.grupo07.CargaUY.exception.BusinessException(
                        "El vehículo STP 4321 no tiene ninguna guía de viaje EN_CURSO; se derivó a fiscalización automática por VIAJE_SIN_GUIA."
                )
        );
        when(motorMock.evaluarVehiculoSinGuia(eq("STP 4321"), anyString())).thenReturn(true);

        bean.enviarTelemetriaFaas();

        verify(motorMock, times(1)).evaluarVehiculoSinGuia(eq("STP 4321"), contains("sin viaje EN_CURSO"));
        assertNotNull(bean.getUltimoResultadoFaas());
        assertTrue(bean.getUltimoResultadoFaas().contains("VIAJE_SIN_GUIA"));
    }

    @Test
    @DisplayName("enviarTelemetriaFaas con matrícula inexistente no reporta falso VIAJE_SIN_GUIA")
    void testEnviarTelemetriaFaasPorMatriculaInexistente() {
        uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal motorMock =
                mock(uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal.class);
        bean.setMotorFiscalizacion(motorMock);
        bean.setGuiaIdSeleccionada(null);
        bean.setTelemetriaMatricula("ZZZ 9999");
        bean.setTelemetriaLatitud(-33.5200);
        bean.setTelemetriaLongitud(-55.8100);
        bean.setEvaluarDesvioInmediato(true);

        when(eventosService.guardarEvento(eq(TOKEN_DETALLE), any(EventoViajeDTO.class))).thenThrow(
                new uy.edu.fing.grupo07.CargaUY.exception.BusinessException(
                        "No existe vehículo registrado ni guía de viaje EN_CURSO para la matrícula ZZZ 9999"
                )
        );

        bean.enviarTelemetriaFaas();

        verifyNoInteractions(motorMock);
        assertNull(bean.getUltimoResultadoFaas());
    }

    @Test
    @DisplayName("enviarTelemetriaFaas con matrícula manual que sí tiene guía EN_CURSO usa su corredor y evalúa esa guía")
    void testEnviarTelemetriaFaasConMatriculaManualDeCamionConGuiaEnCursoEvaluaGuiaCorrecta() {
        uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal motorMock =
                mock(uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal.class);
        bean.setMotorFiscalizacion(motorMock);

        Vehiculo v1 = new Vehiculo();
        v1.setMatricula("AA 1111");
        GuiaDeViaje g1 = new GuiaDeViaje();
        g1.setId(1L);
        g1.setOrigen("Montevideo");
        g1.setDestino("Colonia");
        g1.setEstado(TipoEstado.EN_CURSO);
        g1.setVehiculo(v1);

        Vehiculo v2 = new Vehiculo();
        v2.setMatricula("BB 2222");
        GuiaDeViaje g2 = new GuiaDeViaje();
        g2.setId(2L);
        g2.setOrigen("Salto");
        g2.setDestino("Artigas");
        g2.setEstado(TipoEstado.EN_CURSO);
        g2.setVehiculo(v2);

        when(guiaDeViajeEJB.listarParaOperador(TOKEN_DETALLE)).thenReturn(List.of(g1, g2));
        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 1L)).thenReturn(g1);
        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 2L)).thenReturn(g2);
        when(eventosService.listarEventosPorGuia(eq(TOKEN_DETALLE), anyLong())).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(eq(TOKEN_DETALLE), anyLong())).thenReturn(Collections.emptyList());

        bean.setGuiaIdSeleccionada(1L);
        bean.consultarViaje();

        bean.setTelemetriaMatricula("  bb 2222  ");
        bean.cargarCoordenadasEnRuta();
        assertTrue(bean.getTelemetriaDescripcion().contains("Salto -> Artigas"));

        uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje persistidoG2 =
                new uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje();
        persistidoG2.setGuia(g2);
        when(eventosService.guardarEvento(eq(TOKEN_DETALLE), any(EventoViajeDTO.class))).thenReturn(persistidoG2);
        when(motorMock.evaluarGuia(2L)).thenReturn(Collections.emptyList());

        bean.setEvaluarDesvioInmediato(true);
        bean.enviarTelemetriaFaas();

        verify(eventosService).guardarEvento(eq(TOKEN_DETALLE), argThat(dto ->
                "bb 2222".equals(dto.matricula())
                        && dto.distanciaDesvioKm() != null
                        && dto.distanciaDesvioKm() < 1.0
        ));
        verify(motorMock, times(1)).evaluarGuia(2L);
        verify(motorMock, never()).evaluarGuia(1L);
        assertEquals(2L, bean.getGuiaIdSeleccionada());
    }

    @Test
    @DisplayName("refrescarPorPollMock, isPollAutomaticoActivo y estado de nodos reflejan estado EN_CURSO y registro periférico")
    void testPollAutomaticoYEstadoNodos() {
        uy.edu.fing.grupo07.CargaUY.service.GestionNodosPerifericosServiceImpl registro =
                new uy.edu.fing.grupo07.CargaUY.service.GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-TRK-01", true);
        registro.setHabilitado("NODO-BAL-01", true);
        bean.setNodosPerifericosService(registro);

        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(50L);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");
        guia.setEstado(TipoEstado.EN_CURSO);

        when(guiaDeViajeEJB.buscarGuias(TOKEN_DETALLE, 50L)).thenReturn(guia);
        when(eventosService.listarEventosPorGuia(TOKEN_DETALLE, 50L)).thenReturn(Collections.emptyList());
        when(balanzaService.listarPesadasPorGuia(TOKEN_DETALLE, 50L)).thenReturn(Collections.emptyList());

        bean.setGuiaIdSeleccionada(50L);
        bean.refrescarPorPollMock();

        assertTrue(bean.isPollAutomaticoActivo());
        assertTrue(bean.isNodoTrackingActivoSistema());
        assertEquals(1, bean.getCantidadBalanzasActivas());
        assertEquals(uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico.RUTA_NORMAL.getEtiqueta(),
                bean.getResumenModoTracking());

        registro.setHabilitado("NODO-TRK-01", false);
        registro.setHabilitado("NODO-BAL-01", false);
        assertFalse(bean.isNodoTrackingActivoSistema());
        assertEquals(0, bean.getCantidadBalanzasActivas());

        guia.setEstado(TipoEstado.FINALIZADO);
        bean.refrescarPorPollMock();
        assertFalse(bean.isPollAutomaticoActivo());
    }

    @Test
    @DisplayName("getDatosMapaJson sin guía seleccionada retorna guiaSeleccionada false y arreglos vacíos")
    void getDatosMapaJsonSinGuiaSeleccionadaRetornaFalse() {
        bean.setGuiaActual(null);
        bean.setEventos(Collections.emptyList());

        assertEquals("", bean.getCorredorVialResuelto());

        try (JsonReader reader = Json.createReader(new StringReader(bean.getDatosMapaJson()))) {
            JsonObject json = reader.readObject();
            assertFalse(json.getBoolean("guiaSeleccionada"));
            assertTrue(json.getJsonArray("rutaPlanificada").isEmpty());
            assertTrue(json.getJsonArray("eventos").isEmpty());
        }
    }

    @Test
    @DisplayName("getDatosMapaJson con guía y eventos serializa ruta MTOP y trayectoria GPS filtrando coordenadas nulas")
    void getDatosMapaJsonConGuiaYEventosSerializaRutaYEventosFiltrandoCeros() {
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(1001L);
        guia.setOrigen("Puerto de Montevideo");
        guia.setDestino("Rivera (Frontera)");
        guia.setEstado(TipoEstado.EN_CURSO);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula("STP 1001");
        guia.setVehiculo(vehiculo);

        EventoViajeDTO sinGps = new EventoViajeDTO(
                "u-0",
                LocalDateTime.of(2026, 11, 13, 8, 0),
                TipoEvento.INICIO,
                0.0,
                0.0,
                1001L,
                null
        );
        EventoViajeDTO evNormal = new EventoViajeDTO(
                "u-1",
                LocalDateTime.of(2026, 11, 13, 9, 15),
                TipoEvento.POSICION,
                -33.5200,
                -56.2000,
                1001L,
                null,
                1.25,
                24500.0,
                "Granos",
                "AgroSur",
                "STP 1001"
        );
        EventoViajeDTO evDesvio = new EventoViajeDTO(
                "u-2",
                LocalDateTime.of(2026, 11, 13, 10, 30),
                TipoEvento.INCIDENTE,
                -32.7500,
                -55.1000,
                1001L,
                new IncidenteDTO(null, "Desvío detectado en ruta"),
                18.4,
                null,
                null,
                null,
                "STP 1001"
        );

        bean.setGuiaActual(guia);
        bean.setEventos(List.of(sinGps, evNormal, evDesvio));

        String jsonStr = bean.getDatosMapaJson();
        try (JsonReader reader = Json.createReader(new StringReader(jsonStr))) {
            JsonObject json = reader.readObject();
            assertTrue(json.getBoolean("guiaSeleccionada"));
            assertEquals(1001L, json.getJsonNumber("guiaId").longValue());
            assertEquals("STP 1001", json.getString("matricula"));
            assertFalse(json.getString("corredorVial").isBlank());

            JsonArray ruta = json.getJsonArray("rutaPlanificada");
            assertTrue(ruta.size() >= 2, "Debe resolver la polilínea MTOP entre Montevideo y Rivera");

            JsonArray eventos = json.getJsonArray("eventos");
            assertEquals(2, eventos.size(), "El evento (0.0, 0.0) debe filtrarse del mapa");

            JsonObject primero = eventos.getJsonObject(0);
            assertEquals("u-1", primero.getString("uuid"));
            assertFalse(primero.getBoolean("desviado"));
            assertEquals(24500.0, primero.getJsonNumber("cantidadKg").doubleValue(), 0.01);
            assertEquals("Granos", primero.getString("mercaderia"));
            assertEquals("AgroSur", primero.getString("empresaCarga"));

            JsonObject segundo = eventos.getJsonObject(1);
            assertEquals("u-2", segundo.getString("uuid"));
            assertEquals("INCIDENTE", segundo.getString("tipo"));
            assertTrue(segundo.getBoolean("desviado"));
            assertEquals(18.4, segundo.getJsonNumber("desvioKm").doubleValue(), 0.01);
            assertEquals("Desvío detectado en ruta", segundo.getString("incidente"));
        }
    }
}
