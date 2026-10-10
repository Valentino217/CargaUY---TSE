package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.EventoViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;
import uy.edu.fing.grupo07.CargaUY.dto.IncidenteDTO;
import uy.edu.fing.grupo07.CargaUY.dto.SyncResultDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - GestionEventosService (Deduplicación e Idempotencia)")
class GestionEventosServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Long> queryCount;

    @Mock
    private TypedQuery<EventoViaje> queryEventos;

    private GestionEventosServiceImpl service;
    private GuiaDeViaje guiaPrueba;

    @BeforeEach
    void setUp() {
        service = new GestionEventosServiceImpl(em);
        guiaPrueba = new GuiaDeViaje();
        guiaPrueba.setId(1L);
        guiaPrueba.setFecha(LocalDate.now());
        guiaPrueba.setOrigen("Montevideo");
        guiaPrueba.setDestino("Rivera");
        guiaPrueba.setRubroCliente("Forestal");
        guiaPrueba.setVolumenCarga(45.0f);
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
    }

    @Test
    @DisplayName("Debe persistir un nuevo evento cuando el UUID no existe")
    void testGuardarEventoExitoso() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-001",
                LocalDateTime.now(),
                TipoEvento.CARGA,
                -34.90,
                -56.16,
                1L,
                null
        );

        EventoViaje resultado = service.guardarEvento(dto);

        assertNotNull(resultado);
        assertEquals("uuid-001", resultado.getUuid());
        assertEquals(TipoEvento.CARGA, resultado.getTipo());
        verify(em).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Debe rechazar un evento con BusinessException si el UUID ya fue registrado")
    void testGuardarEventoDuplicado() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), eq("uuid-duplicado"))).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(1L);

        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-duplicado",
                LocalDateTime.now(),
                TipoEvento.INICIO,
                -34.90,
                -56.16,
                1L,
                null
        );

        assertThrows(BusinessException.class, () -> service.guardarEvento(dto));
        verify(em, never()).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Debe procesar lote mixto devolviendo ACCEPTED para nuevos y DUPLICATE para repetidos")
    void testGuardarEventosLoteDeduplicacion() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), eq("uuid-nuevo"))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), eq("uuid-existente"))).thenReturn(queryCount);

        when(queryCount.getSingleResult())
                .thenReturn(0L) // primera comprobación existeEvento (lote: uuid-nuevo)
                .thenReturn(0L) // segunda comprobación dentro de guardarEvento (uuid-nuevo)
                .thenReturn(1L); // comprobación para uuid-existente (devuelve 1 -> duplicado)

        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO eventoNuevo = new EventoViajeDTO(
                "uuid-nuevo", LocalDateTime.now(), TipoEvento.CARGA, -34.90, -56.16, 1L, null
        );
        EventoViajeDTO eventoExistente = new EventoViajeDTO(
                "uuid-existente", LocalDateTime.now(), TipoEvento.DESCARGA, -31.38, -55.53, 1L, null
        );

        List<SyncResultDTO> resultados = service.guardarEventosLote(List.of(eventoNuevo, eventoExistente));

        assertEquals(2, resultados.size());

        SyncResultDTO resNuevo = resultados.stream().filter(r -> r.uuid().equals("uuid-nuevo")).findFirst().orElseThrow();
        assertEquals("ACCEPTED", resNuevo.estado());

        SyncResultDTO resExistente = resultados.stream().filter(r -> r.uuid().equals("uuid-existente")).findFirst().orElseThrow();
        assertEquals("DUPLICATE", resExistente.estado());

        verify(em, times(1)).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Debe guardar un evento con IncidenteRuta asociado")
    void testGuardarEventoConIncidente() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        IncidenteDTO incDto = new IncidenteDTO("/fotos/pinchadura.jpg", "Rueda pinchada en km 120");
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-incidente",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                -33.5,
                -56.0,
                1L,
                incDto
        );

        EventoViaje resultado = service.guardarEvento(dto);

        assertNotNull(resultado);
        assertNotNull(resultado.getIncidente());
        assertEquals("Rueda pinchada en km 120", resultado.getIncidente().getDescripcion());
        verify(em).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Debe listar eventos ordenados cronológicamente")
    void testListarEventosPorGuia() {
        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventos);
        when(queryEventos.setParameter(eq("guiaId"), eq(1L))).thenReturn(queryEventos);

        EventoViaje ev1 = new EventoViaje("u1", LocalDateTime.now().minusHours(2), TipoEvento.INICIO, -34.9, -56.1, guiaPrueba);
        EventoViaje ev2 = new EventoViaje("u2", LocalDateTime.now().minusHours(1), TipoEvento.CARGA, -34.8, -56.0, guiaPrueba);
        when(queryEventos.getResultList()).thenReturn(List.of(ev1, ev2));

        List<EventoViajeDTO> dtos = service.listarEventosPorGuia(1L);

        assertEquals(2, dtos.size());
        assertEquals("u1", dtos.get(0).uuid());
        assertEquals("u2", dtos.get(1).uuid());
    }
}
