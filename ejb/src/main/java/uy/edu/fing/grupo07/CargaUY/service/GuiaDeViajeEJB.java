package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;

/**
 * Session Bean implementation class GuiaDeViajeEJB
 */
@Stateless
@LocalBean
public class GuiaDeViajeEJB implements GuiaDeViajeEJBLocal {
    @jakarta.ejb.EJB private uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control;
    @jakarta.ejb.EJB private uy.edu.fing.grupo07.CargaUY.jms.FiscalizacionProducerLocal fiscalizacionProducer;
    public List<GuiaDeViaje> listarParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarGuias();
        return em.createQuery("select g from GuiaDeViaje g join fetch g.empresa e left join fetch g.vehiculo "
            +"left join fetch g.chofer where e.activa=true and exists(select v from VinculoEmpresa v "
            +"where v.empresa=e and v.usuario.id=:id and v.responsable=true) order by g.fecha desc",GuiaDeViaje.class)
            .setParameter("id",actor.id()).getResultList();
    }
    public List<Usuario> listarChoferesPorEmpresa(String token,int empresa) {
        control.empresa(token,empresa,false); return listarChoferesPorEmpresa(empresa);
    }
    public GuiaDeViaje buscarGuias(String token,Long id) {
        GuiaDeViaje g=buscarGuias(id);
        if(g!=null) control.empresa(token,g.getEmpresa().getNroEmpresa(),false);
        return g;
    }
    private void validarAsignacion(String token,GuiaDeViaje g) {
        control.empresa(token,g.getEmpresa().getNroEmpresa(),true);
        if(g.getChofer()!=null) control.choferAsignable(token,g.getEmpresa().getNroEmpresa(),g.getChofer().getId());
        if(g.getVehiculo()!=null) {
            Vehiculo v = em.find(Vehiculo.class, g.getVehiculo().getMatricula());
            if (v == null) {
                v = g.getVehiculo();
            }
            if(v.getEmpresa()!=null && g.getEmpresa()!=null && v.getEmpresa().getNroEmpresa()!=g.getEmpresa().getNroEmpresa())
                throw new IllegalArgumentException("El vehículo no pertenece a la empresa de la guía");
            if (!v.isEstadoHabilitacion()) {
                throw new IllegalArgumentException("El vehículo " + v.getMatricula() + " se encuentra suspendido y no puede ser asignado a guías de viaje.");
            }
        }
    }
    public void crear(String token,GuiaDeViaje g) { validarAsignacion(token,g); crear(g); }
    public void modificar(String token,GuiaDeViaje g) {
        GuiaDeViaje anterior=buscarGuias(token,g.getId());
        if(anterior==null) throw new IllegalArgumentException("Guía inexistente");
        control.empresa(token,anterior.getEmpresa().getNroEmpresa(),true);
        validarAsignacion(token,g); modificar(g);
    }
    public void eliminar(String token,GuiaDeViaje g) {
        GuiaDeViaje actual=buscarGuias(token,g.getId());
        if(actual==null) throw new IllegalArgumentException("Guía inexistente");
        control.empresa(token,actual.getEmpresa().getNroEmpresa(),true); eliminar(actual);
    }

    @Override
    public void avanzarEstadoAdmin(String token, Long guiaId) {
        control.admin(token);
        if (guiaId == null) {
            throw new IllegalArgumentException("El ID de guía es obligatorio.");
        }
        GuiaDeViaje guia = em.find(GuiaDeViaje.class, guiaId);
        if (guia == null) {
            throw new IllegalArgumentException("Guía de viaje inexistente: #" + guiaId);
        }
        if (guia.getEstado() == TipoEstado.SIN_INICIAR) {
            if (guia.getVehiculo() != null) {
                Vehiculo v = em.find(Vehiculo.class, guia.getVehiculo().getMatricula());
                if (v == null) {
                    v = guia.getVehiculo();
                }
                if (!v.isEstadoHabilitacion()) {
                    throw new IllegalStateException("No se puede iniciar el viaje: el vehículo " + v.getMatricula() + " se encuentra suspendido.");
                }
            }
            guia.setEstado(TipoEstado.EN_CURSO);
            em.flush();
            if (fiscalizacionProducer != null) {
                fiscalizacionProducer.solicitarEvaluacion(guia.getId());
            }
        } else if (guia.getEstado() == TipoEstado.EN_CURSO) {
            guia.setEstado(TipoEstado.FINALIZADO);
            em.flush();
        } else if (guia.getEstado() == TipoEstado.FINALIZADO) {
            throw new IllegalStateException("La guía de viaje #" + guiaId + " ya se encuentra FINALIZADA.");
        }
    }

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
        if (guia.getVehiculo() != null) {
            Vehiculo v = em.find(Vehiculo.class, guia.getVehiculo().getMatricula());
            if (v == null) {
                v = guia.getVehiculo();
            }
            if (!v.isEstadoHabilitacion()) {
                throw new IllegalArgumentException("El vehículo " + v.getMatricula() + " se encuentra suspendido y no puede ser asignado a guías de viaje.");
            }
        }
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

        if (guia.getVehiculo() != null) {
            Vehiculo v = em.find(Vehiculo.class, guia.getVehiculo().getMatricula());
            if (v == null) {
                v = guia.getVehiculo();
            }
            if (!v.isEstadoHabilitacion()) {
                throw new IllegalArgumentException("El vehículo " + v.getMatricula() + " se encuentra suspendido y no puede ser asignado a guías de viaje.");
            }
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
    public List<Usuario> listarChoferesPorEmpresa(int nroEmpresa) {
        return em.createQuery(
            "select v.usuario from VinculoEmpresa v where v.empresa.nroEmpresa=:nroEmpresa "
            +"and v.empresa.activa=true and v.chofer=true and v.usuario.activo=true "
            +"and :rol member of v.usuario.roles order by v.usuario.nombre",
            Usuario.class
        )
        .setParameter("nroEmpresa", nroEmpresa)
        .setParameter("rol",uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CHOFER)
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
    
 // Lista las guías de las empresas asociadas al ciudadano
    @Override
    public List<GuiaDeViaje> listarGuiasPorCiudadano(int ciudadanoId) {
        return em.createQuery(
            "SELECT DISTINCT g FROM GuiaDeViaje g " +
            "JOIN FETCH g.empresa " +
            "LEFT JOIN FETCH g.vehiculo " +
            "LEFT JOIN FETCH g.chofer " +
            "WHERE g.empresa IN (" +
            "    SELECT e FROM Empresa e " +
            "    JOIN e.ciudadanos c " +
            "    WHERE c.id = :ciudadanoId" +
            ") " +
            "ORDER BY g.fecha DESC",
            GuiaDeViaje.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList();
    }

    //buscar guia por id
    @Override
    public GuiaDeViaje buscarGuias(Long id) {
        if (id == null) {
            return null;
        }
        List<GuiaDeViaje> list = em.createQuery(
            "SELECT g FROM GuiaDeViaje g " +
            "LEFT JOIN FETCH g.empresa " +
            "LEFT JOIN FETCH g.vehiculo " +
            "LEFT JOIN FETCH g.chofer " +
            "WHERE g.id = :id",
            GuiaDeViaje.class
        )
        .setParameter("id", id)
        .getResultList();

        return list.isEmpty() ? null : list.get(0);
    }

}
