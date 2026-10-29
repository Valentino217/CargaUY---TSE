package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;

/**
 * Session Bean implementation class VehiculoEJB
 */
@Stateless
@LocalBean
public class VehiculoEJB implements VehiculoEJBLocal {
    @jakarta.ejb.EJB private uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control;

    public List<Vehiculo> listarParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarVehiculos();
        return em.createQuery("select v from Vehiculo v join fetch v.empresa e where e.activa=true "
            +"and exists(select x from VinculoEmpresa x where x.empresa=e and x.usuario.id=:id and x.responsable=true) "
            +"order by v.matricula",Vehiculo.class).setParameter("id",actor.id()).getResultList();
    }
    public List<Empresa> listarEmpresasParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarEmpresas();
        return em.createQuery("select e from Empresa e where e.activa=true and exists(select x from VinculoEmpresa x "
            +"where x.empresa=e and x.usuario.id=:id and x.responsable=true)",Empresa.class)
            .setParameter("id",actor.id()).getResultList();
    }
    public List<Vehiculo> listarVehiculosPorEmpresa(String token,int empresa) {
        control.empresa(token,empresa,false); return listarVehiculosPorEmpresa(empresa);
    }
    public Vehiculo buscar(String token,String matricula) {
        Vehiculo v=buscar(matricula);
        if(v!=null) control.empresa(token,v.getEmpresa().getNroEmpresa(),false);
        return v;
    }
    public void crear(String token,Vehiculo v) {
        if (v == null || v.getMatricula() == null || v.getMatricula().isBlank()) {
            throw new IllegalArgumentException("La matrícula del vehículo es obligatoria.");
        }
        v.setMatricula(v.getMatricula().trim().toUpperCase());
        control.empresa(token,v.getEmpresa().getNroEmpresa(),true);
        if (buscar(v.getMatricula()) != null) {
            throw new IllegalArgumentException("Ya existe un vehículo registrado con la matrícula " + v.getMatricula() + ".");
        }
        crear(v);
    }
    public void modificar(String token,Vehiculo v) {
        Vehiculo anterior=buscar(token,v.getMatricula());
        if(anterior==null) throw new IllegalArgumentException("Vehículo inexistente");
        if(tieneGuiaEnCurso(v.getMatricula())) {
            throw new IllegalStateException("No se puede editar el vehículo " + v.getMatricula() + " porque tiene una guía de viaje EN_CURSO.");
        }
        control.empresa(token,anterior.getEmpresa().getNroEmpresa(),true);
        control.empresa(token,v.getEmpresa().getNroEmpresa(),true); modificar(v);
    }
    public void eliminar(String token,Vehiculo v) {
        Vehiculo actual=buscar(token,v.getMatricula());
        if(actual==null) throw new IllegalArgumentException("Vehículo inexistente");
        control.empresa(token,actual.getEmpresa().getNroEmpresa(),true); eliminar(actual);
    }

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public VehiculoEJB() {
    }
    
    @Override
    public List<Vehiculo> listarVehiculos() {
        return em.createQuery(
            "SELECT v FROM Vehiculo v " +
            "LEFT JOIN FETCH v.empresa",
            Vehiculo.class
        ).getResultList();
    }

    @Override
    public List<Empresa> listarEmpresas() {
        return em.createQuery(
                "SELECT e FROM Empresa e ORDER BY e.razonSocial",
                Empresa.class
        ).getResultList();
    }
    
    @Override
    public void crear(Vehiculo vehiculo) {
        em.persist(vehiculo);
    }

    @Override
    public boolean tieneGuiaEnCurso(String matricula) {
        if (matricula == null || matricula.isBlank()) return false;
        Long count = em.createQuery(
                "SELECT COUNT(g) FROM GuiaDeViaje g WHERE UPPER(TRIM(g.vehiculo.matricula)) = UPPER(TRIM(:mat)) AND g.estado = :enCurso",
                Long.class
        )
        .setParameter("mat", matricula)
        .setParameter("enCurso", uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado.EN_CURSO)
        .getSingleResult();
        return count != null && count > 0L;
    }

    @Override
    public void modificar(Vehiculo vehiculo) {
        if (tieneGuiaEnCurso(vehiculo.getMatricula())) {
            throw new IllegalStateException("No se puede editar el vehículo " + vehiculo.getMatricula() + " porque tiene una guía de viaje EN_CURSO.");
        }
        em.merge(vehiculo);
    }

    @Override
    public void eliminar(Vehiculo vehiculo) {
        Long cantGuias = em.createQuery(
                "SELECT COUNT(g) FROM GuiaDeViaje g WHERE g.vehiculo.matricula = :mat",
                Long.class
        ).setParameter("mat", vehiculo.getMatricula()).getSingleResult();

        if (cantGuias != null && cantGuias > 0) {
            throw new IllegalStateException(
                "No se puede eliminar el vehículo " + vehiculo.getMatricula() +
                " porque tiene " + cantGuias + " guía(s) de viaje asociada(s). Puede suspender su habilitación en su lugar."
            );
        }
        em.remove(em.merge(vehiculo));
    }

    @Override
    public Vehiculo buscar(String matricula) {
        return em.find(Vehiculo.class, matricula);
    }

    @Override
    public List<Vehiculo> listarVehiculosPorCiudadano(int ciudadanoId) {
        return em.createQuery(
            "SELECT DISTINCT v FROM Vehiculo v " +
            "JOIN FETCH v.empresa " +
            "JOIN v.empresa e " +
            "JOIN e.ciudadanos c " +
            "WHERE c.id = :ciudadanoId " +
            "ORDER BY v.matricula",
            Vehiculo.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList();
    }
    
    @Override
    public List<Empresa> listarEmpresasPorCiudadano(int ciudadanoId) {
        return em.createQuery(
            "SELECT DISTINCT e FROM Empresa e " +
            "JOIN e.ciudadanos c " +
            "WHERE c.id = :ciudadanoId",
            Empresa.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList();
    }
    
    @Override
    public List<Vehiculo> listarVehiculosPorEmpresa(int nroEmpresa) {
        return em.createQuery(
            "SELECT v FROM Vehiculo v " +
            "JOIN FETCH v.empresa " +
            "WHERE v.empresa.nroEmpresa = :nroEmpresa " +
            "ORDER BY v.matricula",
            Vehiculo.class
        )
        .setParameter("nroEmpresa", nroEmpresa)
        .getResultList();
    }
}
