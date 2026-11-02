package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Descargo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Infraccion;
import uy.edu.fing.grupo07.CargaUY.domain.entity.RegistroAuditoria;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.exception.BusinessException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - GestionInfraccionesService (CU-C4)")
class GestionInfraccionesServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Infraccion> queryInfracciones;

    private GestionInfraccionesServiceImpl service;
    private Infraccion infraccionPrueba;
    private GuiaDeViaje guiaPrueba;
    private Funcionario funcionarioPrueba;

    @BeforeEach
    void setUp() {
        service = new GestionInfraccionesServiceImpl(em);

        guiaPrueba = new GuiaDeViaje();
        guiaPrueba.setId(100L);

        Vehiculo vehiculo = new Vehiculo("987654", "Scania", "R450", 9000, 31000, true, null);
        guiaPrueba.setVehiculo(vehiculo);

        Empresa empresa = new Empresa(50, "Logística del Sur SA", "LogiSur", "Ruta 1 km 25");
        guiaPrueba.setEmpresa(empresa);

        infraccionPrueba = new Infraccion(
                LocalDate.now(),
                TipoInfraccion.EXCESO_DE_CARGA,
                EstadoInfraccion.DETECTADA,
                guiaPrueba,
                "Exceso de 1500 kg constatado en balanza."
        );
        infraccionPrueba.setIdCaso(1);

        funcionarioPrueba = new Funcionario(
                "Inspector Juan",
                "juan@mtop.gub.uy",
                LocalDate.of(1982, 5, 20),
                23456789,
                "hash",
                501,
                "Fiscalización Nacional"
        );
    }

    @Test
    @DisplayName("Debe listar todas las infracciones mapeadas a DTO sin filtros")
    void testListarInfraccionesSinFiltros() {
        when(em.createQuery(anyString(), eq(Infraccion.class))).thenReturn(queryInfracciones);
        when(queryInfracciones.getResultList()).thenReturn(List.of(infraccionPrueba));

        List<InfraccionDTO> resultado = service.listarInfracciones(null, null);

        assertEquals(1, resultado.size());
        InfraccionDTO dto = resultado.get(0);
        assertEquals(1, dto.idCaso());
        assertEquals(TipoInfraccion.EXCESO_DE_CARGA, dto.tipo());
        assertEquals(EstadoInfraccion.DETECTADA, dto.estado());
        assertEquals("LogiSur", dto.nombreEmpresa());
        assertEquals("987654", dto.matricula());
    }

    @Test
    @DisplayName("Debe aplicar parámetros de estado y tipo al filtrar infracciones")
    void testListarInfraccionesConFiltroEstadoYTipo() {
        when(em.createQuery(anyString(), eq(Infraccion.class))).thenReturn(queryInfracciones);
        when(queryInfracciones.setParameter("estado", EstadoInfraccion.DETECTADA)).thenReturn(queryInfracciones);
        when(queryInfracciones.setParameter("tipo", TipoInfraccion.EXCESO_DE_CARGA)).thenReturn(queryInfracciones);
        when(queryInfracciones.getResultList()).thenReturn(List.of(infraccionPrueba));

        List<InfraccionDTO> resultado = service.listarInfracciones(
                EstadoInfraccion.DETECTADA,
                TipoInfraccion.EXCESO_DE_CARGA
        );

        assertEquals(1, resultado.size());
        verify(queryInfracciones).setParameter("estado", EstadoInfraccion.DETECTADA);
        verify(queryInfracciones).setParameter("tipo", TipoInfraccion.EXCESO_DE_CARGA);
    }

    @Test
    @DisplayName("Debe obtener una infracción existente por su identificador")
    void testObtenerInfraccionExistente() {
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        InfraccionDTO dto = service.obtenerInfraccion(1);

        assertNotNull(dto);
        assertEquals(1, dto.idCaso());
    }

    @Test
    @DisplayName("Debe lanzar BusinessException al buscar una infracción inexistente")
    void testObtenerInfraccionInexistenteLanzaExcepcion() {
        when(em.find(Infraccion.class, 999)).thenReturn(null);

        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.obtenerInfraccion(999)
        );

        assertTrue(excepcion.getMessage().contains("No existe"));
    }

    @Test
    @DisplayName("Debe cambiar el estado a NOTIFICADA cuando la infracción está DETECTADA")
    void testNotificarInfraccionDesdeEstadoDetectada() {
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        InfraccionDTO dto = service.notificarInfraccion(1);

        assertEquals(EstadoInfraccion.NOTIFICADA, dto.estado());
        assertEquals(EstadoInfraccion.NOTIFICADA, infraccionPrueba.getEstado());
    }

    @Test
    @DisplayName("Debe rechazar la notificación si la infracción ya se encuentra confirmada")
    void testNotificarInfraccionEstadoInvalidoLanzaExcepcion() {
        infraccionPrueba.setEstado(EstadoInfraccion.CONFIRMADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.notificarInfraccion(1)
        );

        assertTrue(excepcion.getMessage().contains("Operación no permitida"));
    }

    @Test
    @DisplayName("Debe registrar descargo y pasar a estado EN_DESCARGO cuando está NOTIFICADA")
    void testPresentarDescargoDesdeNotificada() {
        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        DescargoRequestDTO peticion = new DescargoRequestDTO("Balanza descalibrada, adjunto prueba técnica.");
        InfraccionDTO dto = service.presentarDescargo(1, peticion);

        assertEquals(EstadoInfraccion.EN_DESCARGO, dto.estado());
        assertNotNull(dto.textoDescargo());
        assertEquals("Balanza descalibrada, adjunto prueba técnica.", dto.textoDescargo());
        assertNotNull(infraccionPrueba.getDescargo());
    }

    @Test
    @DisplayName("Debe actualizar el texto del descargo si ya existía uno previo")
    void testPresentarDescargoActualizaDescargoExistente() {
        infraccionPrueba.setEstado(EstadoInfraccion.EN_DESCARGO);
        Descargo descargoPrevio = new Descargo(LocalDate.now(), "Texto anterior", infraccionPrueba);
        infraccionPrueba.setDescargo(descargoPrevio);

        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        DescargoRequestDTO peticion = new DescargoRequestDTO("Texto ampliado y corregido");
        InfraccionDTO dto = service.presentarDescargo(1, peticion);

        assertEquals("Texto ampliado y corregido", dto.textoDescargo());
        assertEquals("Texto ampliado y corregido", descargoPrevio.getDescargo());
    }

    @Test
    @DisplayName("Debe rechazar descargo con texto nulo o en blanco")
    void testPresentarDescargoTextoVacioLanzaExcepcion() {
        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.presentarDescargo(1, new DescargoRequestDTO("   "))
        );

        assertTrue(excepcion.getMessage().contains("obligatorio"));
    }

    @Test
    @DisplayName("Debe confirmar infracción notificada persistiendo RegistroAuditoria inmutable")
    void testConfirmarInfraccionConAuditoriaExitoso() {
        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);
        funcionarioPrueba.getRoles().add(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        when(em.find(uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario.class, 501)).thenReturn(funcionarioPrueba);

        ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(
                501,
                "Se confirma la infracción por exceso de carga constatado sin descargo válido."
        );

        InfraccionDTO dto = service.confirmarInfraccion(1, resolucion);

        assertEquals(EstadoInfraccion.CONFIRMADA, dto.estado());
        assertEquals("CONFIRMADA", dto.auditoria().resolucion());
        assertEquals("CONFIRMAR_INFRACCION", dto.auditoria().accion());
        assertTrue(dto.auditoria().usuario().contains("Inspector Juan"));
        verify(em).persist(any(RegistroAuditoria.class));
    }

    @Test
    @DisplayName("Debe rechazar la confirmación directa desde DETECTADA sin previa notificación")
    void testConfirmarInfraccionDesdeEstadoDetectadaLanzaExcepcion() {
        infraccionPrueba.setEstado(EstadoInfraccion.DETECTADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(501, "Fundamento");

        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.confirmarInfraccion(1, resolucion)
        );

        assertTrue(excepcion.getMessage().contains("Operación no permitida"));
        verify(em, never()).persist(any(RegistroAuditoria.class));
    }

    @Test
    @DisplayName("Debe rechazar la confirmación si no se proporciona fundamento")
    void testConfirmarInfraccionFundamentoVacioLanzaExcepcion() {
        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(501, "");

        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.confirmarInfraccion(1, resolucion)
        );

        assertTrue(excepcion.getMessage().contains("fundamento"));
    }

    @Test
    @DisplayName("Debe rechazar la resolución si el funcionario no existe")
    void testConfirmarInfraccionFuncionarioInexistenteLanzaExcepcion() {
        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);
        when(em.find(uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario.class, 999)).thenReturn(null);

        ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(999, "Fundamento válido");

        BusinessException excepcion = assertThrows(
                BusinessException.class,
                () -> service.confirmarInfraccion(1, resolucion)
        );

        assertTrue(excepcion.getMessage().contains("No existe el funcionario"));
    }

    @Test
    @DisplayName("Debe desestimar infracción y generar RegistroAuditoria inmutable")
    void testDesestimarInfraccionConAuditoriaExitoso() {
        infraccionPrueba.setEstado(EstadoInfraccion.EN_DESCARGO);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);
        funcionarioPrueba.getRoles().add(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        when(em.find(uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario.class, 501)).thenReturn(funcionarioPrueba);

        ResolucionInfraccionDTO resolucion = new ResolucionInfraccionDTO(
                501,
                "Se hace lugar al descargo presentado por la empresa de transporte."
        );

        InfraccionDTO dto = service.desestimarInfraccion(1, resolucion);

        assertEquals(EstadoInfraccion.DESESTIMADA, dto.estado());
        assertEquals("DESESTIMADA", dto.auditoria().resolucion());
        assertEquals("DESESTIMAR_INFRACCION", dto.auditoria().accion());
        verify(em).persist(any(RegistroAuditoria.class));
    }

    @Test
    @DisplayName("Punto D.5 - ControlAcceso bloquea acceso sin privilegios a listarInfracciones, obtenerInfraccion y presentarDescargo")
    void testControlAccesoRbacYAislamientoPorEmpresa() {
        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso controlMock =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        GestionInfraccionesServiceImpl svcSeguro = new GestionInfraccionesServiceImpl(em, controlMock);

        when(controlMock.exigir(
                eq("tok-chofer"),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD)
        )).thenThrow(new uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso(403, "Rol no autorizado"));

        assertThrows(
                uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso.class,
                () -> svcSeguro.listarInfracciones("tok-chofer", null, null)
        );

        // Ciudadano no puede consultar una infracción aún en estado DETECTADA
        uy.edu.fing.grupo07.CargaUY.security.Acceso actorCiudadano =
                new uy.edu.fing.grupo07.CargaUY.security.Acceso(
                        80, 41234567, "Ana Representante",
                        java.util.Set.of(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
                );
        when(controlMock.exigir(
                eq("tok-ciudadano"),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
        )).thenReturn(actorCiudadano);

        infraccionPrueba.setEstado(EstadoInfraccion.DETECTADA);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        assertThrows(
                uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso.class,
                () -> svcSeguro.obtenerInfraccion("tok-ciudadano", 1)
        );

        // Ciudadano de otra empresa no puede presentar descargo sobre empresa #50
        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        when(controlMock.empresa("tok-otra-empresa", 50, true))
                .thenThrow(new uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso(403, "No pertenece a la empresa"));

        assertThrows(
                uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso.class,
                () -> svcSeguro.presentarDescargo("tok-otra-empresa", 1, new DescargoRequestDTO("Intento cruzado"))
        );
    }

    @Test
    @DisplayName("Punto D.5 / E.8 - listarInfraccionesParaEmpresa aísla estrictamente por empresa vinculada (sin fuga multi-empresa) e incluye VIAJE_SIN_GUIA")
    void testListarInfraccionesParaEmpresaSinFugaEIncluyeViajeSinGuia() {
        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso controlMock =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        GestionInfraccionesServiceImpl svcSeguro = new GestionInfraccionesServiceImpl(em, controlMock);

        uy.edu.fing.grupo07.CargaUY.security.Acceso actorCiudadano =
                new uy.edu.fing.grupo07.CargaUY.security.Acceso(
                        81, 45556667, "Representante LogiSur",
                        java.util.Set.of(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO)
                );
        when(controlMock.exigir(
                anyString(),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CIUDADANO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.AUTORIDAD)
        )).thenReturn(actorCiudadano);

        @SuppressWarnings("unchecked")
        TypedQuery<Integer> qEmpresas = org.mockito.Mockito.mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Integer.class))).thenReturn(qEmpresas);
        when(qEmpresas.setParameter("usuarioId", 81)).thenReturn(qEmpresas);

        // Caso 1: usuario sin empresas vinculadas -> retorna lista vacía (jamás todas las notificadas)
        when(qEmpresas.getResultList()).thenReturn(List.of());
        List<InfraccionDTO> vacio = svcSeguro.listarInfraccionesParaEmpresa("tok-sin-empresa");
        assertTrue(vacio.isEmpty());
        verify(em, never()).createQuery(anyString(), eq(Infraccion.class));

        // Caso 2: usuario responsable de empresa 50 -> retorna caso VIAJE_SIN_GUIA (guia == null)
        when(qEmpresas.getResultList()).thenReturn(List.of(50));

        Empresa emp50 = guiaPrueba.getEmpresa();
        Vehiculo camionEmp50 = new Vehiculo("987654", "Scania", "R450", 9000, 31000, true, emp50);
        Infraccion infSinGuia = new Infraccion(
                LocalDate.now(),
                TipoInfraccion.VIAJE_SIN_GUIA,
                EstadoInfraccion.NOTIFICADA,
                camionEmp50,
                "Pesada en balanza sin guía activa."
        );
        infSinGuia.setIdCaso(77);

        when(em.createQuery(anyString(), eq(Infraccion.class))).thenReturn(queryInfracciones);
        when(queryInfracciones.setParameter("detectada", EstadoInfraccion.DETECTADA)).thenReturn(queryInfracciones);
        when(queryInfracciones.setParameter("empresas", List.of(50))).thenReturn(queryInfracciones);
        when(queryInfracciones.getResultList()).thenReturn(List.of(infSinGuia));

        List<InfraccionDTO> propios = svcSeguro.listarInfraccionesParaEmpresa("tok-emp-50");
        assertEquals(1, propios.size());
        assertEquals(77, propios.get(0).idCaso());
        assertNull(propios.get(0).guiaId());
        assertEquals("987654", propios.get(0).matricula());
        assertEquals("LogiSur", propios.get(0).nombreEmpresa());
        assertEquals(TipoInfraccion.VIAJE_SIN_GUIA, propios.get(0).tipo());
    }

    @Test
    @DisplayName("Punto D.5 - confirmarInfraccion(token, ...) toma funcionarioId del token autenticado sin IDs hardcodeados")
    void testConfirmarInfraccionConTokenAutenticado() {
        uy.edu.fing.grupo07.CargaUY.security.ControlAcceso controlMock =
                org.mockito.Mockito.mock(uy.edu.fing.grupo07.CargaUY.security.ControlAcceso.class);
        GestionInfraccionesServiceImpl svcSeguro = new GestionInfraccionesServiceImpl(em, controlMock);

        uy.edu.fing.grupo07.CargaUY.security.Acceso actorFunc =
                new uy.edu.fing.grupo07.CargaUY.security.Acceso(
                        501, 23456789, "Inspector Juan",
                        java.util.Set.of(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO)
                );
        when(controlMock.exigir(
                eq("tok-func-501"),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO),
                eq(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)
        )).thenReturn(actorFunc);

        infraccionPrueba.setEstado(EstadoInfraccion.NOTIFICADA);
        funcionarioPrueba.setId(501);
        funcionarioPrueba.getRoles().add(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);
        when(em.find(uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario.class, 501)).thenReturn(funcionarioPrueba);

        InfraccionDTO dto = svcSeguro.confirmarInfraccion("tok-func-501", 1, "Sanción firme verificada.");

        assertEquals(EstadoInfraccion.CONFIRMADA, dto.estado());
        assertTrue(dto.auditoria().usuario().contains("Inspector Juan"));
    }

    @Test
    @DisplayName("Punto E.c / E.d - procesarNotificacionAutomatica es idempotente y el ciclo completo acumula los 4 hitos en historialAuditoria append-only")
    void testCicloCompletoHistorialAuditoriaAppendOnlyYNotificacionAutomatica() {
        // Caso inexistente -> retorna null sin lanzar excepción
        when(em.find(Infraccion.class, 404)).thenReturn(null);
        assertNull(service.procesarNotificacionAutomatica(404));

        // 1. Creación inicial (MotorFiscalizacion adjunta CREAR_INFRACCION)
        infraccionPrueba.agregarRegistroAuditoria(new RegistroAuditoria(
                infraccionPrueba,
                EstadoInfraccion.DETECTADA.name(),
                LocalDate.now(),
                "SISTEMA (Motor de Fiscalización)",
                "CREAR_INFRACCION",
                "Exceso de 1500 kg constatado en balanza."
        ));
        when(em.find(Infraccion.class, 1)).thenReturn(infraccionPrueba);

        // 2. Notificación automática (E.c)
        InfraccionDTO trasNotificar = service.procesarNotificacionAutomatica(1);
        assertNotNull(trasNotificar);
        assertEquals(EstadoInfraccion.NOTIFICADA, trasNotificar.estado());
        assertEquals(2, trasNotificar.historialAuditoria().size());

        // Reintento idempotente de notificación automática: no agrega un tercer registro
        InfraccionDTO trasReintento = service.procesarNotificacionAutomatica(1);
        assertEquals(2, trasReintento.historialAuditoria().size());

        // 3. Presentación de descargo por la empresa
        InfraccionDTO trasDescargo = service.presentarDescargo(1, new DescargoRequestDTO("Certificado de tara actualizado."));
        assertEquals(EstadoInfraccion.EN_DESCARGO, trasDescargo.estado());
        assertEquals(3, trasDescargo.historialAuditoria().size());

        // 4. Resolución final por funcionario
        funcionarioPrueba.getRoles().add(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.FUNCIONARIO);
        when(em.find(uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario.class, 501)).thenReturn(funcionarioPrueba);

        InfraccionDTO trasConfirmar = service.confirmarInfraccion(
                1,
                new ResolucionInfraccionDTO(501, "El certificado adjunto corresponde a otro remolque; se confirma sanción.")
        );

        assertEquals(EstadoInfraccion.CONFIRMADA, trasConfirmar.estado());
        assertEquals(4, trasConfirmar.historialAuditoria().size());
        assertEquals("CREAR_INFRACCION", trasConfirmar.historialAuditoria().get(0).accion());
        assertEquals("NOTIFICAR_INFRACCION", trasConfirmar.historialAuditoria().get(1).accion());
        assertEquals("PRESENTAR_DESCARGO", trasConfirmar.historialAuditoria().get(2).accion());
        assertEquals("CONFIRMAR_INFRACCION", trasConfirmar.historialAuditoria().get(3).accion());
        assertEquals("CONFIRMAR_INFRACCION", trasConfirmar.auditoria().accion());
    }
}
