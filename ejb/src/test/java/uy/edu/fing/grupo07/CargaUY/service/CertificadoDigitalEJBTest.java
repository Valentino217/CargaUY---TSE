package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoCertificado;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - CertificadoDigitalEJB")
class CertificadoDigitalEJBTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Usuario> usuarioQuery;

    @Mock
    private TypedQuery<CertificadoDigital> certQuery;

    @InjectMocks
    private CertificadoDigitalEJB ejb;

    private void stubUsuarioQuery(List<Usuario> resultado) {
        when(em.createQuery(anyString(), eq(Usuario.class))).thenReturn(usuarioQuery);
        when(usuarioQuery.setParameter(anyString(), any())).thenReturn(usuarioQuery);
        when(usuarioQuery.getResultList()).thenReturn(resultado);
    }

    private void stubCertQuery(List<CertificadoDigital> resultado) {
        when(em.createQuery(anyString(), eq(CertificadoDigital.class))).thenReturn(certQuery);
        when(certQuery.setParameter(anyString(), any())).thenReturn(certQuery);
        when(certQuery.getResultList()).thenReturn(resultado);
    }

    @Test
    @DisplayName("buscarUsuarioPorCi retorna usuario o null según existencia")
    void buscarUsuarioPorCi() {
        Usuario u = new Funcionario();
        u.setId(10);
        u.setCi(12345678);
        stubUsuarioQuery(List.of(u));
        assertEquals(u, ejb.buscarUsuarioPorCi(12345678));

        stubUsuarioQuery(List.of());
        assertNull(ejb.buscarUsuarioPorCi(99999999));
    }

    @Test
    @DisplayName("registrarCertificado persiste cuando es válido y valida errores de negocio")
    void registrarCertificado() {
        Usuario u = new Funcionario();
        u.setId(5);
        u.setCi(11223344);
        stubUsuarioQuery(List.of(u));
        stubCertQuery(List.of());

        LocalDateTime vence = LocalDateTime.now().plusDays(30);
        CertificadoDigital creado = ejb.registrarCertificado(11223344, new byte[]{1, 2, 3}, "insp.p12", vence);
        assertNotNull(creado);
        assertEquals(EstadoCertificado.ACTIVO, creado.getEstado());
        assertEquals("insp.p12", creado.getNombreArchivo());
        verify(em).persist(creado);

        stubUsuarioQuery(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ejb.registrarCertificado(11223344, new byte[]{1}, "x.p12", vence));

        stubUsuarioQuery(List.of(u));
        stubCertQuery(List.of(creado));
        assertThrows(IllegalStateException.class,
                () -> ejb.registrarCertificado(11223344, new byte[]{1}, "x.p12", vence));
    }

    @Test
    @DisplayName("revocarCertificado y revocarCertificadoDeUsuario validan existencia, dueño y motivo")
    void revocarCertificados() {
        Usuario u = new Funcionario();
        u.setId(7);
        CertificadoDigital cert = new CertificadoDigital();
        cert.setUsuario(u);
        cert.setEstado(EstadoCertificado.ACTIVO);

        when(em.find(CertificadoDigital.class, 1)).thenReturn(cert);
        when(em.find(CertificadoDigital.class, 99)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> ejb.revocarCertificado(99, "motivo"));
        assertThrows(IllegalArgumentException.class, () -> ejb.revocarCertificadoDeUsuario(99, 7, "motivo"));
        assertThrows(SecurityException.class, () -> ejb.revocarCertificadoDeUsuario(1, 999, "motivo"));
        assertThrows(IllegalArgumentException.class, () -> ejb.revocarCertificado(1, "   "));

        ejb.revocarCertificadoDeUsuario(1, 7, "  Extravío de token  ");
        assertEquals(EstadoCertificado.REVOCADO, cert.getEstado());
        assertEquals("Extravío de token", cert.getMotivoRevocacion());
        assertNotNull(cert.getFechaRevocacion());

        assertThrows(IllegalStateException.class, () -> ejb.revocarCertificado(1, "otro"));
    }

    @Test
    @DisplayName("abrirCredenciales y obtenerFechaVencimiento validan archivo PKCS#12")
    void abrirCredencialesYFecha() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ejb.abrirCredenciales(null, "1234"));
        assertThrows(IllegalArgumentException.class, () -> ejb.abrirCredenciales(new byte[0], "1234"));
        assertThrows(IllegalArgumentException.class, () -> ejb.abrirCredenciales(new byte[]{1, 2}, ""));

        byte[] p12 = ActaServiceEJBTest.generarPkcs12Prueba("secreto");
        CredencialesFirma creds = ejb.abrirCredenciales(p12, "secreto");
        assertNotNull(creds);
        assertNotNull(ejb.obtenerFechaVencimiento(creds.getCertificado()));

        assertThrows(IllegalArgumentException.class, () -> ejb.abrirCredenciales(p12, "mala"));
    }

    @Test
    @DisplayName("listados y búsqueda de inspector delegan en TypedQuery")
    void listadosYInspector() {
        CertificadoDigital cert = new CertificadoDigital();
        stubCertQuery(List.of(cert));
        when(certQuery.setMaxResults(anyInt())).thenReturn(certQuery);

        assertEquals(1, ejb.listarCertificados().size());
        assertEquals(1, ejb.listarCertificadosPorUsuario(3).size());
        assertEquals(cert, ejb.buscarCertificadoActivoPorCiInspector(12345678));

        stubCertQuery(List.of());
        assertNull(ejb.buscarCertificadoActivoPorCiInspector(12345678));
    }
}
