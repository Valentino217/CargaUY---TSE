package uy.edu.fing.grupo07.CargaUY.security;

import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless @LocalBean
public class IdentidadesService {
    @EJB private ControlAcceso control;
    
    @EJB private SesionesService sesiones;
    
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    
    public void vincular(String token, int id, String origen, String uid) {
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
        u.setOrigenIdentidad(origen); u.setUidExterno(uid);
        u.setPassword("{"+origen+"}"); // Eliminar copia innecesaria de credencial local.
        sesiones.revocarUsuario(id);
    }
    
    public void roles(String token, int id, Set<TipoUsuario> roles) {
        Acceso actor = control.admin(token);
        Usuario u = requerido(id);
        if (roles==null || roles.isEmpty()) throw new ErrorAcceso(400,"Al menos un rol");
        boolean internos = roles.stream().anyMatch(r -> r==TipoUsuario.ADMINISTRADOR||r==TipoUsuario.AUTORIDAD);
        boolean externos = roles.stream().anyMatch(r -> r!=TipoUsuario.ADMINISTRADOR&&r!=TipoUsuario.AUTORIDAD);
        if (internos && externos) throw new ErrorAcceso(409,"No mezclar cuentas LDAP y gub.uy");
        if (actor.id()==id && !roles.contains(TipoUsuario.ADMINISTRADOR))
            throw new ErrorAcceso(409,"No quitar tu propio rol administrador");
        if ("LDAP".equals(u.getOrigenIdentidad()) && !internos
                || "GUB".equals(u.getOrigenIdentidad()) && internos)
            throw new ErrorAcceso(409,"Los roles no coinciden con el proveedor de la cuenta");
        u.getRoles().clear(); u.getRoles().addAll(roles); sesiones.revocarUsuario(id);
    }
    
    public void vinculo(String token, int empresa, int usuario, boolean responsable, boolean chofer) {
        guardarVinculo(token,empresa,usuario,responsable,chofer);
    }

    /** Modifica una sola función sin quitar la otra; ambas se pueden ejercer a la vez. */
    public void funcionEmpresa(String token,int empresa,int usuario,TipoUsuario funcion,boolean habilitar) {
        if(funcion!=TipoUsuario.CIUDADANO && funcion!=TipoUsuario.CHOFER)
            throw new ErrorAcceso(400,"Función de empresa inválida");
        guardarVinculo(token,empresa,usuario,funcion==TipoUsuario.CIUDADANO?habilitar:null,
            funcion==TipoUsuario.CHOFER?habilitar:null);
    }

    private void guardarVinculo(String token,int empresa,int usuario,Boolean responsable,Boolean chofer) {
        control.exigir(token,TipoUsuario.ADMINISTRADOR,TipoUsuario.FUNCIONARIO);
        Empresa e = em.find(Empresa.class,empresa); Usuario u = requerido(usuario);
        if (e==null || !e.isActiva() || !u.isActivo()) throw new ErrorAcceso(409,"Cuenta o empresa inactiva");
        if (Boolean.TRUE.equals(responsable) && !u.getRoles().contains(TipoUsuario.CIUDADANO)
                || Boolean.TRUE.equals(chofer) && !u.getRoles().contains(TipoUsuario.CHOFER))
            throw new ErrorAcceso(409,"El usuario no tiene el rol requerido");
        em.lock(e,LockModeType.PESSIMISTIC_WRITE);
        VinculoEmpresa v = em.find(VinculoEmpresa.class,VinculoEmpresa.clave(empresa,usuario));
        if (v==null) { v=new VinculoEmpresa(u,e); em.persist(v); }
        if(responsable!=null) v.setResponsable(responsable);
        if(chofer!=null) v.setChofer(chofer);
        // false/false conserva el vínculo sin permisos, útil para trazabilidad.
    }
    
    public record VinculoVista(int usuario,String nombre,boolean responsable,boolean chofer){
    }
    
    public java.util.List<VinculoVista> listar(String token,int empresa){
        control.empresa(token,empresa,false);
        return em.createQuery("select v from VinculoEmpresa v where v.empresa.nroEmpresa=:empresa order by v.usuario.id",VinculoEmpresa.class)
            .setParameter("empresa",empresa).setMaxResults(100).getResultList().stream()
            .map(v->new VinculoVista(v.getUsuario().getId(),v.getUsuario().getNombre(),v.isResponsable(),v.isChofer())).toList();
    }
    
    private Usuario requerido(int id) {
        Usuario u=em.find(Usuario.class,id);
        if(u==null) throw new ErrorAcceso(404,"Usuario inexistente");
        return u;
    }
}
