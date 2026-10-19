package uy.edu.fing.grupo07.CargaUY.security;

import java.time.*;
import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless
@LocalBean
public class SesionesService {
    public record Login(String token, Acceso usuario, Instant vence) {}
    @PersistenceContext(unitName = "CargaUYPersistenceUnit") private EntityManager em;

    // Solo invocada por AutenticacionService después de validar LDAP.
    public Login emitirLdap(String uid) {
        Usuario u = em.createQuery(
            "select u from Usuario u where u.origenIdentidad='LDAP' and u.uidExterno=:uid",
            Usuario.class).setParameter("uid", uid).getResultStream().findFirst()
            .orElseThrow(() -> new ErrorAcceso(401, "Credenciales inválidas"));
        if (!u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
                && !u.getRoles().contains(TipoUsuario.AUTORIDAD))
            throw new ErrorAcceso(403, "Cuenta interna sin rol habilitado");
        return emitir(u);
    }
    // Claims ya verificados criptográficamente por GubOidcClient.
    public Login emitirGub(GubOidcClient.Identidad i) {
        Usuario u = em.createQuery(
            "select u from Usuario u where u.origenIdentidad='GUB' and u.uidExterno=:uid",
            Usuario.class).setParameter("uid", i.subject()).getResultStream().findFirst().orElse(null);
        if (u == null) {
            long existe = em.createQuery("select count(u) from Usuario u where u.ci=:ci", Long.class)
                .setParameter("ci", i.ci()).getSingleResult();
            if (existe > 0) throw new ErrorAcceso(409,
                "La CI existe: un administrador debe vincular la identidad verificada. Referencia: "+i.subject());
            if (i.nacimiento()==null) throw new ErrorAcceso(409,
                "Un administrador debe crear el perfil completo y vincular la identidad verificada. Referencia: "+i.subject());
            Ciudadano c = new Ciudadano(i.nombre(), i.mail(), i.nacimiento(), i.ci(), "{GUB}");
            c.getRoles().add(TipoUsuario.CIUDADANO);
            c.setOrigenIdentidad("GUB"); c.setUidExterno(i.subject());
            em.persist(c); em.flush(); u = c;
        }
        if (u.getCi()!=i.ci()) throw new ErrorAcceso(409,"La identidad no corresponde a la CI del perfil vinculado");
        if (u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
                || u.getRoles().contains(TipoUsuario.AUTORIDAD))
            throw new ErrorAcceso(403, "Las cuentas internas deben utilizar LDAP");
        return emitir(u);
    }
    private Login emitir(Usuario u) {
        if (!u.isActivo()) throw new ErrorAcceso(401, "Cuenta no habilitada");
        String token = Cripto.aleatorio();
        Instant vence = Instant.now().plus(Duration.ofMinutes(30));
        em.persist(new SesionAcceso(Cripto.hash(token), u, vence));
        return new Login(token, acceso(u), vence);
    }
    public Acceso validarToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new ErrorAcceso(401, "Sesión inválida");
        SesionAcceso s = em.find(SesionAcceso.class, Cripto.hash(token));
        if (s == null || !s.getVence().isAfter(Instant.now()) || !s.getUsuario().isActivo())
            throw new ErrorAcceso(401, "Sesión vencida o revocada");
        return acceso(s.getUsuario()); // Roles actuales, no una copia vieja en el token.
    }
    public void logout(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return;
        SesionAcceso s = em.find(SesionAcceso.class, Cripto.hash(token));
        if (s != null) em.remove(s);
    }
    public void revocarUsuario(int id) {
        em.createQuery("delete from SesionAcceso s where s.usuario.id=:id")
            .setParameter("id", id).executeUpdate();
    }
    @Schedule(hour="*", minute="*/15", persistent=false)
    public void limpiarVencidas() {
        em.createQuery("delete from SesionAcceso s where s.vence<=:ahora")
            .setParameter("ahora", Instant.now()).executeUpdate();
    }
    private Acceso acceso(Usuario u) {
        return new Acceso(u.getId(), u.getCi(), u.getNombre(), u.getRoles());
    }
}
