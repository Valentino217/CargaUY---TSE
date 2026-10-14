package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;

/**
 * Session Bean implementation class PNCEJB
 */
@Stateless
@LocalBean
public class PNCEJB implements PNCEJBLocal {

	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;
    /**
     * Default constructor. 
     */
    public PNCEJB() {
        // TODO Auto-generated constructor stub
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
