package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;
import uy.edu.fing.grupo07.CargaUY.integration.ClientePdi;
import uy.edu.fing.grupo07.CargaUY.integration.ResultadoPdi;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.security.SesionesService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - UsuarioEJB, FuncionarioEJB, ValidacionPermisosService, ConsultaCiudadanoService, EstadoService y Servicios de Gestión")
class UsuarioFuncionarioEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private ControlAcceso control;

    @Mock
    private ClientePdi pdi;

    @Mock
    private SesionesService sesiones;

    @Mock
    private TypedQuery<Vehiculo> qVehiculos;

    @Mock
    private TypedQuery<Long> qLong;

    @Mock
    private TypedQuery<Usuario> qUsuarios;

    @Mock
    private TypedQuery<Empresa> qEmpresas;

    @Mock
    private TypedQuery<Rubro> qRubros;

    @Mock
    private TypedQuery<TipoCarga> qTiposCarga;

    private static void inyectar(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("UsuarioEJB y FuncionarioEJB: instanciación y retiro de alta antigua")
    void testUsuarioYFuncionarioEJB() {
        UsuarioEJB usuarioEJB = new UsuarioEJB();
        assertNotNull(usuarioEJB);

        FuncionarioEJB funcionarioEJB = new FuncionarioEJB();
        Funcionario f = new Funcionario("Nom", "m@test.com", LocalDate.of(1990, 1, 1), 12345678, "pwd", 10, "Depto");
        assertThrows(ErrorNegocio.class, () -> funcionarioEJB.crear(f));
    }

    @Test
    @DisplayName("ValidacionPermisosService: consultar permisos y estados de unidades de empresa")
    void testValidacionPermisosService() throws Exception {
        ValidacionPermisosService service = new ValidacionPermisosService();
        inyectar(service, "control", control);
        inyectar(service, "em", em);

        assertEquals(400, assertThrows(ErrorAcceso.class, () -> service.consultar("token", 0)).getEstado());

        when(em.find(Empresa.class, 99)).thenReturn(null);
        assertEquals(404, assertThrows(ErrorAcceso.class, () -> service.consultar("token", 99)).getEstado());

        Empresa emp = new Empresa(10, "Razon SA", "Publico", "Dir");
        when(em.find(Empresa.class, 10)).thenReturn(emp);

        LocalDate hoy = LocalDate.now();

        // Unidad 1: inhabilitada administrativamente
        Vehiculo v1 = new Vehiculo("MAT001", "M", "M", 1000, 2000, false, emp);

        // Unidad 2: PNC vencido, ITV vigente -> NO_VIGENTE
        Vehiculo v2 = new Vehiculo("MAT002", "M", "M", 1000, 2000, true, emp);
        v2.getPncs().add(new PNC(hoy.minusDays(5), v2));
        v2.getItv().add(new ITV(hoy.plusDays(10), v2));

        // Unidad 3: sin información (listas vacías o null) -> INFORMACION_INCOMPLETA
        Vehiculo v3 = new Vehiculo("MAT003", "M", "M", 1000, 2000, true, emp);
        v3.setPncs(null);
        v3.setItv(null);

        // Unidad 4: PNC e ITV vigentes -> VIGENTE
        Vehiculo v4 = new Vehiculo("MAT004", "M", "M", 1000, 2000, true, emp);
        v4.getPncs().add(new PNC(hoy.plusDays(10), v4));
        v4.getItv().add(new ITV(hoy.plusDays(15), v4));

        // Unidad 5: PNC con fecha null -> SIN_INFORMACION -> INFORMACION_INCOMPLETA
        Vehiculo v5 = new Vehiculo("MAT005", "M", "M", 1000, 2000, true, emp);
        v5.getPncs().add(new PNC(null, v5));
        v5.getItv().add(new ITV(hoy.plusDays(5), v5));

        when(em.createQuery(anyString(), eq(Vehiculo.class))).thenReturn(qVehiculos);
        when(qVehiculos.setParameter("numero", 10)).thenReturn(qVehiculos);
        when(qVehiculos.getResultList()).thenReturn(List.of(v1, v2, v3, v4, v5));

        ValidacionEmpresaDTO res = service.consultar("token", 10);
        assertEquals(10, res.numero());
        assertEquals(5, res.unidades().size());
        assertEquals(1L, res.habilitadas());
        assertEquals("INHABILITADA", res.unidades().get(0).resultado());
        assertEquals("NO_VIGENTE", res.unidades().get(1).resultado());
        assertEquals("INFORMACION_INCOMPLETA", res.unidades().get(2).resultado());
        assertEquals("VIGENTE", res.unidades().get(3).resultado());
        assertEquals("INFORMACION_INCOMPLETA", res.unidades().get(4).resultado());
    }

    @Test
    @DisplayName("ConsultaCiudadanoService: permite consulta propia o por admin/funcionario y bloquea ajena")
    void testConsultaCiudadanoService() throws Exception {
        ConsultaCiudadanoService service = new ConsultaCiudadanoService();
        inyectar(service, "control", control);
        inyectar(service, "pdi", pdi);

        ResultadoPdi esperado = new ResultadoPdi(ResultadoPdi.Estado.DISPONIBLE, 12345678, "Juan", "Perez", LocalDate.of(1990, 1, 1), true, "OK");
        when(pdi.consultar(12345678, "corr-1")).thenReturn(esperado);

        // Propia CI
        when(control.exigir("tokenCiu")).thenReturn(new Acceso(5, 12345678, "Juan", Set.of(TipoUsuario.CIUDADANO)));
        assertEquals(esperado, service.consultar("tokenCiu", 12345678, "corr-1"));

        // Otra CI como ciudadano -> 403
        assertEquals(403, assertThrows(ErrorAcceso.class, () -> service.consultar("tokenCiu", 87654321, "corr-1")).getEstado());

        // Otra CI como FUNCIONARIO -> permitido
        when(control.exigir("tokenFunc")).thenReturn(new Acceso(9, 99999999, "Func", Set.of(TipoUsuario.FUNCIONARIO)));
        assertEquals(esperado, service.consultar("tokenFunc", 12345678, "corr-1"));
    }

    @Test
    @DisplayName("EstadoService: listo retorna true con select 1 y false ante error")
    void testEstadoService() throws Exception {
        EstadoService service = new EstadoService();
        inyectar(service, "em", em);

        Query nq = mock(Query.class);
        when(em.createNativeQuery("select 1")).thenReturn(nq);
        when(nq.getSingleResult()).thenReturn(1);
        assertTrue(service.listo());

        when(nq.getSingleResult()).thenReturn(0);
        assertFalse(service.listo());

        when(em.createNativeQuery("select 1")).thenThrow(new RuntimeException("DB down"));
        assertFalse(service.listo());
    }

    @Test
    @DisplayName("ReglasLecturaService y Validaciones: lectura de snapshot y validaciones de texto/positivos")
    void testReglasLecturaYValidaciones() throws Exception {
        ReglasLecturaService reglasService = new ReglasLecturaService();
        inyectar(reglasService, "em", em);

        when(em.find(eq(ReglaFiscalizacion.class), eq(1), anyMap())).thenReturn(null);
        assertThrows(ErrorNegocio.class, reglasService::actuales);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(600, 4.5);
        when(em.find(eq(ReglaFiscalizacion.class), eq(1), anyMap())).thenReturn(regla);
        ReglasLecturaService.Snapshot snap = reglasService.actuales();
        assertEquals(600, snap.cargaKg());
        assertEquals(4.5, snap.desvioKm());
        verify(em).refresh(regla);

        assertEquals("Hola", Validaciones.texto("  Hola  ", "Campo", 10));
        assertThrows(ErrorNegocio.class, () -> Validaciones.texto("   ", "Campo", 10));
        assertThrows(ErrorNegocio.class, () -> Validaciones.texto("123456", "Campo", 3));
        assertEquals(5, Validaciones.positivo(5, "Num"));
        assertThrows(ErrorNegocio.class, () -> Validaciones.positivo(0, "Num"));
    }

    @Test
    @DisplayName("GestionPerfilesService: creación de todos los subtipos, consultas, listados y empresas")
    void testGestionPerfilesServiceCompleto() throws Exception {
        GestionPerfilesService service = new GestionPerfilesService();
        inyectar(service, "em", em);
        inyectar(service, "control", control);
        inyectar(service, "sesiones", sesiones);

        Acceso adminAcceso = new Acceso(1, 11111111, "Admin", Set.of(TipoUsuario.ADMINISTRADOR));
        lenient().when(control.admin("token")).thenReturn(adminAcceso);
        lenient().when(control.exigir("token")).thenReturn(adminAcceso);
        lenient().when(control.exigir("token", TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO)).thenReturn(adminAcceso);

        when(em.createQuery(contains("count(u)"), eq(Long.class))).thenReturn(qLong);
        when(qLong.setParameter(eq("ci"), anyInt())).thenReturn(qLong);
        when(qLong.getSingleResult()).thenReturn(0L);

        // Crear Chofer, Funcionario, Autoridad, Administrador
        service.crearUsuario("token", new DatosUsuario(TipoUsuario.AUTORIDAD, "Aut", "a@test.com", LocalDate.of(1990, 1, 1), 1003, "Password123!", null, null, "Director"));
        // eliminado creador de funcionario directo
        service.crearUsuario("token", new DatosUsuario(TipoUsuario.AUTORIDAD, "Aut", "a@test.com", LocalDate.of(1990, 1, 1), 1003, "Password123!", null, null, "Director"));
        service.crearUsuario("token", new DatosUsuario(TipoUsuario.ADMINISTRADOR, "Adm", "ad@test.com", LocalDate.of(1990, 1, 1), 1004, "Password123!", null, null, null));

        // Contraseña corta
        assertThrows(ErrorNegocio.class, () -> service.crearUsuario("token",
                new DatosUsuario(TipoUsuario.ADMINISTRADOR, "Adm", "ad@test.com", LocalDate.of(1990, 1, 1), 1005, "123", null, null, null)));

        // registrarConRoles para AUTORIDAD y FUNCIONARIO
        service.registrarConRoles("token", new DatosUsuario(TipoUsuario.AUTORIDAD, "Aut2", "a2@test.com", LocalDate.of(1990, 1, 1), 1006, null, null, null, "Ministro"), Set.of(TipoUsuario.AUTORIDAD));
        service.registrarConRoles("token", new DatosUsuario(TipoUsuario.FUNCIONARIO, "Func2", "f2@test.com", LocalDate.of(1990, 1, 1), 1007, null, 77, "Rutas", null), Set.of(TipoUsuario.FUNCIONARIO));

        // consultarUsuario y listarUsuarios
        Funcionario funcEntity = new Funcionario("Func", "f@test.com", LocalDate.of(1990, 1, 1), 1002, "pwd", 55, "Fiscal");
        funcEntity.setId(15);
        when(em.find(Usuario.class, 15)).thenReturn(funcEntity);
        assertEquals("FUNCIONARIO", service.consultarUsuario("token", 15).getTipo());

        when(em.createQuery(contains("select u from Usuario u"), eq(Usuario.class))).thenReturn(qUsuarios);
        when(qUsuarios.setParameter(eq("tipo"), any())).thenReturn(qUsuarios);
        when(qUsuarios.setFirstResult(anyInt())).thenReturn(qUsuarios);
        when(qUsuarios.setMaxResults(anyInt())).thenReturn(qUsuarios);
        when(qUsuarios.getResultList()).thenReturn(List.of(funcEntity));

        assertEquals(1, service.listarUsuarios("token", null, 0, 10).size());
        assertEquals(1, service.listarUsuarios("token", TipoUsuario.FUNCIONARIO, 0, 10).size());
        assertThrows(ErrorNegocio.class, () -> service.listarUsuarios("token", null, -1, 10));

        // Gestión de Empresas
        when(em.find(Empresa.class, 50)).thenReturn(null);
        service.crearEmpresa("token", 50, "Emp 50 SA", "Emp50", "Calle 50");

        Empresa emp50 = new Empresa(50, "Emp 50 SA", "Emp50", "Calle 50");
        when(em.find(Empresa.class, 50)).thenReturn(emp50);
        assertThrows(ErrorNegocio.class, () -> service.crearEmpresa("token", 50, "Emp 50 SA", "Emp50", "Calle 50"));

        assertEquals(50, service.consultarEmpresa("token", 50).getNumero());
        service.modificarEmpresa("token", 50, 0L, "Nueva Razon", "Nuevo Nom", "Nueva Dir");
        assertEquals("Nueva Razon", emp50.getRazonSocial());
        assertThrows(ErrorAcceso.class, () -> service.modificarEmpresa("token", 50, 99L, "R", "N", "D"));

        service.cambiarEstadoEmpresa("token", 50, false);
        assertFalse(emp50.isActiva());

        when(em.createQuery(anyString(), eq(Empresa.class))).thenReturn(qEmpresas);
        when(qEmpresas.setFirstResult(anyInt())).thenReturn(qEmpresas);
        when(qEmpresas.setMaxResults(anyInt())).thenReturn(qEmpresas);
        when(qEmpresas.getResultList()).thenReturn(List.of(emp50));
        assertEquals(1, service.listarEmpresas("token", 0, 10).size());
    }

    @Test
    @DisplayName("GestionPlataformaService: ABM de rubros, tipos de carga y consulta de reglas")
    void testGestionPlataformaServiceCompleto() throws Exception {
        GestionPlataformaService service = new GestionPlataformaService();
        inyectar(service, "em", em);
        inyectar(service, "control", control);

        // Rubros
        service.crearRubro("token", "Forestal", "Madera y celulosa");
        Rubro r = new Rubro("Forestal", "Madera");
        r.setCodigo(1);
        when(em.find(Rubro.class, 1)).thenReturn(r);
        service.modificarRubro("token", 1, "Agroforestal", null);
        assertEquals("Agroforestal", r.getNombre());

        when(em.createQuery(contains("from Rubro"), eq(Rubro.class))).thenReturn(qRubros);
        when(qRubros.getResultList()).thenReturn(List.of(r));
        assertEquals(1, service.listarRubros("token").size());

        when(em.createQuery(contains("join e.rubros"), eq(Long.class))).thenReturn(qLong);
        when(qLong.setParameter("codigo", 1)).thenReturn(qLong);
        when(qLong.getSingleResult()).thenReturn(1L);
        assertThrows(ErrorNegocio.class, () -> service.eliminarRubro("token", 1));

        when(qLong.getSingleResult()).thenReturn(0L);
        service.eliminarRubro("token", 1);
        verify(em).remove(r);

        // Tipos de carga
        service.crearTipoCarga("token", "Peligrosa");
        TipoCarga tc = new TipoCarga("Peligrosa");
        tc.setCodigo(2);
        when(em.find(TipoCarga.class, 2)).thenReturn(tc);
        service.modificarTipoCarga("token", 2, "Muy Peligrosa");
        assertEquals("Muy Peligrosa", tc.getDesc());

        when(em.createQuery(contains("from TipoCarga"), eq(TipoCarga.class))).thenReturn(qTiposCarga);
        when(qTiposCarga.getResultList()).thenReturn(List.of(tc));
        assertEquals(1, service.listarTiposCarga("token").size());

        when(em.createQuery(contains("join g.tiposCarga"), eq(Long.class))).thenReturn(qLong);
        when(qLong.setParameter("codigo", 2)).thenReturn(qLong);
        when(qLong.getSingleResult()).thenReturn(2L);
        assertThrows(ErrorNegocio.class, () -> service.eliminarTipoCarga("token", 2));

        when(qLong.getSingleResult()).thenReturn(0L);
        service.eliminarTipoCarga("token", 2);
        verify(em).remove(tc);

        // Reglas
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(null);
        assertThrows(ErrorNegocio.class, () -> service.consultarRegla("token"));
        service.crearRegla("token", 500, 3.0);

        ReglaFiscalizacion regla = new ReglaFiscalizacion(500, 3.0);
        when(em.find(ReglaFiscalizacion.class, 1)).thenReturn(regla);
        assertEquals(500, service.consultarRegla("token").cargaKg());
        assertThrows(ErrorNegocio.class, () -> service.crearRegla("token", 500, 3.0));
    }
}
