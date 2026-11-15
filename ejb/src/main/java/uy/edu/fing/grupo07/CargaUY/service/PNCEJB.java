package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;

/**
 * Session Bean implementation class PNCEJB
 */
@Stateless
@LocalBean
public class PNCEJB implements PNCEJBLocal {

	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

	@EJB
	private ControlAcceso control;
    /**
     * Default constructor. 
     */
    public PNCEJB() {
        // TODO Auto-generated constructor stub
    }

    private void verificarControl() {
        if (control == null) {
            throw new IllegalStateException("ControlAcceso no disponible");
        }
    }

    private int resolverNroEmpresa(PNC pnc) {
        Vehiculo ref = pnc != null ? pnc.getVehiculo() : null;
        if (ref == null || ref.getMatricula() == null || ref.getMatricula().isBlank()) {
            throw new IllegalArgumentException("El certificado PNC debe estar asociado a un vehículo.");
        }
        if (ref.getEmpresa() != null) {
            return ref.getEmpresa().getNroEmpresa();
        }
        Vehiculo persistido = em.find(Vehiculo.class, ref.getMatricula().trim().toUpperCase(java.util.Locale.ROOT));
        if (persistido == null || persistido.getEmpresa() == null) {
            throw new IllegalArgumentException("Vehículo o empresa inexistente para el certificado PNC.");
        }
        return persistido.getEmpresa().getNroEmpresa();
    }

    @Override
    public void crear(String token, PNC pnc) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        resolverNroEmpresa(pnc);
        crear(pnc);
    }

    @Override
    public void modificar(String token, PNC pnc) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        if (pnc == null) {
            throw new IllegalArgumentException("El certificado PNC es obligatorio.");
        }
        if (pnc.getNro() > 0) {
            PNC existente = em.find(PNC.class, pnc.getNro());
            if (existente == null) {
                throw new IllegalArgumentException("Certificado PNC inexistente.");
            }
            resolverNroEmpresa(existente);
        }
        resolverNroEmpresa(pnc);
        modificar(pnc);
    }

    @Override
    public void eliminar(String token, PNC pnc) {
        verificarControl();
        control.exigir(token, TipoUsuario.ADMINISTRADOR, TipoUsuario.FUNCIONARIO);
        if (pnc == null) {
            throw new IllegalArgumentException("El certificado PNC es obligatorio.");
        }
        PNC objetivo = pnc.getNro() > 0 ? em.find(PNC.class, pnc.getNro()) : null;
        if (objetivo == null) {
            if (pnc.getNro() > 0) {
                throw new IllegalArgumentException("Certificado PNC inexistente.");
            }
            objetivo = pnc;
        }
        resolverNroEmpresa(objetivo);
        eliminar(objetivo);
    }

    @Override
    public List<PNC> listarPNCs(String token) {
        verificarControl();
        Acceso actor = control.exigir(token,
                TipoUsuario.ADMINISTRADOR,
                TipoUsuario.FUNCIONARIO,
                TipoUsuario.AUTORIDAD,
                TipoUsuario.CIUDADANO);
        if (actor.tiene(TipoUsuario.ADMINISTRADOR)
                || actor.tiene(TipoUsuario.FUNCIONARIO)
                || actor.tiene(TipoUsuario.AUTORIDAD)) {
            return listarPNCs();
        }
        return em.createQuery(
                "SELECT p FROM PNC p LEFT JOIN FETCH p.vehiculo WHERE p.vehiculo.empresa.activa = true "
                        + "AND EXISTS (SELECT x FROM VinculoEmpresa x WHERE x.empresa = p.vehiculo.empresa "
                        + "AND x.usuario.id = :id AND x.responsable = true)",
                PNC.class)
                .setParameter("id", actor.id())
                .getResultList();
    }
    
    @Override
    public void crear(PNC pnc) {
        em.persist(pnc);
    }

    @Override
    public void modificar(PNC pnc) {
        em.merge(pnc);
    }

    @Override
    public void eliminar(PNC pnc) {
        em.remove(em.merge(pnc));
    }
    
    @Override
    public List<PNC> listarPNCs() {
        return em.createQuery("SELECT p FROM PNC p LEFT JOIN FETCH p.vehiculo", PNC.class)
                 .getResultList();
    }

}
