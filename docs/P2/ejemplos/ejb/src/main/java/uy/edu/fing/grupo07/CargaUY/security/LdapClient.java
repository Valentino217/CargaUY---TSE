package uy.edu.fing.grupo07.CargaUY.security;

import java.net.URI;
import java.util.Hashtable;
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
        boolean local = ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
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
}
