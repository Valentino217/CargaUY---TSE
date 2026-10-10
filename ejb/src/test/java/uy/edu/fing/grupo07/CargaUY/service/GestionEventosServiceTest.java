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

import uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.GuiaResumenDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ReportarIncidenteDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - GestionEventosService (Deduplicación e Idempotencia)")
class GestionEventosServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Long> queryCount;

    @Mock
    private TypedQuery<EventoViaje> queryEventos;

    @Mock
    private TypedQuery<GuiaDeViaje> queryGuia;

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

    @Test
    @DisplayName("Debe obtener la guía asignada por choferId exitosamente")
    void testObtenerGuiaAsignadaPorChoferId() {
        Vehiculo vehiculo = new Vehiculo(1234, "Volvo", "FH540", 8000, 30000, true, null);
        Empresa empresa = new Empresa(101, "Transportes SA", "Trans SA", "Av Italia 1234");
        Chofer chofer = new Chofer("Juan Chofer", "juan@mail.com", LocalDate.of(1990, 1, 1), 12345678, "pass");
        chofer.setId(7);

        guiaPrueba.setVehiculo(vehiculo);
        guiaPrueba.setEmpresa(empresa);
        guiaPrueba.setChofer(chofer);

        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("sinIniciar"), eq(TipoEstado.SIN_INICIAR))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("choferId"), eq(7))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        GuiaResumenDTO resumen = service.obtenerGuiaAsignadaChofer(7, null);

        assertNotNull(resumen);
        assertEquals(1L, resumen.id());
        assertEquals("Montevideo", resumen.origen());
        assertEquals(1234, resumen.vehiculoMatricula());
        assertEquals("Volvo FH540", resumen.vehiculoMarcaModelo());
        assertEquals(101, resumen.nroEmpresa());
        assertEquals("Transportes SA", resumen.empresaRazonSocial());
        assertEquals(7, resumen.choferId());
        assertEquals("Juan Chofer", resumen.choferNombre());
    }

    @Test
    @DisplayName("Debe obtener la guía asignada por CI del chofer exitosamente")
    void testObtenerGuiaAsignadaPorCi() {
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("sinIniciar"), eq(TipoEstado.SIN_INICIAR))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("ci"), eq(12345678))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        GuiaResumenDTO resumen = service.obtenerGuiaAsignadaChofer(null, 12345678);

        assertNotNull(resumen);
        assertEquals(1L, resumen.id());
    }

    @Test
    @DisplayName("Debe retornar null cuando el chofer no tiene guías activas")
    void testObtenerGuiaAsignadaNoEncontrada() {
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("sinIniciar"), eq(TipoEstado.SIN_INICIAR))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("choferId"), eq(99))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(Collections.emptyList());

        GuiaResumenDTO resumen = service.obtenerGuiaAsignadaChofer(99, null);

        assertNull(resumen);
    }

    @Test
    @DisplayName("Debe lanzar BusinessException si no se pasa ni choferId ni ci")
    void testObtenerGuiaAsignadaParametrosNulos() {
        assertThrows(BusinessException.class, () -> service.obtenerGuiaAsignadaChofer(null, null));
    }

    @Test
    @DisplayName("Debe reportar un incidente creando el EventoViaje y el IncidenteRuta")
    void testReportarIncidenteExitoso() {
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        ReportarIncidenteDTO dto = new ReportarIncidenteDTO(
                "uuid-inc-1",
                1L,
                LocalDateTime.now(),
                -32.5,
                -55.8,
                "/fotos/desvio.png",
                "Corte de ruta en km 250"
        );

        EventoViajeDTO resultado = service.reportarIncidente(dto);

        assertNotNull(resultado);
        assertEquals("uuid-inc-1", resultado.uuid());
        assertEquals(TipoEvento.INCIDENTE, resultado.tipo());
        assertEquals(1L, resultado.guiaId());
        assertNotNull(resultado.incidente());
        assertEquals("Corte de ruta en km 250", resultado.incidente().descripcion());
        assertEquals("/fotos/desvio.png", resultado.incidente().foto());
        verify(em).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Debe validar campos obligatorios al reportar incidente")
    void testReportarIncidenteValidaciones() {
        assertThrows(BusinessException.class, () -> service.reportarIncidente(null));

        ReportarIncidenteDTO sinGuia = new ReportarIncidenteDTO(
                "u-1", null, LocalDateTime.now(), -34.0, -56.0, null, "Choque"
        );
        assertThrows(BusinessException.class, () -> service.reportarIncidente(sinGuia));

        ReportarIncidenteDTO sinDescripcion = new ReportarIncidenteDTO(
                "u-2", 1L, LocalDateTime.now(), -34.0, -56.0, null, "  "
        );
        assertThrows(BusinessException.class, () -> service.reportarIncidente(sinDescripcion));
    }
}
