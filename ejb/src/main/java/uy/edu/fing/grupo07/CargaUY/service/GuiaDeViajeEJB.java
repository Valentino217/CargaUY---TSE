package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Rubro;
import uy.edu.fing.grupo07.CargaUY.domain.entity.TipoCarga;
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
    private GuiaDeViaje inicializarRelaciones(GuiaDeViaje g) {
        if (g != null) {
            if (g.getEmpresa() != null) g.getEmpresa().getRazonSocial();
            if (g.getVehiculo() != null) g.getVehiculo().getMatricula();
            if (g.getChofer() != null) g.getChofer().getNombre();
            if (g.getTiposCarga() != null) g.getTiposCarga().size();
            if (g.getCorredorVial() == null || g.getCorredorVial().isBlank()
                    || g.getPolilineaRuta() == null || g.getPolilineaRuta().isBlank()) {
                g.actualizarRutaPlanificada();
            }
        }
        return g;
    }

    private List<GuiaDeViaje> inicializarRelaciones(List<GuiaDeViaje> lista) {
        if (lista != null) {
            for (GuiaDeViaje g : lista) inicializarRelaciones(g);
        }
        return lista;
    }

    public List<GuiaDeViaje> listarParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarGuias();
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CHOFER)) {
            return inicializarRelaciones(em.createQuery("select g from GuiaDeViaje g join fetch g.empresa left join fetch g.vehiculo "
                +"left join fetch g.chofer where g.chofer.id=:id order by g.fecha desc", GuiaDeViaje.class)
                .setParameter("id", actor.id()).getResultList());
        }
        return inicializarRelaciones(em.createQuery("select g from GuiaDeViaje g join fetch g.empresa left join fetch g.vehiculo "
            +"left join fetch g.chofer where g.empresa.activa=true and exists(select v from VinculoEmpresa v "
            +"where v.empresa=g.empresa and v.usuario.id=:id and v.responsable=true) order by g.fecha desc",GuiaDeViaje.class)
            .setParameter("id",actor.id()).getResultList());
    }
    public List<Usuario> listarChoferesPorEmpresa(String token,int empresa) {
        control.empresa(token,empresa,false); return listarChoferesPorEmpresa(empresa);
    }
    public GuiaDeViaje buscarGuias(String token,Long id) {
        GuiaDeViaje g=buscarGuias(id);
        if(g!=null) control.empresa(token,g.getEmpresa().getNroEmpresa(),false);
        return g;
    }
    public static String motivoNoHabilitado(Vehiculo v) {
        return motivoNoHabilitado(v, java.time.LocalDate.now());
    }

    public static String motivoNoHabilitado(Vehiculo v, java.time.LocalDate fechaReferencia) {
        java.time.LocalDate ref = fechaReferencia != null ? fechaReferencia : java.time.LocalDate.now();
        StringBuilder sb = new StringBuilder();
        if (!v.isEstadoHabilitacion()) sb.append("suspendido administrativamente; ");
        boolean itv = jakarta.persistence.Persistence.getPersistenceUtil().isLoaded(v, "itvs") && v.getItv() != null && v.getItv().stream().anyMatch(i -> i.isVigenteEn(ref));
        boolean pnc = jakarta.persistence.Persistence.getPersistenceUtil().isLoaded(v, "pncs") && v.getPncs() != null && v.getPncs().stream().anyMatch(p -> p.isVigenteEn(ref));
        if (!itv) sb.append("sin ITV vigente al ").append(ref).append("; ");
        if (!pnc) sb.append("sin PNC vigente al ").append(ref).append("; ");
        return sb.toString().trim();
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
            if (guia.getVehiculo() != null && guia.getVehiculo().getMatricula() != null) {
                String matricula = guia.getVehiculo().getMatricula().trim();
                var qVeh = em.createQuery(
                        "SELECT g FROM GuiaDeViaje g JOIN g.vehiculo v "
                                + "WHERE UPPER(v.matricula) = :matricula AND g.estado = :enCurso AND g.id <> :guiaId",
                        GuiaDeViaje.class
                );
                if (qVeh != null) {
                    List<GuiaDeViaje> activas = qVeh
                            .setParameter("matricula", matricula.toUpperCase())
                            .setParameter("enCurso", TipoEstado.EN_CURSO)
                            .setParameter("guiaId", guia.getId())
                            .setMaxResults(1)
                            .getResultList();
                    if (activas != null && !activas.isEmpty()) {
                        throw new IllegalStateException(
                                "No se puede iniciar la guía #" + guia.getId() + ": el vehículo " + matricula
                                        + " ya se encuentra en una guía de viaje en curso (Guía #" + activas.get(0).getId() + ")."
                        );
                    }
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
    }
    
    @Override
    public void crear(GuiaDeViaje guia) {
        if (guia != null) {
            guia.actualizarRutaPlanificada();
            if (guia.getTiposCarga() != null && !guia.getTiposCarga().isEmpty()) {
                java.util.List<TipoCarga> gestionados = new java.util.ArrayList<>();
                for (TipoCarga tc : guia.getTiposCarga()) {
                    if (tc != null) {
                        TipoCarga ref = em != null ? em.find(TipoCarga.class, tc.getCodigo()) : tc;
                        gestionados.add(ref != null ? ref : tc);
                    }
                }
                guia.setTiposCarga(gestionados);
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

    		existente.setFecha(guia.getFecha());
    		existente.setOrigen(guia.getOrigen());
    		existente.setDestino(guia.getDestino());
    		existente.actualizarRutaPlanificada();
    		existente.setRubroCliente(guia.getRubroCliente());
    		existente.setVolumenCarga(guia.getVolumenCarga());
            if (guia.getEmpresa() != null) existente.setEmpresa(guia.getEmpresa());
            if (guia.getVehiculo() != null) existente.setVehiculo(guia.getVehiculo());
            existente.setChofer(guia.getChofer());
            if (jakarta.persistence.Persistence.getPersistenceUtil().isLoaded(guia, "tiposCarga") && guia.getTiposCarga() != null) {
                java.util.List<TipoCarga> gestionados = new java.util.ArrayList<>();
                for (TipoCarga tc : guia.getTiposCarga()) {
                    if (tc != null) {
                        TipoCarga ref = em != null ? em.find(TipoCarga.class, tc.getCodigo()) : tc;
                        gestionados.add(ref != null ? ref : tc);
                    }
                }
                existente.setTiposCarga(gestionados);
            }
    }

    @Override
    public List<Rubro> listarRubrosCatalogo() {
        return em.createQuery("SELECT r FROM Rubro r ORDER BY r.nombre", Rubro.class).getResultList();
    }

    @Override
    public List<TipoCarga> listarTiposCargaCatalogo() {
        return em.createQuery("SELECT t FROM TipoCarga t ORDER BY t.desc", TipoCarga.class).getResultList();
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
        return inicializarRelaciones(em.createQuery(
            "SELECT g FROM GuiaDeViaje g " +
            "LEFT JOIN FETCH g.empresa " +
            "LEFT JOIN FETCH g.vehiculo " +
            "LEFT JOIN FETCH g.chofer " +
            "ORDER BY g.fecha DESC",
            GuiaDeViaje.class
        ).getResultList());
    }
    
 // Lista las guías de las empresas asociadas al ciudadano
    @Override
    public List<GuiaDeViaje> listarGuiasPorCiudadano(int ciudadanoId) {
        return inicializarRelaciones(em.createQuery(
            "SELECT DISTINCT g FROM GuiaDeViaje g " +
            "JOIN FETCH g.empresa " +
            "LEFT JOIN FETCH g.vehiculo " +
            "LEFT JOIN FETCH g.chofer " +
            "WHERE g.empresa IN (" +
            "    SELECT e FROM Empresa e " +
            "    WHERE e.nroEmpresa IN (SELECT ve.empresa.nroEmpresa FROM VinculoEmpresa ve WHERE ve.usuario.id = :ciudadanoId AND ve.responsable = true AND ve.empresa.activa = true)" +
            "" +
            ") " +
            "ORDER BY g.fecha DESC",
            GuiaDeViaje.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList());
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

        return list.isEmpty() ? null : inicializarRelaciones(list.get(0));
    }

}
