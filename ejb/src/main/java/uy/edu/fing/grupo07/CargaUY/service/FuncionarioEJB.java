package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;

/**
 * Session Bean implementation class FuncionarioEJB
 */
@Stateless
@LocalBean
public class FuncionarioEJB implements FuncionarioEJBLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public FuncionarioEJB() {
    }

    @Override
    public void crear(Funcionario funcionario) {
        em.persist(funcionario);
    }
}
