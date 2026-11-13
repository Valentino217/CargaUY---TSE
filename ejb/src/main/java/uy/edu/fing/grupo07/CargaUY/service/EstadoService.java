package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.annotation.Resource;
import jakarta.ejb.*;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.persistence.*;
@Stateless @LocalBean @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EstadoService {
    @PersistenceContext(unitName="CargaUYPersistenceUnit")private EntityManager em;
    @Resource(lookup="java:/ConnectionFactory")private ConnectionFactory connectionFactory;
    public boolean listo(){
        try{return ((Number)em.createNativeQuery("select 1").getSingleResult()).intValue()==1;}
        catch(RuntimeException e){return false;}
    }
    public boolean jmsListo(){
        if(connectionFactory==null) return false;
        try(JMSContext jmsContext=connectionFactory.createContext()){
            return jmsContext!=null;
        } catch(Exception e){
            return false;
        }
    }
}