package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Chofer;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;

/**
 * Session Bean implementation class GuiaDeViajeEJB
 */
@Stateless
@LocalBean
public class GuiaDeViajeEJB implements GuiaDeViajeEJBLocal {

	@PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;
	/**
     * Default constructor. 
     */
    public GuiaDeViajeEJB() {
        // TODO Auto-generated constructor stub
    }
    
    @Override
    public void crear(GuiaDeViaje guia) {
        em.persist(guia);
    }

    @Override
    public void modificar(GuiaDeViaje guia) {
    	//NO SE PUEDEN MODIFICAR GUIAS EN OTRO ESTADO DIFERENTE AL SIN_INCIAR
    	 GuiaDeViaje existente = em.find(
    		        GuiaDeViaje.class, guia.getId()
    	  );

          if (existente == null) {
    		        throw new IllegalArgumentException(
    		            "La guía de viaje no existe."
    	   );
    	   }

    	   if (existente.getEstado() != TipoEstado.SIN_INICIAR) {
    		        throw new IllegalStateException(
    		            "Solo se pueden modificar guías en estado SIN_INICIAR."
    		        );
    		}

    		existente.setFecha(guia.getFecha());
    		existente.setOrigen(guia.getOrigen());
    		existente.setDestino(guia.getDestino());
    		existente.setRubroCliente(guia.getRubroCliente());
    		existente.setVolumenCarga(guia.getVolumenCarga());
            em.merge(guia);
    }

    @Override
    public void eliminar(GuiaDeViaje guia) {
    	GuiaDeViaje existente = em.find(
    	        GuiaDeViaje.class, guia.getId()
    	    );

    	    if (existente == null) {
    	        throw new IllegalArgumentException(
    	            "La guía de viaje no existe."
    	        );
    	    }

    	    // No se pueden eliminar guías en curso o finalizadas.
    	    if (existente.getEstado() != TipoEstado.SIN_INICIAR) {
    	        throw new IllegalStateException(
    	            "No se pueden dar de baja guías en estado EN_CURSO o FINALIZADO."
    	        );
    	    }

    	    em.remove(existente);
    }
    
    
    //lista los choferes que trabajan en la empresa
    @Override
    public List<Chofer> listarChoferesPorEmpresa(int nroEmpresa) {
        return em.createQuery(
            "SELECT c FROM Empresa e " +
            "JOIN e.choferes c " +
            "WHERE e.nroEmpresa = :nroEmpresa " +
            "ORDER BY c.nombre",
            Chofer.class
        )
        .setParameter("nroEmpresa", nroEmpresa)
        .getResultList();
    }
    
    //lista todas las guias en el sistema
    @Override
    public List<GuiaDeViaje> listarGuias() {
        return em.createQuery(
            "SELECT g FROM GuiaDeViaje g " +
            "LEFT JOIN FETCH g.empresa " +
            "LEFT JOIN FETCH g.vehiculo " +
            "LEFT JOIN FETCH g.chofer " +
            "ORDER BY g.fecha DESC",
            GuiaDeViaje.class
        ).getResultList();
    }

    //buscar guia por id
    @Override
    public GuiaDeViaje buscarGuias(Long id) {
        return em.find(GuiaDeViaje.class, id);
    }

}
