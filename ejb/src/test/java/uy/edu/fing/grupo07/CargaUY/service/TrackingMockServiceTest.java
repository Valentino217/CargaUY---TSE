package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.integration.RedVialUruguay;
import uy.edu.fing.grupo07.CargaUY.integration.TrackingClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingMockServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private GestionEventosServiceLocal eventosService;

    @Mock
    private MotorFiscalizacionLocal motorFiscalizacion;

    @Mock
    private TypedQuery<GuiaDeViaje> queryGuias;

    private TrackingMockServiceImpl trackingMockService;

    @BeforeEach
    void setUp() {
        trackingMockService = new TrackingMockServiceImpl(
                em,
                eventosService,
                motorFiscalizacion,
                new FaasTrackingClient()
        );
    }

    @AfterEach
    void tearDown() {
        trackingMockService.cerrar();
    }

    @Test
    void deberiaRecorrerRutaSinDesviosYPermanecerEnDestinoHastaFinalizarViaje() {
        GuiaDeViaje guia = crearGuiaActiva(101L, "Montevideo", "Rivera", "STP1234");
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        when(queryGuias.getResultList()).thenReturn(List.of(guia));

        RedVialUruguay.RutaPlanificada rutaEsperada = RedVialUruguay.calcularRuta("Montevideo", "Rivera");
        double[] coordsDestino = rutaEsperada.destino().coordenadas();

        // Emitimos 15 pasos (la ruta base tiene 10 puntos; a partir del paso 10 se queda en destino)
        for (int i = 0; i < 15; i++) {
            int emitidos = trackingMockService.emitirPasoGuiasActivas();
            assertEquals(1, emitidos);
        }

        ArgumentCaptor<EventoViajeDTO> captor = ArgumentCaptor.forClass(EventoViajeDTO.class);
        verify(eventosService, times(15)).guardarEvento(captor.capture());
        verify(motorFiscalizacion, times(15)).evaluarGuia(101L);

        List<EventoViajeDTO> eventos = captor.getAllValues();
        assertEquals(15, eventos.size());
        assertEquals(0L, trackingMockService.getTotalDesviosEmitidos());

        // Todos los puntos deben ser POSICION y estar dentro del corredor (sin desvío > 5 km)
        for (EventoViajeDTO ev : eventos) {
            assertEquals(TipoEvento.POSICION, ev.tipo());
            assertNotNull(ev.distanciaDesvioKm());
            assertTrue(ev.distanciaDesvioKm() <= 5.0,
                    "Sin desvíos configurados todos los puntos deben ir por la ruta esperada, pero dio " + ev.distanciaDesvioKm());
        }

        // Los últimos 6 pasos (índices 9 a 14) deben haberse quedado emitiendo en las coordenadas del destino (Rivera)
        for (int idx = 9; idx < 15; idx++) {
            EventoViajeDTO evEnDestino = eventos.get(idx);
            assertEquals(coordsDestino[0], evEnDestino.latitud(), 0.01);
            assertEquals(coordsDestino[1], evEnDestino.longitud(), 0.01);
            assertTrue(evEnDestino.incidente().descripcion().contains("finalización"));
        }

        // Cuando el chofer finaliza el viaje (ya no está EN_CURSO), deja de emitir puntos
        when(queryGuias.getResultList()).thenReturn(List.of());
        int emitidosTrasFinalizar = trackingMockService.emitirPasoGuiasActivas();
        assertEquals(0, emitidosTrasFinalizar);
    }

    @Test
    void deberiaInyectarCantidadConfiguradaDeEpisodiosDeDesvioYRetornarARuta() {
        GuiaDeViaje guia = crearGuiaActiva(202L, "Montevideo", "Salto", "STP9999");

        // Construimos plan con 2 desvíos deseados
        TrackingMockServiceImpl.EstadoSimulacionGuia estado =
                TrackingMockServiceImpl.construirEstadoSimulacion(guia, 2);

        FaasTrackingClient faas = new FaasTrackingClient();
        int episodiosDetectados = 0;
        boolean enDesvio = false;

        // Avanzamos suficientes pasos para llegar al destino
        TrackingMockServiceImpl.PasoSimulado ultimoPaso = null;
        for (int i = 0; i < 20; i++) {
            TrackingMockServiceImpl.PasoSimulado paso = estado.siguientePaso();
            ultimoPaso = paso;
            EventoViajeDTO dto = new EventoViajeDTO(
                    "uuid-" + i,
                    null,
                    TipoEvento.POSICION,
                    paso.latitud(),
                    paso.longitud(),
                    guia.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    "STP9999"
            );
            double distKm = faas.procesarEvento(dto, guia.getOrigen(), guia.getDestino(), guia.getPolilineaRuta())
                    .distanciaDesvioKm();

            if (distKm > 5.0) {
                if (!enDesvio) {
                    episodiosDetectados++;
                    enDesvio = true;
                }
            } else {
                enDesvio = false;
            }
        }

        assertEquals(2, episodiosDetectados, "El plan debe generar exactamente los 2 episodios de desvío configurados");
        assertNotNull(ultimoPaso);
        assertTrue(ultimoPaso.enDestino(), "Al completar la ruta debe permanecer en el destino");
    }

    @Test
    void deberiaIniciarDetenerYReiniciarMockCorrectamente() {
        lenient().when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        lenient().when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        lenient().when(queryGuias.getResultList()).thenReturn(List.of());

        assertFalse(trackingMockService.estaActivo());

        trackingMockService.iniciar(24, 3);
        assertTrue(trackingMockService.estaActivo());
        assertEquals(24, trackingMockService.getEventosPorMinuto());
        assertEquals(3, trackingMockService.getCantidadDesvios());

        trackingMockService.reiniciarRecorridos();
        assertTrue(trackingMockService.getUltimoResultado().contains("reiniciados"));

        trackingMockService.detener();
        assertFalse(trackingMockService.estaActivo());
    }

    @Test
    void deberiaDelegarEnNodoPerifericoTrackingEIngerirPuntosDeFormaIdempotente() {
        TrackingClient trackingClientMock = mock(TrackingClient.class);
        lenient().when(trackingClientMock.getEndpointUrl()).thenReturn("http://mock-tracking:8080");
        lenient().when(trackingClientMock.verificarSalud()).thenReturn(true);

        TrackingMockServiceImpl serviceConPeriferico = new TrackingMockServiceImpl(
                em,
                eventosService,
                motorFiscalizacion,
                new FaasTrackingClient(),
                trackingClientMock
        );

        GuiaDeViaje guia = crearGuiaActiva(303L, "Montevideo", "Colonia del Sacramento", "STP7777");
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        when(queryGuias.getResultList()).thenReturn(List.of(guia));

        EventoViajeDTO puntoPeriferico = new EventoViajeDTO(
                "uuid-perif-1",
                LocalDateTime.now(),
                TipoEvento.POSICION,
                -34.4626,
                -57.8400,
                303L,
                null,
                0.25,
                null,
                null,
                null,
                "STP7777"
        );

        when(trackingClientMock.iniciar(eq(18), eq(1), anyList(), anyString(), anyString())).thenReturn(
                new TrackingClient.EstadoNodoTracking(
                        true, 18, 1, 1L, 0L,
                        "Simulación iniciada en nodo periférico",
                        List.of(puntoPeriferico)
                )
        );
        when(eventosService.existeEvento("uuid-perif-1")).thenReturn(false, true);

        serviceConPeriferico.iniciar(18, 1);

        assertTrue(serviceConPeriferico.estaActivo());
        assertTrue(serviceConPeriferico.isNodoPerifericoOnline());
        assertEquals("http://mock-tracking:8080", serviceConPeriferico.getEndpointNodoPeriferico());
        verify(eventosService, times(1)).guardarEvento(any(EventoViajeDTO.class));
        verify(motorFiscalizacion, times(1)).evaluarGuia(303L);

        // Si el mismo UUID llega de nuevo al sincronizar, no debe duplicarse
        when(trackingClientMock.sincronizarGuiasYDrenar(anyList(), eq(true), anyString())).thenReturn(
                new TrackingClient.EstadoNodoTracking(
                        true, 18, 1, 1L, 0L,
                        "Sincronizado",
                        List.of(puntoPeriferico)
                )
        );
        serviceConPeriferico.sincronizarConNodoPeriferico();
        verify(eventosService, times(1)).guardarEvento(any(EventoViajeDTO.class));

        serviceConPeriferico.cerrar();
    }

    @Test
    @DisplayName("El coordinador automático respeta encendido/apagado, modo de desvíos y sondeo de balanzas")
    void testCicloAutomaticoConRegistroDeNodos() {
        GestionNodosPerifericosServiceImpl registro = new GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-TRK-01", true);
        GestionBalanzaServiceLocal balanzaMock = mock(GestionBalanzaServiceLocal.class);
        TrackingClient trackingClientMock = mock(TrackingClient.class);
        lenient().when(trackingClientMock.getEndpointUrl()).thenReturn("http://mock-tracking:8080");

        TrackingMockServiceImpl serviceAuto = new TrackingMockServiceImpl(
                em,
                eventosService,
                motorFiscalizacion,
                new FaasTrackingClient(),
                null
        );
        serviceAuto.setNodosPerifericosService(registro);
        serviceAuto.setBalanzaService(balanzaMock);

        GuiaDeViaje guia = crearGuiaActiva(404L, "Montevideo", "Canelones", "STP 4040");
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        when(queryGuias.getResultList()).thenReturn(List.of(guia));

        // 1. Modo CON_DESVIO fuerza 1 desvío automático en tick 1 (15s cadence) y sondeo de balanza
        registro.cambiarModo("NODO-TRK-01", uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico.CON_DESVIO);
        serviceAuto.ejecutarCicloAutomaticoSeguro();
        assertTrue(serviceAuto.estaActivo());
        assertEquals(1, serviceAuto.getCantidadDesvios());
        verify(balanzaMock, times(1)).sondearBalanzasPeriodico();
        verify(eventosService, times(1)).guardarEvento(any(EventoViajeDTO.class));

        // Tick 2 y 3 (5s y 10s): no deben emitir pasos GPS hasta completar el intervalo de 15s (tick 4),
        // aunque tick 3 sí sondea balanzas cada 10s y notificarCambioBalanza no emite GPS.
        serviceAuto.ejecutarCicloAutomaticoSeguro(); // tick 2
        serviceAuto.ejecutarCicloAutomaticoSeguro(); // tick 3 -> balanzas (times 2), sin GPS
        serviceAuto.notificarCambioBalanza();        // cambio balanza -> balanzas (times 3), sin GPS
        verify(eventosService, times(1)).guardarEvento(any(EventoViajeDTO.class));
        verify(balanzaMock, times(3)).sondearBalanzasPeriodico();

        serviceAuto.ejecutarCicloAutomaticoSeguro(); // tick 4 (15s) -> nuevo paso GPS
        verify(eventosService, times(2)).guardarEvento(any(EventoViajeDTO.class));

        // 2. Apagar nodo de tracking detiene emisión automática y detiene remoto si existe
        long antes = serviceAuto.getTotalEventosEmitidos();
        registro.setHabilitado("NODO-TRK-01", false);
        serviceAuto.ejecutarCicloAutomaticoSeguro();
        assertFalse(serviceAuto.estaActivo());
        assertEquals(0, serviceAuto.ejecutarPasoAutomaticoTracking());
        assertEquals(antes, serviceAuto.getTotalEventosEmitidos());

        // 3. Probar detección de parada remota con trackingClient
        TrackingMockServiceImpl serviceConCliente = new TrackingMockServiceImpl(
                em, eventosService, motorFiscalizacion, new FaasTrackingClient(), trackingClientMock
        );
        serviceConCliente.setNodosPerifericosService(registro);
        registro.setHabilitado("NODO-TRK-01", true);
        serviceConCliente.notificarCambioConfiguracionNodos();
        assertTrue(serviceConCliente.estaActivo());

        registro.setHabilitado("NODO-TRK-01", false);
        serviceConCliente.notificarCambioConfiguracionNodos();
        assertFalse(serviceConCliente.estaActivo());
        verify(trackingClientMock, atLeastOnce()).detener(anyString());

        serviceAuto.cerrar();
        serviceConCliente.cerrar();
    }

    private GuiaDeViaje crearGuiaActiva(Long id, String origen, String destino, String matricula) {
        RedVialUruguay.RutaPlanificada ruta = RedVialUruguay.calcularRuta(origen, destino);
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMatricula(matricula);

        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(id);
        guia.setOrigen(origen);
        guia.setDestino(destino);
        guia.setEstado(TipoEstado.EN_CURSO);
        guia.setVehiculo(vehiculo);
        guia.setCorredorVial(ruta.corredorVial());
        guia.setPolilineaRuta(RedVialUruguay.serializarPolilinea(ruta.polilinea()));
        return guia;
    }

    @Test
    @DisplayName("Overloads tokenizados de tracking fallan cerrado sin ControlAcceso y verifican rol admin")
    void testOverloadsTokenizadosYFailClosed() throws Exception {
        TrackingMockServiceImpl svc = new TrackingMockServiceImpl(
                em, eventosService, motorFiscalizacion, new FaasTrackingClient(), null
        );
        try {
            assertThrows(IllegalStateException.class, () -> svc.iniciar("tok-admin", 10, 0));
            assertThrows(IllegalStateException.class, () -> svc.detener("tok-admin"));
            assertThrows(IllegalStateException.class, () -> svc.reiniciarRecorridos("tok-admin"));
            assertThrows(IllegalStateException.class, () -> svc.emitirPasoGuiasActivas("tok-admin"));
            assertThrows(IllegalStateException.class, () -> svc.sincronizarConNodoPeriferico("tok-admin"));
            assertThrows(IllegalStateException.class, () -> svc.notificarCambioConfiguracionNodos("tok-admin"));
            assertThrows(IllegalStateException.class, () -> svc.notificarCambioBalanza("tok-admin"));

            uy.edu.fing.grupo07.CargaUY.security.ControlAcceso controlMock =
                    mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
            java.lang.reflect.Field field = TrackingMockServiceImpl.class.getDeclaredField("control");
            field.setAccessible(true);
            field.set(svc, controlMock);

            @SuppressWarnings("unchecked")
            jakarta.persistence.TypedQuery<GuiaDeViaje> qGuias = mock(jakarta.persistence.TypedQuery.class);
            when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(qGuias);
            when(qGuias.setParameter(anyString(), any())).thenReturn(qGuias);
            when(qGuias.getResultList()).thenReturn(List.of());

            svc.iniciar("tok-admin", 12, 0);
            assertTrue(svc.estaActivo());
            svc.emitirPasoGuiasActivas("tok-admin");
            svc.sincronizarConNodoPeriferico("tok-admin");
            svc.reiniciarRecorridos("tok-admin");
            svc.notificarCambioConfiguracionNodos("tok-admin");
            svc.notificarCambioBalanza("tok-admin");
            svc.detener("tok-admin");
            assertFalse(svc.estaActivo());

            verify(controlMock, times(2)).admin("tok-admin");
            verify(controlMock, times(5)).exigir(eq("tok-admin"), any(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario[].class));
        } finally {
            svc.cerrar();
        }
    }

    @Test
    @DisplayName("Retoma desde el último evento sembrado y reinicia el nodo periférico si la BD no tiene eventos mock")
    void deberiaContinuarDesdeUltimoEventoSembradoYReiniciarNodoPerifericoSiNoHayEventosMockEnDb() {
        GuiaDeViaje guia = crearGuiaActiva(1001L, "Puerto de Montevideo", "Rivera (Frontera)", "STP 1234");
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        when(queryGuias.getResultList()).thenReturn(List.of(guia));

        EventoViajeDTO evInicio = new EventoViajeDTO(
                "seed-inicio",
                LocalDateTime.now().minusHours(4),
                TipoEvento.INICIO,
                -34.9011,
                -56.1645,
                1001L,
                null,
                0.0,
                30000.0,
                "Eucaliptus",
                "TransPlata",
                "STP 1234"
        );
        EventoViajeDTO evFlorida = new EventoViajeDTO(
                "seed-florida",
                LocalDateTime.now().minusHours(1),
                TipoEvento.INCIDENTE,
                -34.0956,
                -56.2088,
                1001L,
                new IncidenteDTO("pinchadura.jpg", "Pinchadura de neumático"),
                0.5,
                30000.0,
                "Eucaliptus",
                "TransPlata",
                "STP 1234"
        );
        EventoViajeDTO evMock = new EventoViajeDTO(
                "mock-1",
                LocalDateTime.now(),
                TipoEvento.POSICION,
                -33.74,
                -56.24,
                1001L,
                new IncidenteDTO(null, "Mock Tracking GPS - En ruta tramo 1/9"),
                0.0,
                null,
                null,
                null,
                "STP 1234"
        );

        // 1. Modo local: sin eventos Mock en BD debe avanzar hacia el norte desde Florida (sin volver a Montevideo ni saltar a Rivera)
        when(eventosService.listarEventosPorGuia(1001L)).thenReturn(List.of(evInicio, evFlorida));
        assertEquals(1, trackingMockService.emitirPasoGuiasActivas());

        ArgumentCaptor<EventoViajeDTO> captorLocal = ArgumentCaptor.forClass(EventoViajeDTO.class);
        verify(eventosService).guardarEvento(captorLocal.capture());
        EventoViajeDTO primerPaso = captorLocal.getValue();
        assertTrue(primerPaso.latitud() > -34.0956 && primerPaso.latitud() < -30.95,
                "El primer paso debe ubicarse al norte de Florida sin saltar a Rivera, lat=" + primerPaso.latitud());
        assertTrue(primerPaso.distanciaDesvioKm() <= 0.05,
                "La polilínea recortada debe mantenerse sobre el corredor original, desvío=" + primerPaso.distanciaDesvioKm());
        assertTrue(primerPaso.incidente().descripcion().contains("tramo 1/9"));

        // 2. Nodo periférico remoto: reinicia antes de emitir cuando no hay eventos mock,
        //    no reinicia en ticks siguientes mientras existan eventos mock, y vuelve a reiniciar si se limpia la BD.
        TrackingClient trackingClientMock = mock(TrackingClient.class);
        lenient().when(trackingClientMock.getEndpointUrl()).thenReturn("http://mock-tracking:8080");
        when(trackingClientMock.reiniciar(anyList(), anyString())).thenReturn(
                new TrackingClient.EstadoNodoTracking(false, 12, 0, 0L, 0L, "Reiniciado", List.of())
        );
        when(trackingClientMock.emitirPaso(eq(12), eq(0), anyList(), anyString(), anyString())).thenReturn(
                new TrackingClient.EstadoNodoTracking(true, 12, 0, 1L, 0L, "Paso emitido", List.of(evMock))
        );
        when(eventosService.existeEvento("mock-1")).thenReturn(false);

        when(eventosService.listarEventosPorGuia(1001L))
                .thenReturn(List.of(evInicio, evFlorida))
                .thenReturn(List.of(evInicio, evFlorida))
                .thenReturn(List.of(evInicio, evFlorida, evMock))
                .thenReturn(List.of(evInicio, evFlorida, evMock))
                .thenReturn(List.of(evInicio, evFlorida))
                .thenReturn(List.of(evInicio, evFlorida));

        TrackingMockServiceImpl serviceConPeriferico = new TrackingMockServiceImpl(
                em,
                eventosService,
                motorFiscalizacion,
                new FaasTrackingClient(),
                trackingClientMock
        );
        try {
            serviceConPeriferico.emitirPasoGuiasActivas();
            serviceConPeriferico.emitirPasoGuiasActivas();
            serviceConPeriferico.emitirPasoGuiasActivas();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TrackingClient.GuiaActivaPayload>> payloadCaptor = ArgumentCaptor.forClass(List.class);
            InOrder orden = inOrder(trackingClientMock);
            orden.verify(trackingClientMock, calls(1)).reiniciar(payloadCaptor.capture(), anyString());
            orden.verify(trackingClientMock, calls(2)).emitirPaso(eq(12), eq(0), anyList(), anyString(), anyString());
            orden.verify(trackingClientMock, calls(1)).reiniciar(anyList(), anyString());
            orden.verify(trackingClientMock, calls(1)).emitirPaso(eq(12), eq(0), anyList(), anyString(), anyString());
            verify(trackingClientMock, times(2)).reiniciar(anyList(), anyString());

            List<double[]> polilineaEnviada = RedVialUruguay.deserializarPolilinea(
                    payloadCaptor.getValue().get(0).polilineaRuta());
            assertFalse(polilineaEnviada.isEmpty());
            assertTrue(polilineaEnviada.get(0)[0] > -34.0956 && polilineaEnviada.get(0)[0] < -30.95,
                    "La polilínea enviada tras reiniciar debe comenzar adelante de Florida, lat=" + polilineaEnviada.get(0)[0]);
        } finally {
            serviceConPeriferico.cerrar();
        }
    }
}
