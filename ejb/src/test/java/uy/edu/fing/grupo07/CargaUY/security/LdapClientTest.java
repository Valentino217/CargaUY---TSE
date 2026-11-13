package uy.edu.fing.grupo07.CargaUY.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias - LdapClient ({SSHA} y fail-closed)")
class LdapClientTest {

    @Test
    @DisplayName("codificarSsha genera hash RFC 2307 {SSHA} verificable con salt aleatorio de 8 bytes")
    void testCodificarSshaFormatoYVerificacion() throws Exception {
        String ssha1 = LdapClient.codificarSsha("ClaveSegura123!");
        String ssha2 = LdapClient.codificarSsha("ClaveSegura123!");

        assertTrue(ssha1.startsWith("{SSHA}"));
        assertTrue(ssha2.startsWith("{SSHA}"));
        assertNotEquals(ssha1, ssha2, "Cada hash debe usar un salt aleatorio distinto");

        byte[] decoded = Base64.getDecoder().decode(ssha1.substring("{SSHA}".length()));
        assertEquals(28, decoded.length, "Debe contener 20 bytes SHA-1 + 8 bytes salt");

        byte[] digestEsperado = Arrays.copyOfRange(decoded, 0, 20);
        byte[] salt = Arrays.copyOfRange(decoded, 20, 28);

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update("ClaveSegura123!".getBytes(StandardCharsets.UTF_8));
        sha1.update(salt);
        byte[] digestCalculado = sha1.digest();

        assertTrue(MessageDigest.isEqual(digestEsperado, digestCalculado));
    }

    @Test
    @DisplayName("codificarSsha rechaza contraseñas nulas o en blanco")
    void testCodificarSshaRechazaVacia() {
        ErrorAcceso errVacia = assertThrows(ErrorAcceso.class, () -> LdapClient.codificarSsha("  "));
        assertEquals(400, errVacia.getEstado());

        ErrorAcceso errNula = assertThrows(ErrorAcceso.class, () -> LdapClient.codificarSsha(null));
        assertEquals(400, errNula.getEstado());
    }

    @Test
    @DisplayName("crearOActualizarUsuario rechaza UID inválido o contraseña vacía antes de conectar")
    void testCrearOActualizarValidaEntrada() {
        LdapClient client = new LdapClient();

        ErrorAcceso errUid = assertThrows(
                ErrorAcceso.class,
                () -> client.crearOActualizarUsuario("uid/invalido", "Nombre", "mail@test.uy", "secreto")
        );
        assertEquals(400, errUid.getEstado());

        ErrorAcceso errPass = assertThrows(
                ErrorAcceso.class,
                () -> client.crearOActualizarUsuario("funcionario.ok", "Nombre", "mail@test.uy", " ")
        );
        assertEquals(400, errPass.getEstado());
    }

    @Test
    @DisplayName("crearOActualizarUsuario falla cerrado sin ldap.root.password por defecto en código")
    void testCrearOActualizarFallaCerradoSinPasswordConfigurada() {
        if (System.getenv("LDAP_ROOT_PASSWORD") != null && !System.getenv("LDAP_ROOT_PASSWORD").isBlank()) {
            return;
        }
        String prev = System.getProperty("ldap.root.password");
        try {
            System.clearProperty("ldap.root.password");
            LdapClient client = new LdapClient();
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> client.crearOActualizarUsuario("funcionario.ok", "Func", "f@cargauy.uy", "Secret#2026")
            );
            assertTrue(ex.getMessage().contains("ldap.root.password"));
        } finally {
            if (prev == null) {
                System.clearProperty("ldap.root.password");
            } else {
                System.setProperty("ldap.root.password", prev);
            }
        }
    }
}
