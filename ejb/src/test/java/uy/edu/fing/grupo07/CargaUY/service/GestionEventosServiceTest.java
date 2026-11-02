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
        Vehiculo vehiculo = new Vehiculo("1234", "Volvo", "FH540", 8000, 30000, true, null);
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
        assertEquals("1234", resumen.vehiculoMatricula());
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

    @Test
    @DisplayName("guardarEvento con tipo INICIO transiciona la guía a EN_CURSO")
    void testGuardarEventoInicioCambiaEstadoGuiaAEnCurso() {
        guiaPrueba.setEstado(TipoEstado.SIN_INICIAR);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dto = new EventoViajeDTO("uuid-ini", LocalDateTime.now(), TipoEvento.INICIO, -34.9, -56.1, 1L, null);
        service.guardarEvento(dto);

        assertEquals(TipoEstado.EN_CURSO, guiaPrueba.getEstado());
    }

    @Test
    @DisplayName("guardarEvento con tipo FIN transiciona la guía a FINALIZADO")
    void testGuardarEventoFinCambiaEstadoGuiaAFinalizado() {
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dto = new EventoViajeDTO("uuid-fin", LocalDateTime.now(), TipoEvento.FIN, -31.3, -55.5, 1L, null);
        service.guardarEvento(dto);

        assertEquals(TipoEstado.FINALIZADO, guiaPrueba.getEstado());
    }

    @Test
    @DisplayName("listarGuiasChofer retorna lista de GuiaResumenDTO y valida parámetros")
    void testListarGuiasChofer() {
        assertThrows(BusinessException.class, () -> service.listarGuiasChofer(null, null, null));

        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("choferId"), eq(10))).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        List<GuiaResumenDTO> list = service.listarGuiasChofer(10, null, null);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).id());
    }

    @Test
    @DisplayName("Punto C.3 / E.8 - Rechaza eventos distintos de INICIO en guía SIN_INICIAR y deriva vehículo a fiscalización SIN_GUIA")
    void testRechazaEventoNoInicioEnGuiaSinIniciarYDerivaFiscalizacion() {
        uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal producerMock =
                mock(uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal.class);
        GestionEventosServiceImpl svcConFiscalizacion = new GestionEventosServiceImpl(em, null, producerMock);

        Vehiculo vehiculo = new Vehiculo("888999", "Volvo", "FH", 8000, 30000, true, null);
        guiaPrueba.setVehiculo(vehiculo);
        guiaPrueba.setEstado(TipoEstado.SIN_INICIAR);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dtoCarga = new EventoViajeDTO(
                "uuid-invalido-sin-iniciar",
                LocalDateTime.now(),
                TipoEvento.CARGA_PARCIAL,
                -34.9,
                -56.1,
                1L,
                null,
                null,
                5000.0,
                "Soja",
                "AgroSur",
                null
        );

        assertThrows(BusinessException.class, () -> svcConFiscalizacion.guardarEvento(dtoCarga));
        assertEquals(TipoEstado.SIN_INICIAR, guiaPrueba.getEstado());
        verify(em, never()).persist(any(EventoViaje.class));
        verify(producerMock).solicitarEvaluacionSinGuia(eq("888999"), contains("SIN_INICIAR"));
    }

    @Test
    @DisplayName("Punto C.3 - Rechaza segundo evento INICIO cuando la guía ya está EN_CURSO")
    void testRechazaSegundoInicioEnGuiaEnCurso() {
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dtoInicioDuplicado = new EventoViajeDTO(
                "uuid-segundo-inicio",
                LocalDateTime.now(),
                TipoEvento.INICIO,
                -34.9,
                -56.1,
                1L,
                null
        );

        assertThrows(BusinessException.class, () -> service.guardarEvento(dtoInicioDuplicado));
        verify(em, never()).persist(any(EventoViaje.class));
    }

    @Test
    @DisplayName("Punto C.3 / E.8 - Rechaza cualquier evento sobre guía FINALIZADO y deriva vehículo a fiscalización SIN_GUIA")
    void testRechazaEventosEnGuiaFinalizadaYDerivaFiscalizacion() {
        uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal producerMock =
                mock(uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal.class);
        GestionEventosServiceImpl svcConFiscalizacion = new GestionEventosServiceImpl(em, null, producerMock);

        Vehiculo vehiculo = new Vehiculo("777666", "Scania", "G410", 8500, 29000, true, null);
        guiaPrueba.setVehiculo(vehiculo);
        guiaPrueba.setEstado(TipoEstado.FINALIZADO);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dtoPostFin = new EventoViajeDTO(
                "uuid-post-fin",
                LocalDateTime.now(),
                TipoEvento.DESCARGA_PARCIAL,
                -31.3,
                -55.5,
                1L,
                null,
                null,
                1200.0,
                "Madera",
                "Forestal Norte",
                null
        );

        assertThrows(BusinessException.class, () -> svcConFiscalizacion.guardarEvento(dtoPostFin));
        assertEquals(TipoEstado.FINALIZADO, guiaPrueba.getEstado());
        verify(em, never()).persist(any(EventoViaje.class));
        verify(producerMock).solicitarEvaluacionSinGuia(eq("777666"), contains("FINALIZADA"));
    }

    @Test
    @DisplayName("Punto C.3 - Persiste y mapea campos estructurados de carga parcial (cantidadKg, mercaderia, empresaCarga) y rechaza kg negativos")
    void testPersistenciaCamposCargaParcialYValidacionNegativos() {
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
        Vehiculo vehiculo = new Vehiculo("555444", "Mercedes", "Actros", 9000, 31000, true, null);
        guiaPrueba.setVehiculo(vehiculo);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dtoNegativo = new EventoViajeDTO(
                "uuid-kg-neg",
                LocalDateTime.now(),
                TipoEvento.CARGA_PARCIAL,
                -34.9,
                -56.1,
                1L,
                null,
                null,
                -50.0,
                "Trigo",
                "Molino Sur",
                null
        );
        assertThrows(BusinessException.class, () -> service.guardarEvento(dtoNegativo));

        EventoViajeDTO dtoCargaParcial = new EventoViajeDTO(
                "uuid-carga-parcial-ok",
                LocalDateTime.now(),
                TipoEvento.CARGA_PARCIAL,
                -33.5,
                -56.2,
                1L,
                null,
                null,
                12500.5,
                "  Celulosa  ",
                "  UPM Paso de los Toros  ",
                null
        );

        EventoViaje guardado = service.guardarEvento(dtoCargaParcial);
        assertEquals(12500.5, guardado.getCantidadKg());
        assertEquals("Celulosa", guardado.getMercaderia());
        assertEquals("UPM Paso de los Toros", guardado.getEmpresaCarga());

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventos);
        when(queryEventos.setParameter(eq("guiaId"), eq(1L))).thenReturn(queryEventos);
        when(queryEventos.getResultList()).thenReturn(List.of(guardado));

        List<EventoViajeDTO> listados = service.listarEventosPorGuia(1L);
        assertEquals(1, listados.size());
        assertEquals(12500.5, listados.get(0).cantidadKg());
        assertEquals("Celulosa", listados.get(0).mercaderia());
        assertEquals("UPM Paso de los Toros", listados.get(0).empresaCarga());
        assertEquals("555444", listados.get(0).matricula());
    }

    @Test
    @DisplayName("Punto C.3 - Persiste incidente en ruta con fotografía codificada en data URI Base64")
    void testGuardarIncidenteConFotoBase64() {
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        String dataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/";
        EventoViajeDTO dto = new EventoViajeDTO(
                "uuid-inc-b64",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                -32.8,
                -56.4,
                1L,
                new IncidenteDTO(dataUri, "Desperfecto hidráulico con evidencia fotográfica")
        );

        EventoViaje guardado = service.guardarEvento(dto);
        assertNotNull(guardado.getIncidente());
        assertEquals(dataUri, guardado.getIncidente().getFoto());
        assertEquals("Desperfecto hidráulico con evidencia fotográfica", guardado.getIncidente().getDescripcion());
    }

    @Test
    @DisplayName("Punto E.7 - Permite iniciar viaje con vehículo sin ITV/PNC vigente y encola evaluación automática en FiscalizacionQueue")
    void testIniciarViajeVehiculoSinHabilitacionDisparaFiscalizacion() {
        uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal producerMock =
                mock(uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal.class);
        GestionEventosServiceImpl svcConFiscalizacion = new GestionEventosServiceImpl(em, null, producerMock);

        Vehiculo vehiculoSinItv = new Vehiculo("333222", "Scania", "P310", 8000, 22000, false, null);
        guiaPrueba.setVehiculo(vehiculoSinItv);
        guiaPrueba.setEstado(TipoEstado.SIN_INICIAR);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);
        when(em.find(GuiaDeViaje.class, 1L)).thenReturn(guiaPrueba);

        EventoViajeDTO dtoInicio = new EventoViajeDTO(
                "uuid-ini-sin-hab",
                LocalDateTime.now(),
                TipoEvento.INICIO,
                -34.9,
                -56.1,
                1L,
                null
        );

        EventoViaje ev = svcConFiscalizacion.guardarEvento(dtoInicio);
        assertNotNull(ev);
        assertEquals(TipoEstado.EN_CURSO, guiaPrueba.getEstado());
        verify(producerMock).solicitarEvaluacion(1L);
    }

    @Test
    @DisplayName("Punto E.8 - Evento recibido por matrícula para camión registrado sin guía EN_CURSO dispara fiscalización VIAJE_SIN_GUIA")
    void testEventoPorMatriculaSinGuiaActivaDisparaFiscalizacionSinGuia() {
        uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal producerMock =
                mock(uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal.class);
        GestionEventosServiceImpl svcConFiscalizacion = new GestionEventosServiceImpl(em, null, producerMock);

        Vehiculo camionPadron = new Vehiculo("444555", "Volvo", "FMX", 9000, 30000, true, null);

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryCount);
        when(queryCount.setParameter(eq("uuid"), anyString())).thenReturn(queryCount);
        when(queryCount.getSingleResult()).thenReturn(0L);

        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("matricula"), eq("444555"))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(Collections.emptyList());

        when(em.find(Vehiculo.class, "444555")).thenReturn(camionPadron);

        EventoViajeDTO dtoSinGuia = new EventoViajeDTO(
                "uuid-track-sin-guia",
                LocalDateTime.now(),
                TipoEvento.CARGA_PARCIAL,
                -34.5,
                -56.2,
                null,
                null,
                2.0,
                8000.0,
                "Arroz",
                "Saman",
                "444555"
        );

        assertThrows(BusinessException.class, () -> svcConFiscalizacion.guardarEvento(dtoSinGuia));
        verify(producerMock).solicitarEvaluacionSinGuia(eq("444555"), contains("uuid-track-sin-guia"));
        verify(em, never()).persist(any(EventoViaje.class));
    }
}
