package uy.edu.fing.grupo07.CargaUY.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.nosql.PosicionNoSqlRepositoryLocal;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionNodosPerifericosServiceImpl;
import uy.edu.fing.grupo07.CargaUY.service.TrackingMockServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJBLocal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - AdminNodosBean")
class AdminNodosBeanTest {

    private static final String TOKEN_ADMIN = "tok-admin-nodos";

    @Mock
    private GestionBalanzaServiceLocal balanzaService;

    @Mock
    private TrackingMockServiceLocal trackingMockService;

    @Mock
    private VehiculoEJBLocal vehiculoEJB;

    @Mock
    private PosicionNoSqlRepositoryLocal posicionNoSqlRepository;

    @Mock
    private ControlAcceso controlAcceso;

    private MockedStatic<SesionActual> sesionActualMock;
    private GestionNodosPerifericosServiceImpl registroReal;
    private AdminNodosBean bean;

    @BeforeEach
    void setUp() {
        sesionActualMock = mockStatic(SesionActual.class);
        sesionActualMock.when(SesionActual::token).thenReturn(TOKEN_ADMIN);
        lenient().when(controlAcceso.exigir(TOKEN_ADMIN, TipoUsuario.ADMINISTRADOR))
                .thenReturn(new Acceso(1, 11111111, "Admin Test", Set.of(TipoUsuario.ADMINISTRADOR)));

        registroReal = new GestionNodosPerifericosServiceImpl(null, controlAcceso);
        registroReal.init();
        lenient().when(posicionNoSqlRepository.obtenerDescriptorEndpoint()).thenReturn("mongodb://localhost:27017/test");
        lenient().when(posicionNoSqlRepository.estaOperativo()).thenReturn(false);

        bean = new AdminNodosBean();
        bean.setBalanzaService(balanzaService);
        bean.setNodosPerifericosService(registroReal);
        bean.setTrackingMockService(trackingMockService);
        bean.setVehiculoEJB(vehiculoEJB);
        bean.setPosicionNoSqlRepository(posicionNoSqlRepository);
    }

    @AfterEach
    void tearDown() {
        if (sesionActualMock != null) {
            sesionActualMock.close();
        }
    }

    @Test
    @DisplayName("cargarEsqueletoNodos muestra nodos configurados (CONSULTANDO vs APAGADO) e infraestructura")
    void testCargarEsqueletoNodos() {
        bean.cargarEsqueletoNodos();
        List<AdminNodosBean.NodoPerifericoVista> nodos = bean.getNodos();

        assertEquals(7, nodos.size());

        AdminNodosBean.NodoPerifericoVista bal1 = nodos.stream()
                .filter(n -> "NODO-BAL-01".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("Balanza #1", bal1.nombre());
        assertEquals("", bal1.estacionPeaje());
        assertTrue(bal1.isAdministrable());
        assertFalse(bal1.isHabilitado());
        assertFalse(bal1.isConsultando());
        assertTrue(bal1.isApagadoAdmin());
        assertEquals("APAGADO", bal1.estado());

        AdminNodosBean.NodoPerifericoVista bal2 = nodos.stream()
                .filter(n -> "NODO-BAL-02".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("Balanza #2", bal2.nombre());
        assertEquals("", bal2.estacionPeaje());
        assertTrue(bal2.isAdministrable());
        assertFalse(bal2.isHabilitado());
        assertTrue(bal2.isApagadoAdmin());
        assertFalse(bal2.isConsultando());
        assertEquals("APAGADO", bal2.estado());

        AdminNodosBean.NodoPerifericoVista trk = nodos.stream()
                .filter(n -> "NODO-TRK-01".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("Tracking GPS", trk.nombre());

        AdminNodosBean.NodoPerifericoVista pdi = nodos.stream()
                .filter(n -> "NODO-PDI-01".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("PDI (DNIC)", pdi.nombre());

        AdminNodosBean.NodoPerifericoVista ldap = nodos.stream()
                .filter(n -> "NODO-LDAP-01".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("OpenLDAP", ldap.nombre());

        AdminNodosBean.NodoPerifericoVista mongo = nodos.stream()
                .filter(n -> "NODO-MONGO-01".equals(n.codigo()))
                .findFirst().orElseThrow();
        assertEquals("MongoDB", mongo.nombre());
    }

    @Test
    @DisplayName("toggleHabilitado y cambiarModoNodo actualizan el registro y notifican al coordinador")
    void testToggleHabilitadoYCambiarModo() {
        bean.toggleHabilitado("NODO-BAL-02");
        assertTrue(registroReal.isNodoHabilitado("NODO-BAL-02"));

        bean.cambiarModoNodo("NODO-TRK-01", ModoNodoPeriferico.CON_DESVIO.name());
        assertEquals(ModoNodoPeriferico.CON_DESVIO, registroReal.getModoTrackingActivo());

        verify(trackingMockService, times(1)).notificarCambioBalanza(TOKEN_ADMIN);
        verify(trackingMockService, times(1)).notificarCambioConfiguracionNodos(TOKEN_ADMIN);
    }

    @Test
    @DisplayName("agregarNuevaBalanza y eliminarBalanza gestionan balanzas personalizadas y sugieren código libre")
    void testAgregarYEliminarBalanza() {
        bean.abrirDialogoNuevaBalanza();
        assertEquals("NODO-BAL-03", bean.getNuevaBalanzaCodigo());

        bean.setNuevaBalanzaNombre("Balanza Ruta 5");
        bean.setNuevaBalanzaPeaje("Peaje Canelones");
        bean.setNuevaBalanzaEndpoint("http://127.0.0.1:18082");
        bean.setNuevaBalanzaModo(ModoNodoPeriferico.SOBRECARGA.name());
        bean.setNuevaBalanzaHabilitada(true);

        bean.agregarNuevaBalanza();

        NodoPerifericoConfig creada = registroReal.buscarPorCodigo("NODO-BAL-03").orElseThrow();
        assertEquals("Peaje Canelones", creada.estacionPeaje());
        assertEquals(ModoNodoPeriferico.SOBRECARGA, creada.modoOperacion());
        assertTrue(bean.getNodos().stream().anyMatch(n -> "NODO-BAL-03".equals(n.codigo()) && n.isPersonalizado()));

        bean.eliminarBalanza("NODO-BAL-03");
        assertTrue(registroReal.buscarPorCodigo("NODO-BAL-03").isEmpty());
    }

    @Test
    @DisplayName("abrirDialogoPasoBalanza y registrarPasoSimuladoBalanza emiten lectura en el nodo seleccionado")
    void testSimularPasoPorBalanza() {
        Vehiculo v = new Vehiculo("STP 1234", "Scania", "R450", 9000, 31000, true, null);
        when(vehiculoEJB.listarParaFiscalizacion(TOKEN_ADMIN)).thenReturn(List.of(v));
        registroReal.setHabilitado("NODO-BAL-02", true);

        bean.abrirDialogoPasoBalanza("NODO-BAL-02");
        assertEquals("STP 1234", bean.getMatriculaPaso());
        assertEquals(45000, bean.getPesoKgPaso());

        bean.setMatriculaPasoPersonalizada("LOR 2024");
        bean.setPesoKgPaso(46500);
        when(balanzaService.registrarLecturaExternaEnNodo(TOKEN_ADMIN, "NODO-BAL-02", "LOR 2024", 46500))
                .thenReturn(new PesadaBalanzaDTO(88, LocalDate.now(), LocalTime.NOON, 46500, "LOR 2024", null));

        bean.registrarPasoSimuladoBalanza();

        verify(balanzaService).registrarLecturaExternaEnNodo(TOKEN_ADMIN, "NODO-BAL-02", "LOR 2024", 46500);
        verify(trackingMockService, never()).detener();
        verify(trackingMockService, never()).detener(any());
        assertEquals("Balanza #2", bean.getNombreNodoPaso());
    }
}
