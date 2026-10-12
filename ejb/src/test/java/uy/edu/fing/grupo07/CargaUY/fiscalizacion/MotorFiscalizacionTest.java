package uy.edu.fing.grupo07.CargaUY.fiscalizacion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - MotorFiscalizacion (CU-C3)")
class MotorFiscalizacionTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<PesadaBalanza> queryPesada;

    @Mock
    private TypedQuery<Long> queryInfraccionesExistentes;

    private MotorFiscalizacionImpl motor;

    @BeforeEach
    void setUp() {
        motor = new MotorFiscalizacionImpl(em);
    }

    @Test
    @DisplayName("Debe lanzar BusinessException cuando el identificador de la guía es nulo")
    void testEvaluarGuiaConIdNuloLanzaExcepcion() {
        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> motor.evaluarGuia(null)
        );

        assertNotNull(excepcion.getMessage());
        assertTrue(excepcion.getMessage().contains("obligatorio"));
    }

    @Test
    @DisplayName("Debe retornar lista vacía y no fallar cuando la guía no existe en la base de datos")
    void testEvaluarGuiaInexistenteRetornaVacio() {
        when(em.find(GuiaDeViaje.class, 999L)).thenReturn(null);

        List<TipoInfraccion> resultado = motor.evaluarGuia(999L);

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Debe detectar EXCESO_DE_CARGA cuando el peso excede la capacidad más la tolerancia")
    void testExcesoDeCargaSuperaToleranciaGeneraInfraccion() {
        Long guiaId = 1L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        Vehiculo vehiculo = new Vehiculo(1001, "Scania", "R450", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(itvVigente);
        PNC pncVigente = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.getPncs().add(pncVigente);
        guia.setVehiculo(vehiculo);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 3);
        PesadaBalanza pesada = new PesadaBalanza(LocalDate.now(), LocalTime.now(), 41000, guia);
        pesada.setIdPesada(10);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(List.of(pesada));

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("guiaId", guiaId)).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("tipo", TipoInfraccion.EXCESO_DE_CARGA))
                .thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.getSingleResult()).thenReturn(0L);

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertEquals(1, infracciones.size());
        assertEquals(TipoInfraccion.EXCESO_DE_CARGA, infracciones.get(0));
        verify(em).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("No debe generar EXCESO_DE_CARGA cuando el peso está dentro de la tolerancia permitida")
    void testExcesoDeCargaDentroDeToleranciaNoGeneraInfraccion() {
        Long guiaId = 2L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        Vehiculo vehiculo = new Vehiculo(1002, "Volvo", "FH", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(itvVigente);
        PNC pncVigente = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.getPncs().add(pncVigente);
        guia.setVehiculo(vehiculo);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 3);
        PesadaBalanza pesada = new PesadaBalanza(LocalDate.now(), LocalTime.now(), 40400, guia);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(List.of(pesada));

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertTrue(infracciones.isEmpty());
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("No debe generar infracciones por carga si el viaje aún no registra pesadas")
    void testViajeSinPesadasNoGeneraInfraccionDeCarga() {
        Long guiaId = 3L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        Vehiculo vehiculo = new Vehiculo(1003, "Mercedes", "Actros", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(itvVigente);
        PNC pncVigente = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.getPncs().add(pncVigente);
        guia.setVehiculo(vehiculo);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 3));

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertTrue(infracciones.isEmpty());
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Debe detectar SIN_HABILITACION cuando el vehículo tiene PNC o ITV vencidos")
    void testVehiculoSinHabilitacionVigenteGeneraInfraccion() {
        Long guiaId = 4L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        Vehiculo vehiculo = new Vehiculo(1004, "Scania", "P310", 8000, 20000, true, null);
        ITV itvVencida = new ITV(LocalDate.now().minusDays(10), vehiculo);
        vehiculo.setItv(itvVencida);
        guia.setVehiculo(vehiculo);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 3));

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("guiaId", guiaId)).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("tipo", TipoInfraccion.SIN_HABILITACION))
                .thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.getSingleResult()).thenReturn(0L);

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertEquals(1, infracciones.size());
        assertEquals(TipoInfraccion.SIN_HABILITACION, infracciones.get(0));
        verify(em).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("No debe duplicar una infracción si ya existe previamente para la misma guía")
    void testInfraccionExistenteNoSeDuplica() {
        Long guiaId = 5L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        Vehiculo vehiculo = new Vehiculo(1005, "Scania", "R450", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(itvVigente);
        PNC pncVigente = new PNC(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.getPncs().add(pncVigente);
        guia.setVehiculo(vehiculo);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 3);
        PesadaBalanza pesada = new PesadaBalanza(LocalDate.now(), LocalTime.now(), 45000, guia);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(List.of(pesada));

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("guiaId", guiaId)).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("tipo", TipoInfraccion.EXCESO_DE_CARGA))
                .thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.getSingleResult()).thenReturn(1L);

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertTrue(infracciones.isEmpty());
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Debe utilizar valores por defecto si no existe registro de ReglaFiscalizacion en base de datos")
    void testReglasPorDefectoCuandoNoHayRegistro() {
        Long guiaId = 6L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(null);

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertNotNull(infracciones);
        assertTrue(infracciones.isEmpty());
    }
}
