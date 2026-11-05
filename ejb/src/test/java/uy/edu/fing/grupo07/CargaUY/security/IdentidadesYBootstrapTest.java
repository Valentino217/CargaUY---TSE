package uy.edu.fing.grupo07.CargaUY.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - IdentidadesService, BootstrapAdmin, SesionesService, AutenticacionService, Config, ControlAcceso y FlujosService")
class IdentidadesYBootstrapTest {

    @Mock
    private EntityManager em;

    @Mock
    private ControlAcceso control;

    @Mock
    private SesionesService sesionesMock;

    @Mock
    private LdapClient ldapMock;

    @Mock
    private GubOidcClient gubMock;

    @Mock
    private FlujosService flujosMock;

    @Mock
    private TypedQuery<Usuario> qUsuario;

    @Mock
    private TypedQuery<Long> qLong;

    @Mock
    private TypedQuery<Integer> qInt;

    @Mock
    private TypedQuery<VinculoEmpresa> qVinculos;

    @Mock
    private Query qUpdate;

    private static void inyectar(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @BeforeEach
    void setUpProps() {
        System.clearProperty("p1.bootstrap");
        System.clearProperty("p1.entorno.local");
        System.clearProperty("p1.login.local");
        System.clearProperty("bootstrap.admin.uid");
        System.clearProperty("bootstrap.admin.ci");
        System.clearProperty("bootstrap.admin.nombre");
        System.clearProperty("bootstrap.admin.mail");
        System.clearProperty("bootstrap.admin.nacimiento");
    }

    @AfterEach
    void tearDownProps() {
        setUpProps();
    }

    @Test
    @DisplayName("Config: valor con propiedad del sistema, valor por defecto y requerido")
    void testConfig() {
        assertEquals("def", Config.valor("prop.inexistente.test", "def"));
        assertThrows(IllegalStateException.class, () -> Config.requerido("prop.inexistente.test"));

        System.setProperty("prop.inexistente.test", "valorConfigurado");
        assertEquals("valorConfigurado", Config.requerido("prop.inexistente.test"));
        System.clearProperty("prop.inexistente.test");
    }

    @Test
    @DisplayName("IdentidadesService: vincular, roles, vinculo, funcionEmpresa, buscarIdPorCi y listar")
    void testIdentidadesService() throws Exception {
        IdentidadesService service = new IdentidadesService();
        inyectar(service, "control", control);
        inyectar(service, "sesiones", sesionesMock);
        inyectar(service, "em", em);

        // vincular validaciones
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.vincular("t", 1, "OTRO", "uid")).getEstado());
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.vincular("t", 1, "LDAP", "uid con espacios!")).getEstado());

        when(em.find(Usuario.class, 99)).thenReturn(null);
        assertEquals(404, assertThrows(ErrorAcceso.class, () -> service.vincular("t", 99, "LDAP", "admin1")).getEstado());

        Administrador admin = new Administrador("Admin", "a@test.com", LocalDate.of(1990, 1, 1), 11111111, "pwd");
        admin.setId(1);
        admin.getRoles().add(TipoUsuario.ADMINISTRADOR);
        when(em.find(Usuario.class, 1)).thenReturn(admin);

        // Admin requiere LDAP, falla con GUB
        assertEquals(409, assertThrows(ErrorAcceso.class, () -> service.vincular("t", 1, "GUB", "sub-1")).getEstado());

        service.vincular("t", 1, "LDAP", "admin.uid");
        assertEquals("LDAP", admin.getOrigenIdentidad());
        assertEquals("admin.uid", admin.getUidExterno());
        verify(sesionesMock).revocarUsuario(1);

        // roles validaciones
        when(control.admin("t")).thenReturn(new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)));
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.roles("t", 1, Set.of())).getEstado());
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.roles("t", 1, Set.of(TipoUsuario.ADMINISTRADOR, TipoUsuario.CIUDADANO))).getEstado());
        assertEquals(409, assertThrows(ErrorAcceso.class, () -> service.roles("t", 1, Set.of(TipoUsuario.AUTORIDAD))).getEstado());

        Ciudadano ciu = new Ciudadano("Ciu", "c@test.com", LocalDate.of(1995, 5, 5), 22222222, "{GUB}");
        ciu.setId(2);
        ciu.setOrigenIdentidad("GUB");
        ciu.getRoles().add(TipoUsuario.CIUDADANO);
        when(em.find(Usuario.class, 2)).thenReturn(ciu);

        assertEquals(409, assertThrows(ErrorAcceso.class, () -> service.roles("t", 2, Set.of(TipoUsuario.ADMINISTRADOR))).getEstado());
        service.roles("t", 2, Set.of(TipoUsuario.CIUDADANO));
        assertTrue(ciu.getRoles().contains(TipoUsuario.CIUDADANO));

        // vinculo y funcionEmpresa
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.funcionEmpresa("t", 10, 2, TipoUsuario.FUNCIONARIO, true)).getEstado());

        Empresa emp = new Empresa(10, "Emp SA", "Emp", "Dir");
        when(em.find(Empresa.class, 10)).thenReturn(emp);

        service.vinculo("t", 10, 2, true, false);
        assertTrue(emp.getCiudadanos().contains(ciu));

        VinculoEmpresa vinc = new VinculoEmpresa(ciu, emp);
        vinc.setResponsable(true);

        when(em.find(VinculoEmpresa.class, VinculoEmpresa.clave(10, 2))).thenReturn(vinc);
        service.funcionEmpresa("t", 10, 2, TipoUsuario.CIUDADANO, false);
        assertFalse(vinc.isResponsable());

        // buscarIdPorCi y listar
        when(em.createQuery(contains("select u.id"), eq(Integer.class))).thenReturn(qInt);
        when(qInt.setParameter("ci", 22222222)).thenReturn(qInt);
        when(qInt.setMaxResults(1)).thenReturn(qInt);
        when(qInt.getResultList()).thenReturn(List.of(2));
        assertEquals(2, service.buscarIdPorCi(22222222));

        when(em.createQuery(contains("from VinculoEmpresa"), eq(VinculoEmpresa.class))).thenReturn(qVinculos);
        when(qVinculos.setParameter("empresa", 10)).thenReturn(qVinculos);
        when(qVinculos.setMaxResults(100)).thenReturn(qVinculos);
        when(qVinculos.getResultList()).thenReturn(List.of(vinc));
        List<IdentidadesService.VinculoVista> lista = service.listar("t", 10);
        assertEquals(1, lista.size());
        assertEquals(2, lista.get(0).usuario());
    }

    @Test
    @DisplayName("BootstrapAdmin: validaciones de entorno, UID, duplicados y creación exitosa")
    void testBootstrapAdmin() throws Exception {
        BootstrapAdmin boot = new BootstrapAdmin();
        inyectar(boot, "em", em);

        // Deshabilitado por defecto
        boot.iniciar();
        verifyNoInteractions(em);

        // Habilitado fuera de entorno local -> falla
        System.setProperty("p1.bootstrap", "true");
        System.setProperty("p1.entorno.local", "false");
        assertThrows(IllegalStateException.class, boot::iniciar);

        // UID inválido
        System.setProperty("p1.entorno.local", "true");
        System.setProperty("bootstrap.admin.uid", "uid invalido!");
        assertThrows(IllegalStateException.class, boot::iniciar);

        // UID válido pero ya existe
        System.setProperty("bootstrap.admin.uid", "admin.local");
        when(em.createQuery(contains("u.uidExterno=:uid"), eq(Long.class))).thenReturn(qLong);
        when(qLong.setParameter("uid", "admin.local")).thenReturn(qLong);
        when(qLong.getSingleResult()).thenReturn(1L);
        boot.iniciar();
        verify(em, never()).persist(any());

        // No existe UID, pero CI ocupada
        when(qLong.getSingleResult()).thenReturn(0L);
        System.setProperty("bootstrap.admin.ci", "90000001");
        @SuppressWarnings("unchecked")
        TypedQuery<Long> qCi = mock(TypedQuery.class);
        when(em.createQuery(contains("u.ci=:ci"), eq(Long.class))).thenReturn(qCi);
        when(qCi.setParameter("ci", 90000001)).thenReturn(qCi);
        when(qCi.getSingleResult()).thenReturn(1L);
        assertThrows(IllegalStateException.class, boot::iniciar);

        // Creación exitosa
        when(qCi.getSingleResult()).thenReturn(0L);
        System.setProperty("bootstrap.admin.nombre", "Admin Inicial");
        System.setProperty("bootstrap.admin.mail", "admin@cargauy.uy");
        System.setProperty("bootstrap.admin.nacimiento", "1985-01-15");
        boot.iniciar();
        verify(em).persist(any(Administrador.class));
    }

    @Test
    @DisplayName("SesionesService: emitirLdap, emitirGub, emitirDevChofer, emitirLocal, nacimiento y limpieza")
    void testSesionesServiceCompleto() throws Exception {
        SesionesService sesiones = new SesionesService();
        inyectar(sesiones, "em", em);

        // emitirLdap
        when(em.createQuery(contains("origenIdentidad='LDAP'"), eq(Usuario.class))).thenReturn(qUsuario);
        when(qUsuario.setParameter("uid", "admin1")).thenReturn(qUsuario);
        Administrador admin = new Administrador("Admin", "a@test.com", LocalDate.of(1990, 1, 1), 11111111, "{LDAP}");
        admin.setId(1);
        admin.getRoles().add(TipoUsuario.ADMINISTRADOR);
        when(qUsuario.getResultStream()).thenAnswer(inv -> Stream.of(admin));

        SesionesService.Login loginLdap = sesiones.emitirLdap("admin1");
        assertNotNull(loginLdap.token());
        assertTrue(loginLdap.usuario().tiene(TipoUsuario.ADMINISTRADOR));

        // emitirDevChofer
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> sesiones.emitirDevChofer(44444444)).getEstado());
        System.setProperty("p1.entorno.local", "true");
        when(em.createQuery(contains("u.ci=:ci"), eq(Usuario.class))).thenReturn(qUsuario);
        when(qUsuario.setParameter("ci", 44444444)).thenReturn(qUsuario);
        Chofer ch = new Chofer("Chofer", "ch@test.com", LocalDate.of(1990, 1, 1), 44444444, "{GUB}");
        ch.setId(4);
        when(qUsuario.getResultStream()).thenAnswer(inv -> Stream.of(ch));

        SesionesService.Login loginChofer = sesiones.emitirDevChofer(44444444);
        assertTrue(loginChofer.usuario().tiene(TipoUsuario.CHOFER));

        // emitirLocal
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> sesiones.emitirLocal("admin", "pwd")).getEstado());
        System.setProperty("p1.login.local", "true");
        assertEquals(401, assertThrows(ErrorAcceso.class, () -> sesiones.emitirLocal("  ", "pwd")).getEstado());

        String bcryptHash = BCrypt.withDefaults().hashToString(4, "MiClave123!".toCharArray());
        Ciudadano uLocal = new Ciudadano("LocalUser", "local@test.com", LocalDate.of(1992, 2, 2), 33333333, bcryptHash);
        uLocal.setId(3);
        uLocal.getRoles().add(TipoUsuario.CIUDADANO);

        when(em.createQuery(contains("lower(u.mail) = lower(:id)"), eq(Usuario.class))).thenReturn(qUsuario);
        when(qUsuario.setParameter(anyString(), any())).thenReturn(qUsuario);
        when(qUsuario.getResultList()).thenReturn(List.of(uLocal));

        SesionesService.Login loginLocal = sesiones.emitirLocal("33.333.333", "MiClave123!");
        assertEquals(3, loginLocal.usuario().id());
        assertEquals(401, assertThrows(ErrorAcceso.class, () -> sesiones.emitirLocal("33333333", "ClaveErronea")).getEstado());

        // revocarUsuario y limpiarVencidas
        when(em.createQuery(contains("delete from SesionAcceso"))).thenReturn(qUpdate);
        when(qUpdate.setParameter(anyString(), any())).thenReturn(qUpdate);
        sesiones.revocarUsuario(3);
        sesiones.limpiarVencidas();
        verify(qUpdate, times(2)).executeUpdate();
    }

    @Test
    @DisplayName("AutenticacionService: login LDAP, fallback local, loginGub, prepararMovil, loginMovil, loginDevChofer, validarToken y logout")
    void testAutenticacionService() throws Exception {
        AutenticacionService auth = new AutenticacionService();
        inyectar(auth, "ldap", ldapMock);
        inyectar(auth, "gub", gubMock);
        inyectar(auth, "sesiones", sesionesMock);
        inyectar(auth, "flujos", flujosMock);

        SesionesService.Login loginEsperado = new SesionesService.Login("tok", new Acceso(1, 111, "A", Set.of(TipoUsuario.ADMINISTRADOR)), Instant.now());
        when(sesionesMock.emitirLdap("admin")).thenReturn(loginEsperado);
        assertEquals(loginEsperado, auth.login("admin", "pwd"));

        // Fallback local cuando p1.login.local=true y LDAP lanza excepción
        System.setProperty("p1.login.local", "true");
        doThrow(new RuntimeException("LDAP off")).when(ldapMock).autenticar("user", "pwd");
        when(sesionesMock.emitirLocal("user", "pwd")).thenReturn(loginEsperado);
        assertEquals(loginEsperado, auth.login("user", "pwd"));

        // loginGub, prepararMovil, loginMovil, loginDevChofer, validarToken, logout
        GubOidcClient.Identidad idGub = new GubOidcClient.Identidad("sub", 22222222, "Nom", "m@test.com", LocalDate.of(1990, 1, 1));
        when(gubMock.validar("code", "ver", "nonce")).thenReturn(idGub);
        when(sesionesMock.emitirGub(idGub)).thenReturn(loginEsperado);
        assertEquals(loginEsperado, auth.loginGub("code", "ver", "nonce"));

        FlujosService.Inicio inicio = new FlujosService.Inicio("st", "nc", "http://auth");
        when(flujosMock.preparar("ch")).thenReturn(inicio);
        assertEquals(inicio, auth.prepararMovil("ch"));

        when(flujosMock.consumir("st", "ver")).thenReturn("nc");
        when(gubMock.validar("code", "ver", "nc", true)).thenReturn(idGub);
        assertEquals(loginEsperado, auth.loginMovil("code", "st", "ver"));

        when(sesionesMock.emitirDevChofer(444)).thenReturn(loginEsperado);
        assertEquals(loginEsperado, auth.loginDevChofer(444));

        when(sesionesMock.validarToken("tok")).thenReturn(loginEsperado.usuario());
        assertEquals(loginEsperado.usuario(), auth.validarToken("tok"));

        auth.logout("tok");
        verify(sesionesMock).logout("tok");
    }

    @Test
    @DisplayName("ControlAcceso: exigir, admin, empresa, choferAsignable, consultarChofer y guiaChofer")
    void testControlAccesoCompleto() throws Exception {
        ControlAcceso ctrl = new ControlAcceso();
        inyectar(ctrl, "sesiones", sesionesMock);
        inyectar(ctrl, "em", em);

        Acceso accAdmin = new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR));
        Acceso accAut = new Acceso(2, 22222222, "Aut", Set.of(TipoUsuario.AUTORIDAD));
        Acceso accCiu = new Acceso(3, 33333333, "Ciu", Set.of(TipoUsuario.CIUDADANO));
        Acceso accChof = new Acceso(4, 44444444, "Chof", Set.of(TipoUsuario.CHOFER));

        when(sesionesMock.validarToken("tAdmin")).thenReturn(accAdmin);
        when(sesionesMock.validarToken("tAut")).thenReturn(accAut);
        when(sesionesMock.validarToken("tCiu")).thenReturn(accCiu);
        when(sesionesMock.validarToken("tChof")).thenReturn(accChof);

        assertEquals(accAdmin, ctrl.admin("tAdmin"));
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> ctrl.admin("tCiu")).getEstado());

        // Autoridad puede leer empresa pero no editar
        assertEquals(accAut, ctrl.empresa("tAut", 10, false));
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> ctrl.empresa("tAut", 10, true)).getEstado());

        // Ciudadano responsable activo en empresa activa
        Empresa emp = new Empresa(10, "R", "N", "D");
        Ciudadano ciuEnt = new Ciudadano("Ciu", "c@test.com", LocalDate.now(), 33333333, "p");
        ciuEnt.setId(3);
        VinculoEmpresa vinc = new VinculoEmpresa(ciuEnt, emp);
        vinc.setResponsable(true);
        when(em.find(Empresa.class, 10)).thenReturn(emp);
        when(em.find(VinculoEmpresa.class, "10:3")).thenReturn(vinc);
        assertEquals(accCiu, ctrl.empresa("tCiu", 10, true));

        // choferAsignable
        Chofer chofEnt = new Chofer("Ch", "ch@test.com", LocalDate.now(), 44444444, "p");
        chofEnt.setId(4);
        chofEnt.getRoles().add(TipoUsuario.CHOFER);
        VinculoEmpresa vincChof = new VinculoEmpresa(chofEnt, emp);
        vincChof.setChofer(true);
        when(em.find(Usuario.class, 4)).thenReturn(chofEnt);
        when(em.find(VinculoEmpresa.class, "10:4")).thenReturn(vincChof);
        ctrl.choferAsignable("tAdmin", 10, 4);

        vincChof.setChofer(false);
        assertEquals(409, assertThrows(ErrorAcceso.class, () -> ctrl.choferAsignable("tAdmin", 10, 4)).getEstado());

        // consultarChofer
        ctrl.consultarChofer("tChof", 4, 44444444);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> ctrl.consultarChofer("tChof", 99, null)).getEstado());

        // guiaChofer
        assertEquals(400, assertThrows(ErrorAcceso.class, () -> ctrl.guiaChofer("tChof", null)).getEstado());
        when(em.find(GuiaDeViaje.class, 50L)).thenReturn(null);
        assertEquals(404, assertThrows(ErrorAcceso.class, () -> ctrl.guiaChofer("tChof", 50L)).getEstado());

        GuiaDeViaje g = new GuiaDeViaje();
        g.setId(50L);
        g.setChofer(chofEnt);
        when(em.find(GuiaDeViaje.class, 50L)).thenReturn(g);
        ctrl.guiaChofer("tChof", 50L);

        Chofer otroChof = new Chofer();
        otroChof.setId(99);
        g.setChofer(otroChof);
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> ctrl.guiaChofer("tChof", 50L)).getEstado());
    }

    @Test
    @DisplayName("FlujosService: preparar, consumir y limpiar")
    void testFlujosService() throws Exception {
        FlujosService flujos = new FlujosService();
        inyectar(flujos, "em", em);

        assertEquals(400, assertThrows(ErrorAcceso.class, () -> flujos.preparar("corto")).getEstado());

        String verifier = Cripto.aleatorio();
        String challenge = Cripto.pkce(verifier);
        FlujosService.Inicio ini = flujos.preparar(challenge);
        assertNotNull(ini.state());
        assertNotNull(ini.nonce());
        assertTrue(ini.url().contains(challenge));

        FlujoIdentidad guardado = new FlujoIdentidad(Cripto.hash(ini.state()), challenge, ini.nonce(), Instant.now().plusSeconds(120));
        when(em.find(FlujoIdentidad.class, Cripto.hash(ini.state()), LockModeType.PESSIMISTIC_WRITE)).thenReturn(guardado);

        String nonceConsumido = flujos.consumir(ini.state(), verifier);
        assertEquals(ini.nonce(), nonceConsumido);
        verify(em).remove(guardado);

        when(em.createQuery(contains("delete from FlujoIdentidad"))).thenReturn(qUpdate);
        when(qUpdate.setParameter(anyString(), any())).thenReturn(qUpdate);
        flujos.limpiar();
        verify(qUpdate).executeUpdate();
    }
}
