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

	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;
    /**
     * Default constructor. 
     */
    public VehiculoEJB() {
        // TODO Auto-generated constructor stub
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
    public void modificar(Vehiculo vehiculo) {
        em.merge(vehiculo);
    }

    @Override
    public void eliminar(Vehiculo vehiculo) {
        em.remove(em.merge(vehiculo));
    }

    @Override
    public Vehiculo buscar(String matricula) {
        return em.find(Vehiculo.class, matricula);
    }
}
