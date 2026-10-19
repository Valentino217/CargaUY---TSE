package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import jakarta.persistence.*;
@Stateless @LocalBean @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EstadoService {
    @PersistenceContext(unitName="CargaUYPersistenceUnit")private EntityManager em;
    public boolean listo(){
        try{return ((Number)em.createNativeQuery("select 1").getSingleResult()).intValue()==1;}
        catch(RuntimeException e){return false;}
    }
}
