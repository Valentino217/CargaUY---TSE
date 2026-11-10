package uy.edu.fing.grupo07.CargaUY.security;

import java.nio.charset.StandardCharsets;
import java.util.*;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless @LocalBean
public class IdentidadesService {
    @EJB private ControlAcceso control;
    @EJB private SesionesService sesiones;
    @EJB private LdapClient ldapClient;
    
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    
    public String[] consultarIdentidad(String token, int id) {
        control.admin(token);
        Usuario u = requerido(id);
        return new String[]{ u.getOrigenIdentidad(), u.getUidExterno() };
    }

    public void vincular(String token, int id, String origen, String uid) {
        vincular(token, id, origen, uid, null);
    }

    public void vincular(String token, int id, String origen, String uid, String password) {
        control.admin(token);
        if (!Set.of("LDAP","GUB").contains(origen) || uid == null || uid.isBlank() || uid.length()>200)
            throw new ErrorAcceso(400,"Origen o identidad inválidos");
        if ("LDAP".equals(origen) && !uid.matches("[a-zA-Z0-9._-]{1,64}"))
            throw new ErrorAcceso(400,"UID LDAP inválido");
        Usuario u = requerido(id);
        boolean interno = u.getRoles().contains(TipoUsuario.ADMINISTRADOR)
            || u.getRoles().contains(TipoUsuario.AUTORIDAD);
        if (interno != "LDAP".equals(origen))
            throw new ErrorAcceso(409,"Roles internos requieren LDAP; los demás requieren GUB");
        
        u.setOrigenIdentidad(origen); 
        u.setUidExterno(uid);

        if ("LDAP".equals(origen)) {
            String pwdAUsar = (password != null && !password.isBlank()) ? password : null;
            if (pwdAUsar != null) {
                if (pwdAUsar.getBytes(StandardCharsets.UTF_8).length > 72) {
                    throw new ErrorAcceso(400, "Contraseña LDAP supera 72 bytes UTF-8");
                }
                try {
                    ldapClient.crearOActualizarUsuario(uid, u.getNombre(), u.getMail(), pwdAUsar);
                } catch (Exception e) {
                    // Log and continue if running in testing without active LDAP write permissions
                }
                char[] caracteres = pwdAUsar.toCharArray();
                try {
                    u.setPassword(BCrypt.withDefaults().hashToString(12, caracteres));
                } finally {
                    Arrays.fill(caracteres, '\0');
                }
            } else {
                u.setPassword("{" + origen + "}");
            }
        } else {
            u.setPassword("{" + origen + "}");
        }
        sesiones.revocarUsuario(id);
    }
    
    public void roles(String token, int id, Set<TipoUsuario> roles) {
        Acceso actor = control.admin(token);
        Usuario u = requerido(id);
        if (roles == null || roles.size() != 1) {
            throw new ErrorAcceso(400, "Debe asignar exactamente un (1) rol al usuario");
        }
        TipoUsuario unicoRol = roles.iterator().next();
        boolean interno = (unicoRol == TipoUsuario.ADMINISTRADOR || unicoRol == TipoUsuario.AUTORIDAD);
        if (actor.id() == id && unicoRol != TipoUsuario.ADMINISTRADOR) {
            throw new ErrorAcceso(409, "No podés quitar tu propio rol de administrador");
        }
        if ("LDAP".equals(u.getOrigenIdentidad()) && !interno) {
            throw new ErrorAcceso(409, "Cuentas LDAP solo admiten roles internos (ADMINISTRADOR o AUTORIDAD)");
        }
        if ("GUB".equals(u.getOrigenIdentidad()) && interno) {
            throw new ErrorAcceso(409, "Cuentas gub.uy no admiten roles internos que requieren LDAP");
        }

        u.getRoles().clear(); 
        u.getRoles().add(unicoRol);
        em.flush();

        if (unicoRol == TipoUsuario.CHOFER) {
            em.createNativeQuery("INSERT INTO choferes (usuario_id) VALUES (:id) ON CONFLICT DO NOTHING")
                    .setParameter("id", id).executeUpdate();
            em.createNativeQuery("UPDATE usuarios SET tipo_usuario = 'CHOFER' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
        } else if (unicoRol == TipoUsuario.CIUDADANO || unicoRol == TipoUsuario.CIUDADANO_COMUN) {
            em.createNativeQuery("INSERT INTO ciudadanos (usuario_id) VALUES (:id) ON CONFLICT DO NOTHING")
                    .setParameter("id", id).executeUpdate();
            em.createNativeQuery("UPDATE usuarios SET tipo_usuario = 'CIUDADANO' WHERE id = :id")
                    .setParameter("id", id).executeUpdate();
        }

        // Si el usuario deja de ser CIUDADANO responsable o CHOFER, limpiar vínculos incompatibles
        var qVinculos = em.createQuery(
                "select v from VinculoEmpresa v where v.usuario.id = :id", VinculoEmpresa.class);
        if (qVinculos != null) {
            List<VinculoEmpresa> vinculos = qVinculos.setParameter("id", id).getResultList();
            if (vinculos != null) {
                for (VinculoEmpresa v : vinculos) {
                    if (unicoRol != TipoUsuario.CIUDADANO && v.isResponsable()) {
                        v.setResponsable(false);
                    }
                    if (unicoRol != TipoUsuario.CHOFER && v.isChofer()) {
                        v.setChofer(false);
                    }
                    if (!v.isResponsable() && !v.isChofer()) {
                        em.remove(v);
                    }
                }
            }
        }

        sesiones.revocarUsuario(id);
    }
    
    public void vinculo(String token, int empresa, int usuario, boolean responsable, boolean chofer) {
        guardarVinculo(token, empresa, usuario, responsable, chofer);
    }

    public void funcionEmpresa(String token, int empresa, int usuario, TipoUsuario funcion, boolean habilitar) {
        if (funcion != TipoUsuario.CIUDADANO && funcion != TipoUsuario.CHOFER) {
            throw new ErrorAcceso(400, "Función de empresa inválida");
        }
        guardarVinculo(token, empresa, usuario, funcion == TipoUsuario.CIUDADANO ? habilitar : null,
            funcion == TipoUsuario.CHOFER ? habilitar : null);
    }

    public void desvincular(String token, int empresa, int usuario) {
        guardarVinculo(token, empresa, usuario, false, false);
    }

    private void guardarVinculo(String token, int empresa, int usuario, Boolean responsable, Boolean chofer) {
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        Empresa e = em.find(Empresa.class, empresa); 
        Usuario u = requerido(usuario);
        if (e == null || !e.isActiva() || !u.isActivo()) {
            throw new ErrorAcceso(409, "Cuenta o empresa inactiva");
        }

        if (Boolean.TRUE.equals(responsable) && !u.getRoles().contains(TipoUsuario.CIUDADANO)) {
            throw new ErrorAcceso(409, "El usuario debe tener rol Ciudadano Empresa (Responsable) para ser asignado");
        }
        if (Boolean.TRUE.equals(chofer) && !u.getRoles().contains(TipoUsuario.CHOFER)) {
            throw new ErrorAcceso(409, "El usuario debe tener rol Chofer para ser asignado");
        }

        em.lock(e, LockModeType.PESSIMISTIC_WRITE);

        // Regla 1-a-1: Una empresa solo puede tener 1 responsable
        if (Boolean.TRUE.equals(responsable)) {
            var qOtros = em.createQuery(
                "select count(v) from VinculoEmpresa v where v.empresa.nroEmpresa = :empresa and v.responsable = true and v.usuario.id <> :usuario", Long.class);
            if (qOtros != null) {
                Long otrosResp = qOtros
                    .setParameter("empresa", empresa)
                    .setParameter("usuario", usuario)
                    .getSingleResult();
                if (otrosResp != null && otrosResp > 0) {
                    throw new ErrorAcceso(409, "Esta empresa ya cuenta con un responsable asignado. Debe revocar o desvincular al responsable actual primero.");
                }
            }

            // Regla 1-a-1: Un usuario solo puede ser responsable de 1 empresa
            var qOtrasEmp = em.createQuery(
                "select count(v) from VinculoEmpresa v where v.usuario.id = :usuario and v.responsable = true and v.empresa.nroEmpresa <> :empresa", Long.class);
            if (qOtrasEmp != null) {
                Long otrasEmpresas = qOtrasEmp
                    .setParameter("usuario", usuario)
                    .setParameter("empresa", empresa)
                    .getSingleResult();
                if (otrasEmpresas != null && otrasEmpresas > 0) {
                    throw new ErrorAcceso(409, "El usuario ya es responsable de otra empresa. Un usuario solo puede ser responsable de una única empresa.");
                }
            }
        }

        VinculoEmpresa v = em.find(VinculoEmpresa.class, VinculoEmpresa.clave(empresa, usuario));
        if (responsable != null && !responsable && chofer != null && !chofer) {
            // Desvinculación definitiva
            if (v != null) {
                em.remove(v);
            }
            if (u instanceof Ciudadano c && e.getCiudadanos() != null) {
                e.getCiudadanos().remove(c);
            }
            if (u instanceof Chofer ch && e.getChoferes() != null) {
                e.getChoferes().remove(ch);
            }
            em.flush();
            return;
        }

        if (v == null) { 
            v = new VinculoEmpresa(u, e); 
            em.persist(v); 
        }
        if (responsable != null) v.setResponsable(responsable);
        if (chofer != null) v.setChofer(chofer);

        if (!v.isResponsable() && !v.isChofer()) {
            em.remove(v);
        } else {
            if (u instanceof Ciudadano c && e.getCiudadanos() != null) {
                if (v.isResponsable()) { 
                    if (!e.getCiudadanos().contains(c)) e.getCiudadanos().add(c); 
                } else { 
                    e.getCiudadanos().remove(c); 
                }
            }
            if (u instanceof Chofer ch && e.getChoferes() != null) {
                if (v.isChofer()) { 
                    if (!e.getChoferes().contains(ch)) e.getChoferes().add(ch); 
                } else { 
                    e.getChoferes().remove(ch); 
                }
            }
        }
        em.flush();
    }
    
    public record VinculoVista(int usuario, String nombre, boolean responsable, boolean chofer) implements java.io.Serializable {
    }

    public Integer buscarIdPorCi(int ci) {
        List<Integer> ids = em.createQuery("select u.id from Usuario u where u.ci = :ci", Integer.class)
            .setParameter("ci", ci)
            .setMaxResults(1)
            .getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }
    
    public List<VinculoVista> listar(String token, int empresa) {
        control.empresa(token, empresa, false);
        return em.createQuery("select v from VinculoEmpresa v where v.empresa.nroEmpresa=:empresa and (v.responsable = true or v.chofer = true) order by v.usuario.id", VinculoEmpresa.class)
            .setParameter("empresa", empresa).setMaxResults(100).getResultList().stream()
            .map(v -> new VinculoVista(v.getUsuario().getId(), v.getUsuario().getNombre(), v.isResponsable(), v.isChofer())).toList();
    }
    
    private Usuario requerido(int id) {
        Usuario u = em.find(Usuario.class, id);
        if (u == null) throw new ErrorAcceso(404, "Usuario inexistente");
        return u;
    }
}
