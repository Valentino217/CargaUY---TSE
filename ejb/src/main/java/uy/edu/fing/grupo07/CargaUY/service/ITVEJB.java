package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

/**
 * Session Bean implementation class ITVEJB
 */
@Stateless
@LocalBean
public class ITVEJB implements ITVEJBLocal {
	
	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

	@EJB
	private ControlAcceso control;

    /**
     * Default constructor. 
     */
    public ITVEJB() {
        // TODO Auto-generated constructor stub
    }

    private void verificarControl() {
        if (control == null) {
            throw new IllegalStateException("ControlAcceso no disponible");
        }
    }

    private int resolverNroEmpresa(ITV itv) {
        Vehiculo ref = itv != null ? itv.getVehiculo() : null;
        if (ref == null || ref.getMatricula() == null || ref.getMatricula().isBlank()) {
            throw new IllegalArgumentException("El certificado ITV debe estar asociado a un vehículo.");
        }
        if (ref.getEmpresa() != null) {
            return ref.getEmpresa().getNroEmpresa();
        }
        Vehiculo persistido = em.find(Vehiculo.class, ref.getMatricula().trim().toUpperCase(java.util.Locale.ROOT));
        if (persistido == null || persistido.getEmpresa() == null) {
            throw new IllegalArgumentException("Vehículo o empresa inexistente para el certificado ITV.");
        }
        return persistido.getEmpresa().getNroEmpresa();
    }

    @Override
    public void crear(String token, ITV itv) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        resolverNroEmpresa(itv);
        crear(itv);
    }

    @Override
    public void modificar(String token, ITV itv) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        if (itv == null) {
            throw new IllegalArgumentException("El certificado ITV es obligatorio.");
        }
        if (itv.getNroCertificado() > 0) {
            ITV existente = em.find(ITV.class, itv.getNroCertificado());
            if (existente == null) {
                throw new IllegalArgumentException("Certificado ITV inexistente.");
            }
            resolverNroEmpresa(existente);
        }
        resolverNroEmpresa(itv);
        modificar(itv);
    }

    @Override
    public void eliminar(String token, ITV itv) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        if (itv == null) {
            throw new IllegalArgumentException("El certificado ITV es obligatorio.");
        }
        ITV objetivo = itv.getNroCertificado() > 0 ? em.find(ITV.class, itv.getNroCertificado()) : null;
        if (objetivo == null) {
            if (itv.getNroCertificado() > 0) {
                throw new IllegalArgumentException("Certificado ITV inexistente.");
            }
            objetivo = itv;
        }
        resolverNroEmpresa(objetivo);
        eliminar(objetivo);
    }

    @Override
    public List<ITV> listarITVs(String token) {
        verificarControl();
        Acceso actor = control.exigir(token,
                TipoUsuario.ADMINISTRADOR,
                TipoUsuario.FUNCIONARIO,
                TipoUsuario.AUTORIDAD,
                TipoUsuario.CIUDADANO);
        if (actor.tiene(TipoUsuario.ADMINISTRADOR)
                || actor.tiene(TipoUsuario.FUNCIONARIO)
                || actor.tiene(TipoUsuario.AUTORIDAD)) {
            return listarITVs();
        }
        return em.createQuery(
                "SELECT t FROM ITV t LEFT JOIN FETCH t.vehiculo WHERE t.vehiculo.empresa.activa = true "
                        + "AND EXISTS (SELECT x FROM VinculoEmpresa x WHERE x.empresa = t.vehiculo.empresa "
                        + "AND x.usuario.id = :id AND x.responsable = true)",
                ITV.class)
                .setParameter("id", actor.id())
                .getResultList();
    }
    
    @Override
    public void crear(ITV itv) {
        em.persist(itv);
    }

    @Override
    public void modificar(ITV itv) {
        em.merge(itv);
    }

    @Override
    public void eliminar(ITV itv) {
        em.remove(em.merge(itv));
    }
    
    @Override
    public List<ITV> listarITVs() {
        return em.createQuery("SELECT t FROM ITV t LEFT JOIN FETCH t.vehiculo", ITV.class)
                 .getResultList();
    }
}
