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
    @jakarta.ejb.EJB private uy.edu.fing.grupo07.CargaUY.security.ControlAcceso control;

    public List<Vehiculo> listarParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarVehiculos();
        return inicializarRelaciones(em.createQuery("select v from Vehiculo v join fetch v.empresa where v.empresa.activa=true "
            +"and exists(select x from VinculoEmpresa x where x.empresa=v.empresa and x.usuario.id=:id and x.responsable=true) "
            +"order by v.matricula",Vehiculo.class).setParameter("id",actor.id()).getResultList());
    }
    public List<Empresa> listarEmpresasParaActor(String token) {
        var actor=control.exigir(token);
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.ADMINISTRADOR)) return listarEmpresas();
        if(actor.tiene(uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.CHOFER)) {
            return em.createQuery("select e from Empresa e where e.activa=true and exists(select x from VinculoEmpresa x "
                +"where x.empresa=e and x.usuario.id=:id and x.chofer=true)", Empresa.class)
                .setParameter("id", actor.id()).getResultList();
        }
        return em.createQuery("select e from Empresa e where e.activa=true and exists(select x from VinculoEmpresa x "
            +"where x.empresa=e and x.usuario.id=:id and x.responsable=true)",Empresa.class)
            .setParameter("id",actor.id()).getResultList();
    }
    public List<Vehiculo> listarVehiculosPorEmpresa(String token,int empresa) {
        control.empresa(token,empresa,false); return listarVehiculosPorEmpresa(empresa);
    }
    public Vehiculo buscar(String token,String matricula) {
        Vehiculo v=buscar(matricula);
        if(v!=null) control.empresa(token,v.getEmpresa().getNroEmpresa(),false);
        return inicializarRelaciones(v);
    }
    public void crear(String token,Vehiculo v) {
        if (v == null || v.getMatricula() == null || v.getMatricula().isBlank()) {
            throw new IllegalArgumentException("La matrícula del vehículo es obligatoria.");
        }
        v.setMatricula(v.getMatricula().trim().toUpperCase());
        control.empresa(token,v.getEmpresa().getNroEmpresa(),true);
        if (buscar(v.getMatricula()) != null) {
            throw new IllegalArgumentException("Ya existe un vehículo registrado con la matrícula " + v.getMatricula() + ".");
        }
        crear(v);
    }
    public void modificar(String token,Vehiculo v) {
        Vehiculo anterior=buscar(token,v.getMatricula());
        if(anterior==null) throw new IllegalArgumentException("Vehículo inexistente");
        if(tieneGuiaEnCurso(v.getMatricula())) {
            throw new IllegalStateException("No se puede editar el vehículo " + v.getMatricula() + " porque tiene una guía de viaje EN_CURSO.");
        }
        control.empresa(token,anterior.getEmpresa().getNroEmpresa(),true);
        control.empresa(token,v.getEmpresa().getNroEmpresa(),true); modificar(v);
    }
    public void eliminar(String token,Vehiculo v) {
        Vehiculo actual=buscar(token,v.getMatricula());
        if(actual==null) throw new IllegalArgumentException("Vehículo inexistente");
        control.empresa(token,actual.getEmpresa().getNroEmpresa(),true); eliminar(actual);
    }

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public VehiculoEJB() {
    }

    private Vehiculo inicializarRelaciones(Vehiculo v) {
        if (v != null) {
            if (v.getEmpresa() != null) v.getEmpresa().getRazonSocial();
            if (v.getItv() != null) v.getItv().size();
            if (v.getPncs() != null) v.getPncs().size();
        }
        return v;
    }

    private List<Vehiculo> inicializarRelaciones(List<Vehiculo> lista) {
        if (lista != null) {
            for (Vehiculo v : lista) inicializarRelaciones(v);
        }
        return lista;
    }
    
    @Override
    public List<Vehiculo> listarVehiculos() {
        return inicializarRelaciones(em.createQuery(
            "SELECT v FROM Vehiculo v " +
            "LEFT JOIN FETCH v.empresa",
            Vehiculo.class
        ).getResultList());
    }
    @SuppressWarnings("unused")
    private List<Vehiculo> listarVehiculosInternalUnused() {
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
        if (vehiculo != null && vehiculo.getMatricula() != null && !vehiculo.getMatricula().isBlank()) {
            vehiculo.setMatricula(vehiculo.getMatricula().trim().toUpperCase(java.util.Locale.ROOT));
        }
        em.persist(vehiculo);
    }

    @Override
    public boolean tieneGuiaEnCurso(String matricula) {
        if (matricula == null || matricula.isBlank()) return false;
        Long count = em.createQuery(
                "SELECT COUNT(g) FROM GuiaDeViaje g WHERE UPPER(TRIM(g.vehiculo.matricula)) = UPPER(TRIM(:mat)) AND g.estado = :enCurso",
                Long.class
        )
        .setParameter("mat", matricula)
        .setParameter("enCurso", uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado.EN_CURSO)
        .getSingleResult();
        return count != null && count > 0L;
    }

    @Override
    public void modificar(Vehiculo vehiculo) {
        if (tieneGuiaEnCurso(vehiculo.getMatricula())) {
            throw new IllegalStateException("No se puede editar el vehículo " + vehiculo.getMatricula() + " porque tiene una guía de viaje EN_CURSO.");
        }
        Vehiculo existente = buscar(vehiculo.getMatricula());
        if (existente != null) {
            existente.setMarca(vehiculo.getMarca());
            existente.setModelo(vehiculo.getModelo());
            existente.setPeso(vehiculo.getPeso());
            existente.setCapacidadCarga(vehiculo.getCapacidadCarga());
            existente.setEstadoHabilitacion(vehiculo.isEstadoHabilitacion());
            existente.setEmpresa(vehiculo.getEmpresa());
            if (vehiculo.getPncs() != null && !vehiculo.getPncs().isEmpty()) {
                for (var pnc : vehiculo.getPncs()) {
                    if (pnc.getNro() == 0) {
                        pnc.setVehiculo(existente);
                        existente.getPncs().add(pnc);
                    }
                }
            }
            if (vehiculo.getItv() != null && !vehiculo.getItv().isEmpty()) {
                for (var itv : vehiculo.getItv()) {
                    if (itv.getNroCertificado() == 0) {
                        itv.setVehiculo(existente);
                        existente.getItv().add(itv);
                    }
                }
            }
        } else {
            em.merge(vehiculo);
        }
    }

    @Override
    public void eliminar(Vehiculo vehiculo) {
        Long cantGuias = em.createQuery(
                "SELECT COUNT(g) FROM GuiaDeViaje g WHERE g.vehiculo.matricula = :mat",
                Long.class
        ).setParameter("mat", vehiculo.getMatricula()).getSingleResult();

        if (cantGuias != null && cantGuias > 0) {
            throw new IllegalStateException(
                "No se puede eliminar el vehículo " + vehiculo.getMatricula() +
                " porque tiene " + cantGuias + " guía(s) de viaje asociada(s). Puede suspender su habilitación en su lugar."
            );
        }

        Long cantInfracciones = em.createQuery(
                "SELECT COUNT(i) FROM Infraccion i WHERE i.vehiculo.matricula = :mat",
                Long.class
        ).setParameter("mat", vehiculo.getMatricula()).getSingleResult();

        if (cantInfracciones != null && cantInfracciones > 0) {
            throw new IllegalStateException(
                "No se puede eliminar el vehículo " + vehiculo.getMatricula() +
                " porque tiene " + cantInfracciones + " expediente(s) de infracción asociado(s). Puede suspender su habilitación en su lugar."
            );
        }

        Long cantPesadas = em.createQuery(
                "SELECT COUNT(p) FROM PesadaBalanza p WHERE p.vehiculo.matricula = :mat",
                Long.class
        ).setParameter("mat", vehiculo.getMatricula()).getSingleResult();

        if (cantPesadas != null && cantPesadas > 0) {
            throw new IllegalStateException(
                "No se puede eliminar el vehículo " + vehiculo.getMatricula() +
                " porque tiene " + cantPesadas + " registro(s) de pesaje en balanza asociado(s). Puede suspender su habilitación en su lugar."
            );
        }

        em.remove(em.merge(vehiculo));
    }

    @Override
    public Vehiculo buscar(String matricula) {
        if (matricula == null) {
            return null;
        }
        Vehiculo v = em.find(Vehiculo.class, matricula);
        String upper = matricula.trim().toUpperCase(java.util.Locale.ROOT);
        if (v == null && !matricula.equals(upper)) {
            v = em.find(Vehiculo.class, upper);
        }
        return inicializarRelaciones(v);
    }

    @Override
    public List<Vehiculo> listarVehiculosPorCiudadano(int ciudadanoId) {
        return inicializarRelaciones(em.createQuery(
            "SELECT DISTINCT v FROM Vehiculo v " +
            "JOIN FETCH v.empresa " +
            "WHERE v.empresa.nroEmpresa IN (SELECT ve.empresa.nroEmpresa FROM VinculoEmpresa ve WHERE ve.usuario.id = :ciudadanoId AND ve.responsable = true AND ve.empresa.activa = true) " +
            "ORDER BY v.matricula",
            Vehiculo.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList());
    }
    
    @Override
    public List<Empresa> listarEmpresasPorCiudadano(int ciudadanoId) {
        return em.createQuery(
            "SELECT DISTINCT e FROM Empresa e " +
            "" +
            "WHERE e.nroEmpresa IN (SELECT ve.empresa.nroEmpresa FROM VinculoEmpresa ve WHERE ve.usuario.id = :ciudadanoId AND ve.responsable = true AND ve.empresa.activa = true)",
            Empresa.class
        )
        .setParameter("ciudadanoId", ciudadanoId)
        .getResultList();
    }
    
    @Override
    public List<Vehiculo> listarVehiculosPorEmpresa(int nroEmpresa) {
        return inicializarRelaciones(em.createQuery(
            "SELECT v FROM Vehiculo v " +
            "JOIN FETCH v.empresa " +
            "WHERE v.empresa.nroEmpresa = :nroEmpresa " +
            "ORDER BY v.matricula",
            Vehiculo.class
        )
        .setParameter("nroEmpresa", nroEmpresa)
        .getResultList());
    }

    /** Vehículo apto hoy = habilitación base + ITV vigente + PNC vigente. */
    @Override
    public boolean estaAptoParaOperar(String matricula) {
        return estaAptoParaOperar(matricula, java.time.LocalDate.now());
    }

    /** Vehículo apto en la fecha de referencia indicada (E.7: fecha de la guía de viaje). */
    @Override
    public boolean estaAptoParaOperar(String matricula, java.time.LocalDate fechaReferencia) {
        if (matricula == null || matricula.isBlank()) {
            return false;
        }
        Vehiculo v = buscar(matricula);
        return v != null && v.verificarHabilitacionVigenteEn(fechaReferencia);
    }

    VehiculoEJB(EntityManager em) {
        this.em = em;
    }
}
