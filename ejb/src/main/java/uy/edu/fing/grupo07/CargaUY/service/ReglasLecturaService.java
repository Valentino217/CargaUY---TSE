package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ReglaFiscalizacion;

@Stateless @LocalBean
public class ReglasLecturaService {
    public record Snapshot(int cargaKg,double desvioKm,long version){}
    @PersistenceContext(unitName="CargaUYPersistenceUnit") private EntityManager em;
    // Solo lectura interna para el motor; no exponer como endpoint público.
    public Snapshot actuales(){
        ReglaFiscalizacion r=em.find(ReglaFiscalizacion.class,1,
            java.util.Map.of("jakarta.persistence.cache.retrieveMode",CacheRetrieveMode.BYPASS));
        if(r==null)throw new ErrorNegocio("Regla sin configurar");
        em.refresh(r);
        return new Snapshot(r.getUmbralCarga(),r.getUmbralTracking(),r.getVersion());
    }
}