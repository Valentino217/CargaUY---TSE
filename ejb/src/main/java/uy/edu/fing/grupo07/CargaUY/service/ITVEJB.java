package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;

/**
 * Session Bean implementation class ITVEJB
 */
@Stateless
@LocalBean
public class ITVEJB implements ITVEJBLocal {
	
	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    /**
     * Default constructor. 
     */
    public ITVEJB() {
        // TODO Auto-generated constructor stub
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
