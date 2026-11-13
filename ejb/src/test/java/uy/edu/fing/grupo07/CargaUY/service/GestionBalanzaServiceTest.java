package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - GestionBalanzaService")
class GestionBalanzaServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private BalanzaClient balanzaClient;

    @Mock
    private TypedQuery<GuiaDeViaje> queryGuia;

    @Mock
    private TypedQuery<PesadaBalanza> queryPesada;

    @Mock
    private FiscalizacionProducerLocal fiscalizacionProducer;

    private GestionBalanzaServiceImpl service;
    private GuiaDeViaje guiaPrueba;
    private Vehiculo vehiculoPrueba;

    @BeforeEach
    void setUp() {
        service = new GestionBalanzaServiceImpl(em, balanzaClient, fiscalizacionProducer);

        vehiculoPrueba = new Vehiculo("123456", "Scania", "R450", 9000, 36000, true, null);
        guiaPrueba = new GuiaDeViaje();
        guiaPrueba.setId(10L);
        guiaPrueba.setVehiculo(vehiculoPrueba);
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
    }

    @Test
    @DisplayName("Debe registrar la pesada exitosamente cuando se especifica guiaId")
    void testRegistrarPesadaConGuiaId() {
        when(em.find(GuiaDeViaje.class, 10L)).thenReturn(guiaPrueba);

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(
                null, LocalDate.now(), LocalTime.now(), 32000, null, 10L
        );

        PesadaBalanzaDTO resultado = service.registrarPesada(dto);

        assertNotNull(resultado);
        assertEquals(10L, resultado.guiaId());
        assertEquals(32000, resultado.pesoRegistrado());
        assertEquals("123456", resultado.matricula());
        verify(em).persist(any(PesadaBalanza.class));
        verify(fiscalizacionProducer).solicitarEvaluacion(10L);
    }

    @Test
    @DisplayName("Debe buscar la guía EN_CURSO por matrícula y asociar la pesada")
    void testRegistrarPesadaPorMatricula() {
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("matricula"), eq(123456))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(
                null, LocalDate.now(), LocalTime.now(), 35000, "123456", null
        );

        PesadaBalanzaDTO resultado = service.registrarPesada(dto);

        assertNotNull(resultado);
        assertEquals(10L, resultado.guiaId());
        assertEquals(35000, resultado.pesoRegistrado());
        verify(em).persist(any(PesadaBalanza.class));
    }

    @Test
    @DisplayName("Debe lanzar BusinessException si no existe guía activa para la matrícula")
    void testRegistrarPesadaVehiculoSinGuiaActiva() {
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("matricula"), eq(999999))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(Collections.emptyList());

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(
                null, LocalDate.now(), LocalTime.now(), 30000, "999999", null
        );

        assertThrows(BusinessException.class, () -> service.registrarPesada(dto));
        verify(em, never()).persist(any(PesadaBalanza.class));
    }

    @Test
    @DisplayName("Debe validar campos requeridos (dto nulo, peso inválido, matrícula no numérica)")
    void testRegistrarPesadaValidaciones() {
        assertThrows(BusinessException.class, () -> service.registrarPesada(null));

        PesadaBalanzaDTO pesoCero = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.now(), 0, "123456", 10L);
        assertThrows(BusinessException.class, () -> service.registrarPesada(pesoCero));

        PesadaBalanzaDTO sinGuiaNiMatricula = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.now(), 30000, null, null);
        assertThrows(BusinessException.class, () -> service.registrarPesada(sinGuiaNiMatricula));

        PesadaBalanzaDTO matriculaNoNumerica = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.now(), 30000, "ABC-XYZ", null);
        assertThrows(BusinessException.class, () -> service.registrarPesada(matriculaNoNumerica));
    }

    @Test
    @DisplayName("Debe consumir mock e invocar registrarPesada")
    void testRegistrarPesadaDesdeMock() {
        PesadaBalanzaDTO mockDto = new PesadaBalanzaDTO(
                555, LocalDate.now(), LocalTime.now(), 31000, "123456", null
        );
        when(balanzaClient.consultarPesadaMock()).thenReturn(mockDto);

        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("matricula"), eq(123456))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        PesadaBalanzaDTO resultado = service.registrarPesadaDesdeMock();

        assertNotNull(resultado);
        assertEquals(31000, resultado.pesoRegistrado());
        assertEquals(10L, resultado.guiaId());
        verify(em).persist(any(PesadaBalanza.class));
    }

    @Test
    @DisplayName("Debe listar pesadas por guía ordenadas cronológicamente")
    void testListarPesadasPorGuia() {
        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter(eq("guiaId"), eq(10L))).thenReturn(queryPesada);

        PesadaBalanza p1 = new PesadaBalanza(LocalDate.now(), LocalTime.of(10, 0), 30000, guiaPrueba);
        PesadaBalanza p2 = new PesadaBalanza(LocalDate.now(), LocalTime.of(12, 0), 29500, guiaPrueba);
        when(queryPesada.getResultList()).thenReturn(List.of(p1, p2));

        List<PesadaBalanzaDTO> lista = service.listarPesadasPorGuia(10L);

        assertEquals(2, lista.size());
        assertEquals(30000, lista.get(0).pesoRegistrado());
        assertEquals(29500, lista.get(1).pesoRegistrado());
    }

    @Test
    @DisplayName("Punto E.8 - Si un camión registrado en padrón pasa por balanza sin guía EN_CURSO, persiste pesada sin guía y dispara fiscalización VIAJE_SIN_GUIA")
    void testRegistrarPesadaCamionRegistradoSinGuiaActivaDisparaFiscalizacion() {
        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("matricula"), eq(123456))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.setMaxResults(1)).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(Collections.emptyList());

        when(em.find(Vehiculo.class, "123456")).thenReturn(vehiculoPrueba);

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(
                null, LocalDate.now(), LocalTime.of(14, 30), 33800, "123456", null
        );

        PesadaBalanzaDTO resultado = service.registrarPesada(dto);

        assertNotNull(resultado);
        assertNull(resultado.guiaId());
        assertEquals("123456", resultado.matricula());
        assertEquals(33800, resultado.pesoRegistrado());
        verify(em).persist(argThat((PesadaBalanza p) ->
                p.getGuia() == null && p.getVehiculo() == vehiculoPrueba && p.getPesoRegistrado() == 33800
        ));
        verify(fiscalizacionProducer).solicitarEvaluacionSinGuia(eq("123456"), contains("33800 kg"));
    }

    @Test
    @DisplayName("Punto E.8 - Rechaza pesaje con guiaId en estado SIN_INICIAR o FINALIZADO y deriva el camión a fiscalización VIAJE_SIN_GUIA")
    void testRegistrarPesadaConGuiaNoEnCursoDisparaFiscalizacionSinGuia() {
        guiaPrueba.setEstado(TipoEstado.FINALIZADO);
        when(em.find(GuiaDeViaje.class, 10L)).thenReturn(guiaPrueba);

        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(
                null, LocalDate.now(), LocalTime.now(), 28000, null, 10L
        );

        assertThrows(BusinessException.class, () -> service.registrarPesada(dto));
        verify(fiscalizacionProducer).solicitarEvaluacionSinGuia(eq("123456"), contains("FINALIZADO"));
        verify(em, never()).persist(any(PesadaBalanza.class));
    }

    @Test
    @DisplayName("sincronizarPesadasDeGuia importa solo pesadas nuevas evitando duplicados y encola fiscalización")
    void testSincronizarPesadasDeGuiaIdempotente() {
        Empresa empresa = new Empresa();
        empresa.setNroEmpresa(101);
        guiaPrueba.setEmpresa(empresa);
        LocalDate fechaGuia = LocalDate.of(2026, 11, 10);
        guiaPrueba.setFecha(fechaGuia);

        when(em.find(GuiaDeViaje.class, 10L)).thenReturn(guiaPrueba);
        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter(eq("guiaId"), eq(10L))).thenReturn(queryPesada);

        PesadaBalanza yaExistente = new PesadaBalanza(fechaGuia, LocalTime.of(9, 15, 0), 30000, guiaPrueba);
        when(queryPesada.getResultList()).thenReturn(List.of(yaExistente));

        List<PesadaBalanzaDTO> remotas = List.of(
                new PesadaBalanzaDTO(1, fechaGuia, LocalTime.of(9, 15, 0), 30000, "123456", null),
                new PesadaBalanzaDTO(2, fechaGuia, LocalTime.of(11, 40, 0), 35500, "123456", null)
        );
        when(balanzaClient.consultarPesadasVehiculo(eq("123456"), eq(fechaGuia), any(LocalDate.class), eq(101), eq("corr-1")))
                .thenReturn(remotas);

        List<PesadaBalanzaDTO> importadas = service.sincronizarPesadasDeGuia(10L, "corr-1");

        assertEquals(1, importadas.size());
        assertEquals(35500, importadas.get(0).pesoRegistrado());
        verify(em, times(1)).persist(any(PesadaBalanza.class));
        verify(fiscalizacionProducer, times(1)).solicitarEvaluacion(10L);
    }

    @Test
    @DisplayName("registrarPesadaViaPeriferico registra en el periférico sin persistir en central hasta consultar balanzas")
    void testRegistrarPesadaViaPeriferico() {
        Empresa empresa = new Empresa();
        empresa.setNroEmpresa(101);
        guiaPrueba.setEmpresa(empresa);
        when(em.find(GuiaDeViaje.class, 10L)).thenReturn(guiaPrueba);

        LocalDate hoy = LocalDate.of(2026, 11, 10);
        LocalTime ahora = LocalTime.of(14, 0);
        PesadaBalanzaDTO entrada = new PesadaBalanzaDTO(null, hoy, ahora, 39200, null, 10L);
        PesadaBalanzaDTO confirmadaPeriferico = new PesadaBalanzaDTO(88, hoy, ahora, 39200, "123456", null);

        when(balanzaClient.registrarPesadaEnPeriferico(any(PesadaBalanzaDTO.class), eq(101), eq("Peaje Pando"), eq("corr-2")))
                .thenReturn(confirmadaPeriferico);

        PesadaBalanzaDTO resultado = service.registrarPesadaViaPeriferico(entrada, "Peaje Pando", "corr-2");

        assertNotNull(resultado);
        assertEquals(88, resultado.idPesada());
        assertEquals(10L, resultado.guiaId());
        assertEquals(39200, resultado.pesoRegistrado());
        verify(balanzaClient).registrarPesadaEnPeriferico(argThat(d -> "123456".equals(d.matricula())), eq(101), eq("Peaje Pando"), eq("corr-2"));
        verify(em, never()).persist(any(PesadaBalanza.class));
        verify(fiscalizacionProducer, never()).solicitarEvaluacion(anyLong());
    }

    @Test
    @DisplayName("sondearBalanzasPeriodico omite llamadas cuando todas las balanzas están apagadas por Admin")
    void testSondearBalanzasPeriodicoApagadas() {
        GestionNodosPerifericosServiceImpl registro = new GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-BAL-01", false);
        service.setNodosPerifericosService(registro);

        int importadas = service.sondearBalanzasPeriodico();

        assertEquals(0, importadas);
        verifyNoInteractions(balanzaClient);
    }

    @Test
    @DisplayName("sondearBalanzasPeriodico registra lectura en modo CALIBRADA vs SOBRECARGA, importa y detecta VIAJE_SIN_GUIA")
    void testSondearBalanzasPeriodicoCompleto() {
        GestionNodosPerifericosServiceImpl registro = new GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-BAL-01", true);
        service.setNodosPerifericosService(registro);

        Empresa empresa = new Empresa();
        empresa.setNroEmpresa(101);
        guiaPrueba.setEmpresa(empresa);
        guiaPrueba.setEstado(TipoEstado.EN_CURSO);
        guiaPrueba.setVolumenCarga(30000f);
        LocalDate hoy = LocalDate.now();
        guiaPrueba.setFecha(hoy);

        when(em.createQuery(anyString(), eq(GuiaDeViaje.class))).thenReturn(queryGuia);
        when(queryGuia.setParameter(eq("enCurso"), eq(TipoEstado.EN_CURSO))).thenReturn(queryGuia);
        when(queryGuia.getResultList()).thenReturn(List.of(guiaPrueba));

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter(eq("guiaId"), eq(10L))).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        Vehiculo sinGuiaVehiculo = new Vehiculo("LOR 2024", "Volvo", "FH", 8000, 32000, true, empresa);
        when(em.find(Vehiculo.class, "LOR 2024")).thenReturn(sinGuiaVehiculo);

        PesadaBalanzaDTO lecturaSeed = new PesadaBalanzaDTO(5, hoy, LocalTime.of(8, 0), 35000, "OLD 999", null);
        PesadaBalanzaDTO creadaEnCurso = new PesadaBalanzaDTO(10, hoy, LocalTime.of(12, 0), 39000, "123456", 10L);
        PesadaBalanzaDTO lecturaSinGuia = new PesadaBalanzaDTO(11, hoy, LocalTime.of(12, 5), 42000, "LOR 2024", null);

        when(balanzaClient.consultarPesadasPorFechaEnEndpoint(anyString(), eq(hoy), anyString()))
                .thenReturn(List.of(lecturaSeed), List.of(creadaEnCurso, lecturaSinGuia));
        when(balanzaClient.registrarPesadaEnPerifericoEnEndpoint(
                anyString(), argThat(d -> d.pesoRegistrado() == 39000), eq(101), anyString(), anyString()
        )).thenReturn(creadaEnCurso);
        when(balanzaClient.consultarPesadasVehiculoEnEndpoint(anyString(), eq("123456"), eq(hoy), any(), eq(101), anyString()))
                .thenReturn(List.of(creadaEnCurso));

        int total = service.sondearBalanzasPeriodico();

        assertEquals(2, total);
        verify(fiscalizacionProducer, times(1)).solicitarEvaluacion(10L);
        verify(fiscalizacionProducer, times(1)).solicitarEvaluacionSinGuia(eq("LOR 2024"), contains("#11"));
        assertTrue(registro.yaEmitioAutoPesada(10L, "NODO-BAL-01", uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico.CALIBRADA));
        assertEquals(47500, GestionBalanzaServiceImpl.calcularPesoAutomaticoParaBalanza(
                guiaPrueba, uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico.SOBRECARGA));
    }

    @Test
    @DisplayName("registrarLecturaExternaEnNodo valida estado encendido/categoría y envía al endpoint del nodo")
    void testRegistrarLecturaExternaEnNodo() {
        GestionNodosPerifericosServiceImpl registro = new GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-BAL-01", true);
        service.setNodosPerifericosService(registro);

        assertThrows(BusinessException.class, () -> service.registrarLecturaExternaEnNodo("NODO-BAL-02", "STP 1234", 40000));
        assertThrows(BusinessException.class, () -> service.registrarLecturaExternaEnNodo("NODO-TRK-01", "STP 1234", 40000));
        assertThrows(BusinessException.class, () -> service.registrarLecturaExternaEnNodo("NODO-BAL-01", " ", 40000));
        assertThrows(BusinessException.class, () -> service.registrarLecturaExternaEnNodo("NODO-BAL-01", "STP 1234", 0));

        Vehiculo v = new Vehiculo("STP 1234", "Scania", "R450", 9000, 31000, true, null);
        Empresa emp = new Empresa();
        emp.setNroEmpresa(55);
        v.setEmpresa(emp);
        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(v);

        String peajeBal1 = registro.buscarPorCodigo("NODO-BAL-01").orElseThrow().estacionPeaje();
        PesadaBalanzaDTO resp = new PesadaBalanzaDTO(70, LocalDate.now(), LocalTime.of(15, 0), 44000, "STP 1234", null);
        when(balanzaClient.registrarPesadaEnPerifericoEnEndpoint(anyString(), any(PesadaBalanzaDTO.class), eq(55), eq(peajeBal1), anyString()))
                .thenReturn(resp);

        PesadaBalanzaDTO resultado = service.registrarLecturaExternaEnNodo("NODO-BAL-01", "STP 1234", 44000);
        assertEquals(70, resultado.idPesada());
        assertEquals(44000, resultado.pesoRegistrado());
    }

    @Test
    @DisplayName("Overloads tokenizados de balanza fallan cerrado sin ControlAcceso y delegan verificación de roles")
    void testOverloadsTokenizadosYFailClosed() throws Exception {
        PesadaBalanzaDTO dto = new PesadaBalanzaDTO(null, LocalDate.now(), LocalTime.of(10, 0), 31000, "123456", 10L);

        assertThrows(IllegalStateException.class, () -> service.registrarPesada("tok", dto));
        assertThrows(IllegalStateException.class, () -> service.registrarPesadaDesdeMock("tok"));
        assertThrows(IllegalStateException.class, () -> service.registrarPesadaDesdeMock("tok", 10L));
        assertThrows(IllegalStateException.class, () -> service.sincronizarPesadasDeGuia("tok", 10L, "corr"));
        assertThrows(IllegalStateException.class, () -> service.registrarPesadaViaPeriferico("tok", dto, "Peaje", "corr"));
        assertThrows(IllegalStateException.class, () -> service.listarPesadasPorGuia("tok", 10L));
        assertThrows(IllegalStateException.class, () -> service.registrarLecturaExternaEnNodo("tok", "NODO-BAL-01", "123456", 35000));

        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso controlMock =
                mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        java.lang.reflect.Field field = GestionBalanzaServiceImpl.class.getDeclaredField("control");
        field.setAccessible(true);
        field.set(service, controlMock);

        GestionNodosPerifericosServiceImpl registro = new GestionNodosPerifericosServiceImpl();
        registro.init();
        registro.setHabilitado("NODO-BAL-01", true);
        service.setNodosPerifericosService(registro);

        Empresa emp = new Empresa();
        emp.setNroEmpresa(91);
        guiaPrueba.setEmpresa(emp);
        when(em.find(GuiaDeViaje.class, 10L)).thenReturn(guiaPrueba);

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter(eq("guiaId"), eq(10L))).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        PesadaBalanzaDTO remota = new PesadaBalanzaDTO(55, LocalDate.now(), LocalTime.of(10, 0), 31000, "123456", 10L);
        when(balanzaClient.consultarPesadaMock()).thenReturn(remota);
        when(balanzaClient.registrarPesadaEnPeriferico(any(PesadaBalanzaDTO.class), eq(91), eq("Peaje"), eq("corr")))
                .thenReturn(remota);
        when(balanzaClient.consultarPesadasVehiculoEnEndpoint(anyString(), eq("123456"), any(), any(), eq(91), eq("corr")))
                .thenReturn(List.of(remota));
        when(balanzaClient.registrarPesadaEnPerifericoEnEndpoint(anyString(), any(), any(), any(), anyString()))
                .thenReturn(remota);

        uy.edu.fing.grupo07.CargaUY.security.Acceso accesoAdmin = new uy.edu.fing.grupo07.CargaUY.security.Acceso(
                1, 10000001, "Admin", java.util.Set.of(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)
        );
        when(controlMock.exigir(eq("tok-adm"), any(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario[].class))).thenReturn(accesoAdmin);

        assertNotNull(service.registrarPesada("tok-adm", dto));
        assertNotNull(service.registrarPesadaDesdeMock("tok-adm"));
        assertNotNull(service.registrarPesadaDesdeMock("tok-adm", 10L));
        assertNotNull(service.registrarPesadaViaPeriferico("tok-adm", dto, "Peaje", "corr"));
        vehiculoPrueba.setEmpresa(emp);
        when(em.find(Vehiculo.class, "123456")).thenReturn(vehiculoPrueba);
        assertNotNull(service.registrarLecturaExternaEnNodo("tok-adm", "NODO-BAL-01", "123456", 35000));
        verify(controlMock).admin("tok-adm");

        assertTrue(service.listarPesadasPorGuia("tok-adm", 10L).isEmpty());
        assertFalse(service.sincronizarPesadasDeGuia("tok-adm", 10L, "corr").isEmpty());
        assertTrue(service.listarPesadasPorGuia("tok-emp", 10L).isEmpty());
        verify(controlMock).empresa("tok-emp", 91, false);
    }
}
