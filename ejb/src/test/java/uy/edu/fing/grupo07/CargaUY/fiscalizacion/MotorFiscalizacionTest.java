package uy.edu.fing.grupo07.CargaUY.fiscalizacion;

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
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PesadaBalanza;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEvento;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private TypedQuery<EventoViaje> queryEventoDesvio;

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

        Vehiculo vehiculo = new Vehiculo("1001", "Scania", "R450", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(List.of(itvVigente));
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

        Vehiculo vehiculo = new Vehiculo("1002", "Volvo", "FH", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(List.of(itvVigente));
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

        Vehiculo vehiculo = new Vehiculo("1003", "Mercedes", "Actros", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(List.of(itvVigente));
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

        Vehiculo vehiculo = new Vehiculo("1004", "Scania", "P310", 8000, 20000, true, null);
        ITV itvVencida = new ITV(LocalDate.now().minusDays(10), vehiculo);
        vehiculo.setItv(List.of(itvVencida));
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

        Vehiculo vehiculo = new Vehiculo("1005", "Scania", "R450", 10000, 30000, true, null);
        ITV itvVigente = new ITV(LocalDate.now().plusMonths(6), vehiculo);
        vehiculo.setItv(List.of(itvVigente));
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

    @Test
    @DisplayName("Debe detectar DESVIO_DE_RUTA cuando el desvío calculado por FaaS supera el umbral de tracking")
    void testDesvioDeRutaSuperaToleranciaGeneraInfraccion() {
        Long guiaId = 7L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");

        EventoViaje eventoDesviado = new EventoViaje(
                "uuid-desvio-1",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                -33.2333,
                -54.3833,
                guia
        );
        eventoDesviado.setDistanciaDesvioKm(85.4);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(List.of(eventoDesviado));


        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertEquals(1, infracciones.size());
        assertEquals(TipoInfraccion.DESVIO_DE_RUTA, infracciones.get(0));
        verify(em).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("No debe generar DESVIO_DE_RUTA cuando la distancia de desvío está dentro de la tolerancia")
    void testDesvioDeRutaDentroDeToleranciaNoGeneraInfraccion() {
        Long guiaId = 8L;
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");

        EventoViaje eventoEnRuta = new EventoViaje(
                "uuid-enruta-1",
                LocalDateTime.now(),
                TipoEvento.INCIDENTE,
                -32.9032,
                -55.8576,
                guia
        );
        eventoEnRuta.setDistanciaDesvioKm(1.2);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 5.0);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(List.of(eventoEnRuta));

        List<TipoInfraccion> infracciones = motor.evaluarGuia(guiaId);

        assertTrue(infracciones.isEmpty());
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Punto E.7 / E.c / E.d - Evalúa vigencia de ITV/PNC a la fecha futura de la guía, adjunta auditoría CREAR_INFRACCION y encola notificación")
    void testHabilitacionVigenteHoyPeroVencidaEnFechaFuturaGuiaGeneraInfraccionYNotifica() {
        uy.edu.fing.grupo07.CargaUY.jms.NotificacionProducerLocal notifMock =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.jms.NotificacionProducerLocal.class);
        MotorFiscalizacionImpl motorConNotif = new MotorFiscalizacionImpl(em, notifMock);

        Long guiaId = 90L;
        LocalDate fechaViajeFutura = LocalDate.now().plusDays(20);
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setFecha(fechaViajeFutura);

        Vehiculo vehiculo = new Vehiculo("2020", "Volvo", "FH540", 9000, 30000, true, null);
        // ITV y PNC vigentes HOY, pero vencidos para fechaViajeFutura
        vehiculo.setItv(List.of(new ITV(LocalDate.now().plusDays(5), vehiculo)));
        vehiculo.getPncs().add(new PNC(LocalDate.now().plusDays(10), vehiculo));
        guia.setVehiculo(vehiculo);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 5.0));

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("guiaId", guiaId)).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("tipo", TipoInfraccion.SIN_HABILITACION))
                .thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.getSingleResult()).thenReturn(0L);

        final Infraccion[] capturada = new Infraccion[1];
        org.mockito.Mockito.doAnswer(inv -> {
            Infraccion inf = inv.getArgument(0);
            inf.setIdCaso(501);
            capturada[0] = inf;
            return null;
        }).when(em).persist(any(Infraccion.class));

        List<TipoInfraccion> resultado = motorConNotif.evaluarGuia(guiaId);

        assertEquals(1, resultado.size());
        assertEquals(TipoInfraccion.SIN_HABILITACION, resultado.get(0));
        assertNotNull(capturada[0]);
        assertEquals(1, capturada[0].getRegistrosAuditoria().size());
        assertEquals("CREAR_INFRACCION", capturada[0].getRegistrosAuditoria().get(0).getAccion());
        verify(notifMock).solicitarNotificacion(501);
    }

    @Test
    @DisplayName("Punto E.7 - No genera SIN_HABILITACION si ITV/PNC hoy están vencidos pero estaban vigentes en la fecha histórica de la guía")
    void testHabilitacionVencidaHoyPeroVigenteEnFechaHistoricaGuiaNoGeneraInfraccion() {
        Long guiaId = 91L;
        LocalDate fechaViajePasada = LocalDate.now().minusDays(15);
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setFecha(fechaViajePasada);

        Vehiculo vehiculo = new Vehiculo("2021", "Scania", "R500", 9000, 30000, true, null);
        // Vencieron hace 3 días, pero hace 15 días estaban plenamente vigentes
        vehiculo.setItv(List.of(new ITV(LocalDate.now().minusDays(3), vehiculo)));
        vehiculo.getPncs().add(new PNC(LocalDate.now().minusDays(3), vehiculo));
        guia.setVehiculo(vehiculo);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 5.0));

        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(Collections.emptyList());

        List<TipoInfraccion> resultado = motor.evaluarGuia(guiaId);

        assertTrue(resultado.isEmpty());
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Punto E.8 / E.c / E.d - evaluarVehiculoSinGuia genera VIAJE_SIN_GUIA sin guía asociada, registra auditoría y encola notificación")
    void testEvaluarVehiculoSinGuiaGeneraInfraccionYNotifica() {
        uy.edu.fing.grupo07.CargaUY.jms.NotificacionProducerLocal notifMock =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.jms.NotificacionProducerLocal.class);
        MotorFiscalizacionImpl motorConNotif = new MotorFiscalizacionImpl(em, notifMock);

        Vehiculo camion = new Vehiculo("303030", "Mercedes", "Actros", 9000, 31000, true, null);
        when(em.find(Vehiculo.class, "303030")).thenReturn(camion);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qGuias = org.mockito.Mockito.mock(TypedQuery.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> qExistentes = org.mockito.Mockito.mock(TypedQuery.class);

        when(em.createQuery(org.mockito.ArgumentMatchers.contains("GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter(anyString(), any())).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(0L);

        when(em.createQuery(org.mockito.ArgumentMatchers.contains("Infraccion"), eq(Long.class))).thenReturn(qExistentes);
        when(qExistentes.setParameter(anyString(), any())).thenReturn(qExistentes);
        when(qExistentes.getSingleResult()).thenReturn(0L);

        final Infraccion[] capturada = new Infraccion[1];
        org.mockito.Mockito.doAnswer(inv -> {
            Infraccion inf = inv.getArgument(0);
            inf.setIdCaso(602);
            capturada[0] = inf;
            return null;
        }).when(em).persist(any(Infraccion.class));

        boolean generada = motorConNotif.evaluarVehiculoSinGuia(" 303030 ", "Pesaje balanza Ruta 5 (34200 kg)");

        assertTrue(generada);
        assertNotNull(capturada[0]);
        assertEquals(TipoInfraccion.VIAJE_SIN_GUIA, capturada[0].getTipo());
        org.junit.jupiter.api.Assertions.assertNull(capturada[0].getGuia());
        assertEquals("303030", capturada[0].getVehiculo().getMatricula());
        assertEquals(1, capturada[0].getRegistrosAuditoria().size());
        assertEquals("CREAR_INFRACCION", capturada[0].getRegistrosAuditoria().get(0).getAccion());
        verify(notifMock).solicitarNotificacion(602);
    }

    @Test
    @DisplayName("Punto E.8 - evaluarVehiculoSinGuia valida matrícula vacía, vehículo inexistente, guía EN_CURSO existente y deduplicación diaria")
    void testEvaluarVehiculoSinGuiaCasosBordeYDeduplicacion() {
        assertThrows(BusinessException.class, () -> motor.evaluarVehiculoSinGuia(null, "test"));
        assertThrows(BusinessException.class, () -> motor.evaluarVehiculoSinGuia("   ", "test"));

        // Vehículo no registrado en padrón
        when(em.find(Vehiculo.class, "999000")).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertFalse(motor.evaluarVehiculoSinGuia("999000", "Evento GPS"));

        // Vehículo con guía EN_CURSO activa -> no genera infracción
        Vehiculo camionConGuia = new Vehiculo("111222", "Volvo", "FH", 9000, 30000, true, null);
        when(em.find(Vehiculo.class, "111222")).thenReturn(camionConGuia);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qGuias = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter(anyString(), any())).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(1L);

        org.junit.jupiter.api.Assertions.assertFalse(motor.evaluarVehiculoSinGuia("111222", "Pesaje"));

        // Vehículo sin guía pero ya fiscalizado hoy por VIAJE_SIN_GUIA -> no duplica
        when(qGuias.getSingleResult()).thenReturn(0L);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> qExistentes = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("Infraccion"), eq(Long.class))).thenReturn(qExistentes);
        when(qExistentes.setParameter(anyString(), any())).thenReturn(qExistentes);
        when(qExistentes.getSingleResult()).thenReturn(1L);

        org.junit.jupiter.api.Assertions.assertFalse(motor.evaluarVehiculoSinGuia("111222", "Segundo pesaje mismo día"));
        verify(em, never()).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Punto E.8 - evaluarVehiculoSinGuia resuelve matrícula almacenada con mayúsculas/minúsculas mixtas mediante fallback JPQL")
    void testEvaluarVehiculoSinGuiaResuelveMatriculaMixtaCaseInsensitive() {
        Vehiculo camionMixto = new Vehiculo("Stp 7777", "Scania", "R450", 9500, 30500, true, null);
        jakarta.persistence.Query qVehiculo = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("UPPER(TRIM(v.matricula))"))).thenReturn(qVehiculo);
        when(qVehiculo.setParameter(eq("mat"), eq("STP 7777"))).thenReturn(qVehiculo);
        when(qVehiculo.setMaxResults(1)).thenReturn(qVehiculo);
        when(qVehiculo.getResultList()).thenReturn(List.of(camionMixto));

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qGuias = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("GuiaDeViaje"), eq(Long.class))).thenReturn(qGuias);
        when(qGuias.setParameter(anyString(), any())).thenReturn(qGuias);
        when(qGuias.getSingleResult()).thenReturn(0L);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> qExistentes = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(org.mockito.ArgumentMatchers.contains("Infraccion"), eq(Long.class))).thenReturn(qExistentes);
        when(qExistentes.setParameter(anyString(), any())).thenReturn(qExistentes);
        when(qExistentes.getSingleResult()).thenReturn(0L);

        assertTrue(motor.evaluarVehiculoSinGuia("stp 7777", "Pesaje balanza"));
        verify(em).persist(org.mockito.ArgumentMatchers.argThat((Infraccion i) ->
                i.getTipo() == TipoInfraccion.VIAJE_SIN_GUIA
                        && i.getVehiculo() == camionMixto
        ));
    }

    @SuppressWarnings("unchecked")
    private List<TipoInfraccion> evaluarSerieDeDesvios(Long guiaId, List<String> evidenciasPrevias, double... desvios) {
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setOrigen("Montevideo");
        guia.setDestino("Rivera");
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        List<EventoViaje> eventos = new java.util.ArrayList<>();
        for (int i = 0; i < desvios.length; i++) {
            EventoViaje ev = new EventoViaje("uuid-" + (char) ('a' + i), base.plusMinutes(i),
                    TipoEvento.POSICION, -33.0, -55.0, guia);
            ev.setDistanciaDesvioKm(desvios[i]);
            eventos.add(ev);
        }
        guia.setEventos(eventos);

        TypedQuery<String> qEvidencias = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 5.0));
        when(em.createQuery(anyString(), eq(String.class))).thenReturn(qEvidencias);
        when(qEvidencias.setParameter(anyString(), any())).thenReturn(qEvidencias);
        when(qEvidencias.getResultList()).thenReturn(evidenciasPrevias);
        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(Collections.emptyList());

        return motor.evaluarGuia(guiaId);
    }

    @Test
    @DisplayName("Desvío por episodios - posiciones consecutivas en desvío generan una sola infracción")
    void testPosicionesConsecutivasEnDesvioGeneranUnaSolaInfraccion() {
        List<TipoInfraccion> res = evaluarSerieDeDesvios(200L, new java.util.ArrayList<>(), 1.0, 8.0, 12.0, 7.5);

        assertEquals(List.of(TipoInfraccion.DESVIO_DE_RUTA), res);
        verify(em, org.mockito.Mockito.times(1)).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Desvío por episodios - al volver a la ruta y desviarse nuevamente se crea una nueva infracción")
    void testReingresoARutaYNuevoDesvioGeneraNuevaInfraccion() {
        List<TipoInfraccion> res = evaluarSerieDeDesvios(201L, new java.util.ArrayList<>(), 8.0, 12.0, 1.5, 9.0, 10.0);

        assertEquals(List.of(TipoInfraccion.DESVIO_DE_RUTA, TipoInfraccion.DESVIO_DE_RUTA), res);
        verify(em, org.mockito.Mockito.times(2)).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Desvío por episodios - re-evaluar no duplica episodios ya registrados")
    void testReevaluacionNoDuplicaEpisodiosRegistrados() {
        List<String> previas = new java.util.ArrayList<>(List.of("El evento de tracking UUID uuid-a registró un desvío"));
        List<TipoInfraccion> res = evaluarSerieDeDesvios(202L, previas, 8.0, 1.0, 9.0);

        assertEquals(List.of(TipoInfraccion.DESVIO_DE_RUTA), res);
        verify(em, org.mockito.Mockito.times(1)).persist(any(Infraccion.class));
    }

    @Test
    @DisplayName("Habilitación del vehículo inicializa colecciones perezosas ITV/PNC y genera SIN_HABILITACION cuando vencen")
    void testEvaluarHabilitacionInicializaColeccionesPerezosasYGeneraSinHabilitacion() {
        Long guiaId = 203L;
        LocalDate fechaViaje = LocalDate.of(2026, 11, 13);
        GuiaDeViaje guia = new GuiaDeViaje();
        guia.setId(guiaId);
        guia.setFecha(fechaViaje);

        Vehiculo vehiculo = new Vehiculo("STP-LAZY", "Scania", "R450", 9000, 28000, true, null);
        ITV itvVencido = new ITV(fechaViaje.minusDays(5), vehiculo);
        PNC pncVencido = new PNC(fechaViaje.minusDays(2), vehiculo);
        List<ITV> itvSpy = org.mockito.Mockito.spy(new java.util.ArrayList<>(List.of(itvVencido)));
        List<PNC> pncSpy = org.mockito.Mockito.spy(new java.util.ArrayList<>(List.of(pncVencido)));
        vehiculo.setItv(itvSpy);
        vehiculo.setPncs(pncSpy);
        guia.setVehiculo(vehiculo);

        when(em.find(GuiaDeViaje.class, guiaId)).thenReturn(guia);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(new ReglaFiscalizacion(500, 5.0));
        when(em.createQuery(anyString(), eq(PesadaBalanza.class))).thenReturn(queryPesada);
        when(queryPesada.setParameter("guiaId", guiaId)).thenReturn(queryPesada);
        when(queryPesada.setMaxResults(1)).thenReturn(queryPesada);
        when(queryPesada.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("guiaId", guiaId)).thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.setParameter("tipo", TipoInfraccion.SIN_HABILITACION))
                .thenReturn(queryInfraccionesExistentes);
        when(queryInfraccionesExistentes.getSingleResult()).thenReturn(0L);

        when(em.createQuery(anyString(), eq(EventoViaje.class))).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.setParameter("guiaId", guiaId)).thenReturn(queryEventoDesvio);
        when(queryEventoDesvio.getResultList()).thenReturn(Collections.emptyList());

        List<TipoInfraccion> res = motor.evaluarGuia(guiaId);

        assertEquals(List.of(TipoInfraccion.SIN_HABILITACION), res);
        verify(itvSpy).size();
        verify(pncSpy).size();
        verify(em).persist(org.mockito.ArgumentMatchers.argThat((Infraccion inf) ->
                inf.getTipo() == TipoInfraccion.SIN_HABILITACION
                        && inf.getEvidencia() != null
                        && inf.getEvidencia().contains("ITV")
                        && inf.getEvidencia().contains("PNC")
        ));
    }
}
