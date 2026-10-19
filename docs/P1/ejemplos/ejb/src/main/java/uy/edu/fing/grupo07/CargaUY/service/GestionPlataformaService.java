package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

@Stateless
@LocalBean
public class GestionPlataformaService {
    public record RubroVista(int codigo, String nombre, String descripcion) {}
    public record TipoCargaVista(int codigo, String descripcion) {}
    public record ReglaVista(int cargaKg, double desvioKm, long version) {}

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearRubro(String nombre, String descripcion) {
        Rubro r = new Rubro(Validaciones.texto(nombre, "Nombre", 100),
            descripcion(descripcion, 500));
        em.persist(r);
        em.flush();
        return r.getCodigo();
    }
    public List<RubroVista> listarRubros() {
        return em.createQuery("select r from Rubro r order by r.codigo", Rubro.class)
            .getResultList().stream()
            .map(r -> new RubroVista(r.getCodigo(), r.getNombre(), r.getDesc())).toList();
    }
    public void modificarRubro(int codigo, String nombre, String descripcion) {
        Rubro r = requerido(Rubro.class, codigo);
        r.setNombre(Validaciones.texto(nombre, "Nombre", 100));
        r.setDesc(descripcion(descripcion, 500));
    }
    public void eliminarRubro(int codigo) {
        Rubro r = requerido(Rubro.class, codigo);
        long referencias = em.createQuery(
            "select count(e) from Empresa e join e.rubros r where r.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El rubro está asociado a empresas");
        em.remove(r);
        // La FK sigue protegiendo si hay una asociación concurrente.
        em.flush();
    }
    public int crearTipoCarga(String descripcion) {
        TipoCarga t = new TipoCarga(Validaciones.texto(descripcion, "Descripción", 255));
        em.persist(t);
        em.flush();
        return t.getCodigo();
    }
    public List<TipoCargaVista> listarTiposCarga() {
        return em.createQuery("select t from TipoCarga t order by t.codigo", TipoCarga.class)
            .getResultList().stream()
            .map(t -> new TipoCargaVista(t.getCodigo(), t.getDesc())).toList();
    }
    public void modificarTipoCarga(int codigo, String descripcion) {
        requerido(TipoCarga.class, codigo).setDesc(
            Validaciones.texto(descripcion, "Descripción", 255));
    }
    public void eliminarTipoCarga(int codigo) {
        TipoCarga t = requerido(TipoCarga.class, codigo);
        long referencias = em.createQuery(
            "select count(g) from GuiaDeViaje g join g.tiposCarga t where t.codigo = :codigo",
            Long.class).setParameter("codigo", codigo).getSingleResult();
        if (referencias > 0) throw new ErrorNegocio("El tipo está utilizado en guías");
        em.remove(t);
        em.flush();
    }
    public ReglaVista consultarRegla() {
        ReglaFiscalizacion r = em.find(ReglaFiscalizacion.class, 1);
        if (r == null) throw new ErrorNegocio("Todavía no se configuró la regla");
        return vista(r);
    }
    public void crearRegla(int cargaKg, double desvioKm) {
        validarUmbrales(cargaKg, desvioKm);
        if (em.find(ReglaFiscalizacion.class, 1) != null)
            throw new ErrorNegocio("La regla ya existe; usá editar");
        em.persist(new ReglaFiscalizacion(cargaKg, desvioKm));
    }
    public void editarRegla(int cargaKg, double desvioKm, long versionEsperada) {
        validarUmbrales(cargaKg, desvioKm);
        ReglaFiscalizacion r = requerido(ReglaFiscalizacion.class, 1);
        if (r.getVersion() != versionEsperada)
            throw new ErrorNegocio("La regla cambió; consultala de nuevo antes de guardar");
        r.setUmbralCarga(cargaKg);
        r.setUmbralTracking(desvioKm);
        em.flush(); // @Version también detecta cambios concurrentes posteriores a la lectura.
    }
    private ReglaVista vista(ReglaFiscalizacion r) {
        return new ReglaVista(r.getUmbralCarga(), r.getUmbralTracking(), r.getVersion());
    }
    private void validarUmbrales(int cargaKg, double desvioKm) {
        if (cargaKg < 0 || !Double.isFinite(desvioKm) || desvioKm < 0)
            throw new ErrorNegocio("Umbrales no negativos: carga en kg y desvío en km");
    }
    private <T> T requerido(Class<T> clase, int codigo) {
        T entidad = em.find(clase, Validaciones.positivo(codigo, "Código"));
        if (entidad == null) throw new ErrorNegocio("No existe ese registro");
        return entidad;
    }
    private String descripcion(String texto, int maximo) {
        if (texto == null) return null;
        if (texto.length() > maximo) throw new ErrorNegocio("Descripción demasiado larga");
        return texto.trim();
    }
}
