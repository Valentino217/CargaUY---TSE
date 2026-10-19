package uy.edu.fing.grupo07.CargaUY.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ejb.LocalBean;
import jakarta.ejb.EJB;
import uy.edu.fing.grupo07.CargaUY.security.*;
import static uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class GestionPerfilesService {
    @EJB private ControlAcceso control;
    @EJB private SesionesService sesiones;
    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearUsuario(String token, DatosUsuario d) {
        control.admin(token);
        validarUsuario(d);
        long existentes = em.createQuery(
            "select count(u) from Usuario u where u.ci = :ci", Long.class)
            .setParameter("ci", d.ci()).getSingleResult();
        if (existentes > 0) throw new ErrorNegocio("Ya existe un usuario con esa CI");
        String password = d.password();
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ErrorNegocio("Contraseña: mínimo 8 caracteres y máximo 72 bytes UTF-8");
        char[] caracteres = password.toCharArray();
        String hash;
        try { hash = BCrypt.withDefaults().hashToString(12, caracteres); }
        finally { Arrays.fill(caracteres, '\0'); }
        Usuario u = switch (d.tipo()) {
            case CIUDADANO -> new Ciudadano();
            case CHOFER -> new Chofer();
            case FUNCIONARIO -> new Funcionario();
            case AUTORIDAD -> new Autoridad();
            case ADMINISTRADOR -> new Administrador();
        };
        copiarDatos(u, d);
        u.setPassword(hash);
        u.getRoles().add(d.tipo());
        em.persist(u);
        em.flush();
        return u.getId();
    }

    public UsuarioResumen consultarUsuario(String token, int id) {
        Acceso actor=control.exigir(token); if(actor.id()!=id && !actor.tiene(ADMINISTRADOR) && !actor.tiene(FUNCIONARIO)) throw new ErrorAcceso(403,"Perfil ajeno");
        return UsuarioResumen.desde(usuario(id));
    }

    public List<UsuarioResumen> listarUsuarios(String token, TipoUsuario tipo, int pagina, int tam) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        paginar(pagina,tam);
        // TYPE es el subtipo JPA, no una cadena concatenada al JPQL.
        List<Usuario> usuarios = tipo == null
            ? em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .setFirstResult(pagina*tam).setMaxResults(tam).getResultList()
            : em.createQuery("select u from Usuario u where type(u) = :tipo order by u.id",
                Usuario.class).setParameter("tipo", clase(tipo)).setFirstResult(pagina*tam).setMaxResults(tam).getResultList();
        return usuarios.stream().map(UsuarioResumen::desde).toList();
    }

    public void modificarUsuario(String token, int id, long versionEsperada, DatosUsuario d) {
        Acceso actor=control.exigir(token); if(actor.id()!=id && !actor.tiene(ADMINISTRADOR)) throw new ErrorAcceso(403,"Perfil ajeno");
        Usuario u = usuario(id);
        if(u.getVersion()!=versionEsperada) throw new ErrorAcceso(409,"Perfil modificado; recargá");
        validarUsuario(d);
        if (!clase(d.tipo()).isInstance(u) || u.getCi() != d.ci())
            throw new ErrorNegocio("Esta operación no cambia tipo ni CI");
        // La edición de perfil no cambia credenciales.
        copiarDatos(u, d);
    }

    public void cambiarEstadoUsuario(String token, int id, boolean activo) {
        Acceso actor=control.admin(token); if(actor.id()==id && !activo) throw new ErrorAcceso(409,"No desactivar tu propia cuenta"); sesiones.revocarUsuario(id);
        usuario(id).setActivo(activo);
    }

    public void crearEmpresa(String token, int numero, String razon, String nombre, String direccion) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Validaciones.positivo(numero, "Número de empresa");
        if (em.find(Empresa.class, numero) != null)
            throw new ErrorNegocio("Ya existe esa empresa");
        em.persist(new Empresa(numero,
            Validaciones.texto(razon, "Razón social", 150),
            Validaciones.texto(nombre, "Nombre público", 150),
            Validaciones.texto(direccion, "Dirección", 255)));
    }

    public EmpresaResumen consultarEmpresa(String token, int numero) {
        control.empresa(token,numero,false);
        return EmpresaResumen.desde(empresa(numero));
    }

    public List<EmpresaResumen> listarEmpresas(String token, int pagina, int tam) {
        Acceso actor=control.exigir(token); paginar(pagina,tam);
        boolean global=actor.tiene(ADMINISTRADOR)||actor.tiene(FUNCIONARIO)||actor.tiene(AUTORIDAD);
        if(!global&&!actor.tiene(CIUDADANO)) return List.of();
        var q=em.createQuery(global ? "select e from Empresa e order by e.nroEmpresa"
            : "select v.empresa from VinculoEmpresa v where v.usuario.id=:id and v.responsable=true and v.empresa.activa=true order by v.empresa.nroEmpresa",Empresa.class);
        if(!global) q.setParameter("id",actor.id());
        return q.setFirstResult(pagina*tam).setMaxResults(tam).getResultList().stream()
            .map(EmpresaResumen::desde).toList();
    }

    public void modificarEmpresa(String token, int numero, long versionEsperada, String razon, String nombre, String direccion) {
        control.empresa(token,numero,true);
        Empresa e = empresa(numero);
        if(e.getVersion()!=versionEsperada) throw new ErrorAcceso(409,"Empresa modificada; recargá");
        e.setRazonSocial(Validaciones.texto(razon, "Razón social", 150));
        e.setNombrePublico(Validaciones.texto(nombre, "Nombre público", 150));
        e.setDireccion(Validaciones.texto(direccion, "Dirección", 255));
    }

    public void cambiarEstadoEmpresa(String token, int numero, boolean activa) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        empresa(numero).setActiva(activa);
    }

    public void asociarCiudadano(String token, int numeroEmpresa, int idCiudadano) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Empresa e = empresa(numeroEmpresa);
        // Serializa cambios de asociación sobre la misma empresa.
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        if (!e.isActiva() || !c.isActivo())
            throw new ErrorNegocio("Empresa y ciudadano deben estar activos");
        if (e.getCiudadanos().stream().noneMatch(x -> x.getId()==c.getId())) e.getCiudadanos().add(c);
        if (c.getEmpresas().stream().noneMatch(x -> x.getNroEmpresa()==e.getNroEmpresa())) c.getEmpresas().add(e);
        if(!c.getRoles().contains(CIUDADANO)) throw new ErrorAcceso(409,"Falta rol CIUDADANO");
        VinculoEmpresa v=em.find(VinculoEmpresa.class,VinculoEmpresa.clave(numeroEmpresa,idCiudadano));
        if(v==null) { v=new VinculoEmpresa(c,e); em.persist(v); }
        v.setResponsable(true);
    }

    public void desasociarCiudadano(String token, int numeroEmpresa, int idCiudadano) {
        control.exigir(token,ADMINISTRADOR,FUNCIONARIO);
        Empresa e = empresa(numeroEmpresa);
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        e.getCiudadanos().removeIf(x -> x.getId()==c.getId());
        c.getEmpresas().removeIf(x -> x.getNroEmpresa()==e.getNroEmpresa());
        VinculoEmpresa v=em.find(VinculoEmpresa.class,VinculoEmpresa.clave(numeroEmpresa,idCiudadano));
        if(v!=null) v.setResponsable(false);
    }

    private void paginar(int pagina,int tam) {
        if(pagina<0||pagina>100000||tam<1||tam>100) throw new ErrorNegocio("Paginación inválida");
    }
    private Usuario usuario(int id) {
        Usuario u = em.find(Usuario.class, Validaciones.positivo(id, "ID"));
        if (u == null) throw new ErrorNegocio("Usuario inexistente");
        return u;
    }
    private Empresa empresa(int numero) {
        Empresa e = em.find(Empresa.class, Validaciones.positivo(numero, "Empresa"));
        if (e == null) throw new ErrorNegocio("Empresa inexistente");
        return e;
    }
    private void validarUsuario(DatosUsuario d) {
        if (d == null || d.tipo() == null) throw new ErrorNegocio("Tipo obligatorio");
        Validaciones.texto(d.nombre(), "Nombre", 100);
        String mail = Validaciones.texto(d.mail(), "Correo", 150);
        if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ErrorNegocio("Correo inválido");
        Validaciones.positivo(d.ci(), "CI");
        if (d.fechaNac() == null || d.fechaNac().isAfter(LocalDate.now()))
            throw new ErrorNegocio("Fecha de nacimiento inválida");
        if (d.tipo() == TipoUsuario.FUNCIONARIO) {
            if (d.nroFuncionario() == null) throw new ErrorNegocio("Número obligatorio");
            Validaciones.positivo(d.nroFuncionario(), "Número de funcionario");
            Validaciones.texto(d.departamento(), "Departamento", 100);
        }
        if (d.tipo() == TipoUsuario.AUTORIDAD)
            Validaciones.texto(d.cargo(), "Cargo", 100);
    }
    private void copiarDatos(Usuario u, DatosUsuario d) {
        u.setNombre(d.nombre().trim());
        u.setMail(d.mail().trim().toLowerCase(Locale.ROOT));
        u.setFechaNac(d.fechaNac());
        u.setCi(d.ci());
        if (u instanceof Funcionario f) {
            f.setNroFuncionario(d.nroFuncionario());
            f.setDepartamento(d.departamento().trim());
        }
        if (u instanceof Autoridad a) a.setCargo(d.cargo().trim());
    }
    private Class<? extends Usuario> clase(TipoUsuario tipo) {
        return switch (tipo) {
            case CIUDADANO -> Ciudadano.class;
            case CHOFER -> Chofer.class;
            case FUNCIONARIO -> Funcionario.class;
            case AUTORIDAD -> Autoridad.class;
            case ADMINISTRADOR -> Administrador.class;
        };
    }
}