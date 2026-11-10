package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.fiscalizacion.MotorFiscalizacionLocal;
import uy.edu.fing.grupo07.CargaUY.integration.FaasTrackingClient;
import uy.edu.fing.grupo07.CargaUY.integration.RedVialUruguay;

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
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuias);
        when(queryGuias.setParameter("enCurso", TipoEstado.EN_CURSO)).thenReturn(queryGuias);
        when(queryGuias.getResultList()).thenReturn(List.of());

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
}
