package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;
import uy.edu.fing.grupo07.CargaUY.integration.BalanzaClient;

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

    private GestionBalanzaServiceImpl service;
    private GuiaDeViaje guiaPrueba;
    private Vehiculo vehiculoPrueba;

    @BeforeEach
    void setUp() {
        service = new GestionBalanzaServiceImpl(em, balanzaClient);

        vehiculoPrueba = new Vehiculo(123456, "Scania", "R450", 9000, 36000, true, null);
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
}
