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
}
