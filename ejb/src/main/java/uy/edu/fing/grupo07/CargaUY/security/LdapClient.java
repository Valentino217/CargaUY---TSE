package uy.edu.fing.grupo07.CargaUY.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Hashtable;
import java.util.List;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.Rdn;
import jakarta.ejb.*;

@Stateless
@LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class LdapClient {
    public void autenticar(String uid, String password) {
        if (uid == null || !uid.matches("[a-zA-Z0-9._-]{1,64}")
                || password == null || password.isBlank())
            throw new ErrorAcceso(401, "Credenciales inválidas");
        String url = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        URI uri = URI.create(url);
        boolean local = ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()) || "ldap".equals(uri.getHost()))
            && Boolean.parseBoolean(Config.valor("p1.entorno.local", "false"));
        if (!"ldaps".equals(uri.getScheme()) && !("ldap".equals(uri.getScheme()) && local))
            throw new ErrorAcceso(503, "LDAP requiere LDAPS fuera del entorno local");
        DirContext contexto = null;
        try {
            Hashtable<String,Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, url);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, new Rdn("uid", uid).toString() + ","
                + Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local"));
            env.put(Context.SECURITY_CREDENTIALS, password);
            env.put("com.sun.jndi.ldap.connect.timeout", "1500");
            env.put("com.sun.jndi.ldap.read.timeout", "1500");
            env.put(Context.REFERRAL, "throw");
            contexto = new InitialDirContext(env);
            contexto.getAttributes("", new String[] { "uid" }); // Fuerza el bind.
        } catch (AuthenticationException e) {
            throw new ErrorAcceso(401, "Credenciales inválidas");
        } catch (NamingException e) {
            throw new ErrorAcceso(503, "Directorio temporalmente no disponible");
        } finally {
            if (contexto != null) try { contexto.close(); } catch (NamingException ignored) {}
        }
    }

    public void crearOActualizarUsuario(String uid, String nombre, String mail, String password) {
        if (uid == null || !uid.matches("[a-zA-Z0-9._-]{1,64}") || password == null || password.isBlank()) {
            throw new ErrorAcceso(400, "UID o contraseña inválidos para LDAP");
        }
        String url = Config.valor("ldap.url", "ldap://127.0.0.1:1389");
        String rootDn = Config.valor("ldap.root.dn", "cn=manager,dc=cargauy,dc=local");
        String rootPw = Config.requerido("ldap.root.password");
        String peopleDn = Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local");
        String userDn = "uid=" + uid + "," + peopleDn;
        String passwordSsha = codificarSsha(password);

        DirContext contexto = null;
        try {
            Hashtable<String,Object> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, url);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, rootDn);
            env.put(Context.SECURITY_CREDENTIALS, rootPw);
            env.put("com.sun.jndi.ldap.connect.timeout", "2500");
            env.put("com.sun.jndi.ldap.read.timeout", "2500");
            contexto = new InitialDirContext(env);

            Attributes attrs = null;
            try {
                attrs = contexto.getAttributes(userDn);
            } catch (NameNotFoundException ignored) {
            }

            String cn = (nombre != null && !nombre.isBlank()) ? nombre.trim() : uid;
            String sn = "Personal";
            if (cn.contains(" ")) {
                sn = cn.substring(cn.lastIndexOf(" ") + 1);
            }

            if (attrs == null) {
                Attributes nueva = new BasicAttributes(true);
                Attribute oc = new BasicAttribute("objectClass");
                oc.add("top");
                oc.add("inetOrgPerson");
                nueva.put(oc);
                nueva.put("uid", uid);
                nueva.put("cn", cn);
                nueva.put("sn", sn);
                if (mail != null && !mail.isBlank()) {
                    nueva.put("mail", mail.trim());
                }
                nueva.put("userPassword", passwordSsha);
                contexto.createSubcontext(userDn, nueva);
            } else {
                List<ModificationItem> mods = new ArrayList<>();
                mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("userPassword", passwordSsha)));
                if (nombre != null && !nombre.isBlank()) {
                    mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("cn", cn)));
                    mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("sn", sn)));
                }
                if (mail != null && !mail.isBlank()) {
                    mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("mail", mail.trim())));
                }
                contexto.modifyAttributes(userDn, mods.toArray(new ModificationItem[0]));
            }
        } catch (NamingException e) {
            throw new ErrorAcceso(503, "No se pudo sincronizar el usuario en LDAP: " + e.getMessage());
        } finally {
            if (contexto != null) try { contexto.close(); } catch (NamingException ignored) {}
        }
    }

    /** Codifica una contraseña en formato RFC 2307 {@code {SSHA}} usando un salt aleatorio de 8 bytes. */
    static String codificarSsha(String password) {
        if (password == null || password.isBlank()) {
            throw new ErrorAcceso(400, "Contraseña inválida para LDAP");
        }
        try {
            byte[] salt = new byte[8];
            new SecureRandom().nextBytes(salt);
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(password.getBytes(StandardCharsets.UTF_8));
            sha1.update(salt);
            byte[] digest = sha1.digest();
            byte[] combined = new byte[digest.length + salt.length];
            System.arraycopy(digest, 0, combined, 0, digest.length);
            System.arraycopy(salt, 0, combined, digest.length, salt.length);
            return "{SSHA}" + Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible en el entorno", e);
        }
    }
}
