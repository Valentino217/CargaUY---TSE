package uy.edu.fing.grupo07.CargaUY.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class GestionPerfilesService {
    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public int crearUsuario(DatosUsuario d) {
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
        em.persist(u);
        em.flush();
        return u.getId();
    }

    public UsuarioResumen consultarUsuario(int id) {
        return UsuarioResumen.desde(usuario(id));
    }

    public List<UsuarioResumen> listarUsuarios(TipoUsuario tipo) {
        // TYPE es el subtipo JPA, no una cadena concatenada al JPQL.
        List<Usuario> usuarios = tipo == null
            ? em.createQuery("select u from Usuario u order by u.id", Usuario.class)
                .getResultList()
            : em.createQuery("select u from Usuario u where type(u) = :tipo order by u.id",
                Usuario.class).setParameter("tipo", clase(tipo)).getResultList();
        return usuarios.stream().map(UsuarioResumen::desde).toList();
    }

    public void modificarUsuario(int id, DatosUsuario d) {
        Usuario u = usuario(id);
        validarUsuario(d);
        if (u.getClass() != clase(d.tipo()) || u.getCi() != d.ci())
            throw new ErrorNegocio("Esta operación no cambia tipo ni CI");
        // La edición de perfil no cambia credenciales.
        copiarDatos(u, d);
    }

    public void cambiarEstadoUsuario(int id, boolean activo) {
        usuario(id).setActivo(activo);
    }

    public void crearEmpresa(int numero, String razon, String nombre, String direccion) {
        Validaciones.positivo(numero, "Número de empresa");
        if (em.find(Empresa.class, numero) != null)
            throw new ErrorNegocio("Ya existe esa empresa");
        em.persist(new Empresa(numero,
            Validaciones.texto(razon, "Razón social", 150),
            Validaciones.texto(nombre, "Nombre público", 150),
            Validaciones.texto(direccion, "Dirección", 255)));
    }

    public EmpresaResumen consultarEmpresa(int numero) {
        return EmpresaResumen.desde(empresa(numero));
    }

    public List<EmpresaResumen> listarEmpresas() {
        return em.createQuery("select e from Empresa e order by e.nroEmpresa", Empresa.class)
            .getResultList().stream().map(EmpresaResumen::desde).toList();
    }

    public void modificarEmpresa(int numero, String razon, String nombre, String direccion) {
        Empresa e = empresa(numero);
        e.setRazonSocial(Validaciones.texto(razon, "Razón social", 150));
        e.setNombrePublico(Validaciones.texto(nombre, "Nombre público", 150));
        e.setDireccion(Validaciones.texto(direccion, "Dirección", 255));
    }

    public void cambiarEstadoEmpresa(int numero, boolean activa) {
        empresa(numero).setActiva(activa);
    }

    public void asociarCiudadano(int numeroEmpresa, int idCiudadano) {
        Empresa e = empresa(numeroEmpresa);
        // Serializa cambios de asociación sobre la misma empresa.
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        if (!e.isActiva() || !c.isActivo())
            throw new ErrorNegocio("Empresa y ciudadano deben estar activos");
        if (!e.getCiudadanos().contains(c)) e.getCiudadanos().add(c);
        if (!c.getEmpresas().contains(e)) c.getEmpresas().add(e);
    }

    public void desasociarCiudadano(int numeroEmpresa, int idCiudadano) {
        Empresa e = empresa(numeroEmpresa);
        em.lock(e, LockModeType.PESSIMISTIC_WRITE);
        Ciudadano c = em.find(Ciudadano.class, idCiudadano);
        if (c == null) throw new ErrorNegocio("No existe ese ciudadano");
        e.getCiudadanos().remove(c);
        c.getEmpresas().remove(e);
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
