package uy.edu.fing.grupo07.CargaUY.security;

import java.net.URI;
import java.util.ArrayList;
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
        String rootPw = Config.valor("ldap.root.password", "vQUP53jJO6NyQD9V8hXK7oFQ-NmPjD9VS1NLoC-INkE");
        String peopleDn = Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local");
        String userDn = "uid=" + uid + "," + peopleDn;

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
                nueva.put("userPassword", password);
                contexto.createSubcontext(userDn, nueva);
            } else {
                List<ModificationItem> mods = new ArrayList<>();
                mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("userPassword", password)));
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
}
