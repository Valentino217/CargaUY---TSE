package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - GuiaDeViajeEJB")
class GuiaDeViajeEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private ControlAcceso control;

    @Mock
    private FiscalizacionProducerLocal fiscalizacionProducer;

    @Mock
    private TypedQuery<GuiaDeViaje> qGuias;

    @Mock
    private TypedQuery<Usuario> qUsuarios;

    private GuiaDeViajeEJB guiaEJB;
    private Empresa emp;
    private Vehiculo vehiculo;
    private Chofer chofer;
    private GuiaDeViaje guia;

    @BeforeEach
    void setUp() throws Exception {
        guiaEJB = new GuiaDeViajeEJB();

        Field emField = GuiaDeViajeEJB.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(guiaEJB, em);

        Field controlField = GuiaDeViajeEJB.class.getDeclaredField("control");
        controlField.setAccessible(true);
        controlField.set(guiaEJB, control);

        Field prodField = GuiaDeViajeEJB.class.getDeclaredField("fiscalizacionProducer");
        prodField.setAccessible(true);
        prodField.set(guiaEJB, fiscalizacionProducer);

        emp = new Empresa(10, "Transportes S.A.", "TransSA", "Ruta 1");
        vehiculo = new Vehiculo("STP 1234", "Volvo", "FH", 12000, 30000, true, emp);
        chofer = new Chofer("Juan Chofer", "juan@test.com", LocalDate.of(1985, 5, 10), 44444444, "{GUB}");
        chofer.setId(20);
        guia = new GuiaDeViaje(LocalDate.now(), "Montevideo", "Salto", "Agro", 15000f, emp, vehiculo, chofer);
        guia.setId(100L);
    }

    @Test
    @DisplayName("listarParaActor como ADMINISTRADOR y como CIUDADANO")
    void testListarParaActor() {
        when(control.exigir("tokenAdmin")).thenReturn(new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)));
        when(em.createQuery(contains("ORDER BY g.fecha DESC"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(guia));

        assertEquals(1, guiaEJB.listarParaActor("tokenAdmin").size());

        when(control.exigir("tokenCiu")).thenReturn(new Acceso(7, 22222222, "Ciudadano", Set.of(TipoUsuario.CIUDADANO)));
        when(em.createQuery(contains("VinculoEmpresa"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.setParameter("id", 7)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(guia));

        assertEquals(1, guiaEJB.listarParaActor("tokenCiu").size());
    }

    @Test
    @DisplayName("listarChoferesPorEmpresa con token y sin token")
    void testListarChoferesPorEmpresa() {
        when(em.createQuery(contains("VinculoEmpresa"), eq(Usuario.class))).thenReturn(qUsuarios);
        when(qUsuarios.setParameter("nroEmpresa", 10)).thenReturn(qUsuarios);
        when(qUsuarios.setParameter("rol", TipoUsuario.CHOFER)).thenReturn(qUsuarios);
        when(qUsuarios.getResultList()).thenReturn(List.of(chofer));

        List<Usuario> res = guiaEJB.listarChoferesPorEmpresa("token", 10);
        assertEquals(1, res.size());
        verify(control).empresa("token", 10, false);
    }

    @Test
    @DisplayName("buscarGuias con token y sin token maneja ID nulo, no encontrado y encontrado")
    void testBuscarGuias() {
        assertNull(guiaEJB.buscarGuias(null));
        assertNull(guiaEJB.buscarGuias("token", null));

        when(em.createQuery(contains("WHERE g.id = :id"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.setParameter("id", 999L)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of());
        assertNull(guiaEJB.buscarGuias("token", 999L));

        when(qGuias.setParameter("id", 100L)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(guia));
        GuiaDeViaje encontrada = guiaEJB.buscarGuias("token", 100L);
        assertNotNull(encontrada);
        assertEquals(100L, encontrada.getId());
        verify(control).empresa("token", 10, false);
    }

    @Test
    @DisplayName("motivoNoHabilitado describe causas de inhabilitación")
    void testMotivoNoHabilitado() {
        LocalDate hoy = LocalDate.now();
        vehiculo.setEstadoHabilitacion(false);
        String m1 = GuiaDeViajeEJB.motivoNoHabilitado(vehiculo);
        assertTrue(m1.contains("suspendido administrativamente"));
        assertTrue(m1.contains("sin ITV vigente"));
        assertTrue(m1.contains("sin PNC vigente"));

        vehiculo.setEstadoHabilitacion(true);
        vehiculo.getItv().add(new ITV(hoy.plusDays(5), vehiculo));
        vehiculo.getPncs().add(new PNC(hoy.plusDays(5), vehiculo));
        assertEquals("", GuiaDeViajeEJB.motivoNoHabilitado(vehiculo, hoy));
    }

    @Test
    @DisplayName("crear con token valida chofer y pertenencia del vehículo a la empresa")
    void testCrearConTokenYValidacionAsignacion() {
        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);
        guiaEJB.crear("token", guia);
        verify(control).empresa("token", 10, true);
        verify(control).choferAsignable("token", 10, 20);
        verify(em).persist(guia);

        // Vehículo de otra empresa debe fallar
        Empresa otraEmp = new Empresa(99, "Otra S.A.", "Otra", "Ruta 5");
        Vehiculo vehiculoAjeno = new Vehiculo("AJN 0001", "Volvo", "FH", 10000, 20000, true, otraEmp);
        when(em.find(Vehiculo.class, "AJN 0001")).thenReturn(null);
        GuiaDeViaje guiaInvalida = new GuiaDeViaje(LocalDate.now(), "A", "B", "Rubro", 100f, emp, vehiculoAjeno, null);

        assertThrows(IllegalArgumentException.class, () -> guiaEJB.crear("token", guiaInvalida));
    }

    @Test
    @DisplayName("modificar con token y sin token valida existencia y estado SIN_INICIAR")
    void testModificarGuia() {
        when(em.createQuery(contains("WHERE g.id = :id"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.setParameter("id", 100L)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> guiaEJB.modificar("token", guia));

        // Sin token: guía inexistente en em.find
        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> guiaEJB.modificar(guia));

        // Guía en estado EN_CURSO no se puede modificar
        GuiaDeViaje enCurso = new GuiaDeViaje(LocalDate.now(), "MVD", "PDU", "Forestal", 1000f, emp, vehiculo, chofer);
        enCurso.setId(100L);
        enCurso.setEstado(TipoEstado.EN_CURSO);
        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(enCurso);
        assertThrows(IllegalStateException.class, () -> guiaEJB.modificar(guia));

        // Modificación exitosa en estado SIN_INICIAR
        GuiaDeViaje existente = new GuiaDeViaje(LocalDate.now(), "MVD", "PDU", "Forestal", 1000f, emp, vehiculo, chofer);
        existente.setId(100L);
        when(qGuias.getResultList()).thenReturn(List.of(existente));
        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(existente);
        when(em.find(Vehiculo.class, "STP 1234")).thenReturn(vehiculo);

        GuiaDeViaje editada = new GuiaDeViaje(LocalDate.now().plusDays(1), "Colonia", "Rivera", "Minería", 25000f, emp, vehiculo, chofer);
        editada.setId(100L);
        editada.setTiposCarga(List.of(new TipoCarga("Granel")));

        guiaEJB.modificar("token", editada);
        assertEquals("Colonia", existente.getOrigen());
        assertEquals("Rivera", existente.getDestino());
        assertEquals("Minería", existente.getRubroCliente());
        assertEquals(25000f, existente.getVolumenCarga());
        assertEquals(1, existente.getTiposCarga().size());
    }

    @Test
    @DisplayName("eliminar con token y sin token valida existencia y estado SIN_INICIAR")
    void testEliminarGuia() {
        when(em.createQuery(contains("WHERE g.id = :id"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.setParameter("id", 100L)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> guiaEJB.eliminar("token", guia));

        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> guiaEJB.eliminar(guia));

        GuiaDeViaje enCurso = new GuiaDeViaje(LocalDate.now(), "A", "B", "C", 100f, emp, vehiculo, chofer);
        enCurso.setId(100L);
        enCurso.setEstado(TipoEstado.EN_CURSO);
        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(enCurso);
        assertThrows(IllegalStateException.class, () -> guiaEJB.eliminar(guia));

        GuiaDeViaje sinIniciar = new GuiaDeViaje(LocalDate.now(), "A", "B", "C", 100f, emp, vehiculo, chofer);
        sinIniciar.setId(100L);
        when(qGuias.getResultList()).thenReturn(List.of(sinIniciar));
        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(sinIniciar);

        guiaEJB.eliminar("token", guia);
        verify(em).remove(sinIniciar);
    }

    @Test
    @DisplayName("avanzarEstadoAdmin transiciona SIN_INICIAR -> EN_CURSO -> FINALIZADO y dispara JMS")
    void testAvanzarEstadoAdmin() {
        assertThrows(IllegalArgumentException.class, () -> guiaEJB.avanzarEstadoAdmin("token", null));

        when(em.find(GuiaDeViaje.class, 999L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> guiaEJB.avanzarEstadoAdmin("token", 999L));

        when(em.find(GuiaDeViaje.class, 100L)).thenReturn(guia);
        assertEquals(TipoEstado.SIN_INICIAR, guia.getEstado());

        guiaEJB.avanzarEstadoAdmin("token", 100L);
        assertEquals(TipoEstado.EN_CURSO, guia.getEstado());
        verify(fiscalizacionProducer).solicitarEvaluacion(100L);

        guiaEJB.avanzarEstadoAdmin("token", 100L);
        assertEquals(TipoEstado.FINALIZADO, guia.getEstado());

        assertThrows(IllegalStateException.class, () -> guiaEJB.avanzarEstadoAdmin("token", 100L));
    }

    @Test
    @DisplayName("listarGuiasPorCiudadano ejecuta consulta filtrada por ciudadano")
    void testListarGuiasPorCiudadano() {
        when(em.createQuery(contains("WHERE c.id = :ciudadanoId"), eq(GuiaDeViaje.class))).thenReturn(qGuias);
        when(qGuias.setParameter("ciudadanoId", 7)).thenReturn(qGuias);
        when(qGuias.getResultList()).thenReturn(List.of(guia));

        assertEquals(1, guiaEJB.listarGuiasPorCiudadano(7).size());
    }
}
