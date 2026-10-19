package uy.edu.fing.grupo07.CargaUY.security;

import java.util.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

@Stateless @LocalBean
public class ControlAcceso {
    @EJB private SesionesService sesiones;
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    public Acceso exigir(String token, TipoUsuario... roles) {
        Acceso a = sesiones.validarToken(token);
        if (roles.length > 0 && Arrays.stream(roles).noneMatch(a::tiene))
            throw new ErrorAcceso(403, "Operación no permitida para este rol");
        return a;
    }
    public Acceso admin(String token) { return exigir(token, TipoUsuario.ADMINISTRADOR); }
    public Acceso empresa(String token, int numero, boolean editar) {
        Acceso a = exigir(token);
        if (a.tiene(TipoUsuario.ADMINISTRADOR) || a.tiene(TipoUsuario.FUNCIONARIO)
                || !editar && a.tiene(TipoUsuario.AUTORIDAD)) return a;
        Empresa e = em.find(Empresa.class, numero);
        VinculoEmpresa v = em.find(VinculoEmpresa.class, VinculoEmpresa.clave(numero, a.id()));
        if (!a.tiene(TipoUsuario.CIUDADANO) || e == null || !e.isActiva() || v == null || !v.isResponsable())
            throw new ErrorAcceso(403, "Sin acceso a esa empresa");
        return a;
    }
    public void choferAsignable(String token, int numero, int usuario) {
        empresa(token, numero, true);
        Usuario u = em.find(Usuario.class, usuario);
        VinculoEmpresa v = em.find(VinculoEmpresa.class, VinculoEmpresa.clave(numero, usuario));
        if (u == null || !u.isActivo() || !u.getRoles().contains(TipoUsuario.CHOFER)
                || v == null || !v.isChofer())
            throw new ErrorAcceso(409, "El chofer debe estar activo y vinculado a la empresa");
    }
}