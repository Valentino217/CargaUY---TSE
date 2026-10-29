package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.dto.DatosUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.UsuarioResumen;
import uy.edu.fing.grupo07.CargaUY.security.IdentidadesService;
import uy.edu.fing.grupo07.CargaUY.service.GestionPerfilesService;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Managed Bean para /admin/usuarios.xhtml.
 * ABM de usuarios, asignación de roles e identidades federadas (LDAP / gub.uy).
 */
@Named("adminUsuariosBean")
@ViewScoped
public class AdminUsuariosBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionPerfilesService perfilesService;

    @EJB
    private IdentidadesService identidadesService;

    private List<UsuarioResumen> usuarios;
    private List<UsuarioResumen> usuariosFiltrados;

    private boolean modoEdicion;
    private int idEdit;
    private long versionEdit;
    private TipoUsuario tipoForm = TipoUsuario.CIUDADANO;
    private String nombreForm;
    private String mailForm;
    private LocalDate fechaNacForm = LocalDate.of(1990, 1, 1);
    private int ciForm;
    private String passwordForm;
    private Integer nroFuncionarioForm;
    private String departamentoForm;
    private String cargoForm;

    private UsuarioResumen usuarioIdentidadSel;
    private String origenIdentidadForm = "LDAP";
    private String uidExternoForm;
    private List<String> rolesSeleccionados = new ArrayList<>();

    @PostConstruct
    public void init() {
        cargarUsuarios();
    }

    public void cargarUsuarios() {
        try {
            usuarios = perfilesService.listarUsuarios(SesionActual.token(), null, 0, 100);
        } catch (Exception e) {
            usuarios = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron listar los usuarios: " + e.getMessage());
        }
    }

    public void prepararNuevoUsuario() {
        this.modoEdicion = false;
        this.idEdit = 0;
        this.versionEdit = 0;
        this.tipoForm = TipoUsuario.CIUDADANO;
        this.nombreForm = "";
        this.mailForm = "";
        this.fechaNacForm = LocalDate.of(1990, 1, 1);
        this.ciForm = 0;
        this.passwordForm = "Temporal123!";
        this.nroFuncionarioForm = null;
        this.departamentoForm = "";
        this.cargoForm = "";
    }

    public void prepararEdicionUsuario(UsuarioResumen u) {
        if (u == null) return;
        this.modoEdicion = true;
        this.idEdit = u.id();
        this.versionEdit = u.version();
        this.tipoForm = TipoUsuario.valueOf(u.tipo());
        this.nombreForm = u.nombre();
        this.mailForm = u.mail();
        this.fechaNacForm = u.fechaNac();
        this.ciForm = u.ci();
        this.passwordForm = null;
        this.nroFuncionarioForm = u.nroFuncionario();
        this.departamentoForm = u.departamento();
        this.cargoForm = u.cargo();
    }

    public void guardarUsuario() {
        try {
            String token = SesionActual.token();
            DatosUsuario datos = new DatosUsuario(
                    tipoForm,
                    nombreForm,
                    mailForm,
                    fechaNacForm,
                    ciForm,
                    modoEdicion ? null : (passwordForm == null || passwordForm.isBlank() ? "Temporal123!" : passwordForm),
                    nroFuncionarioForm,
                    departamentoForm,
                    cargoForm
            );
            if (modoEdicion) {
                perfilesService.modificarUsuario(token, idEdit, versionEdit, datos);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Usuario Actualizado", "Se guardaron los cambios de " + nombreForm);
            } else {
                int nuevoId = perfilesService.crearUsuario(token, datos);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Usuario Creado", "Se registró el usuario #" + nuevoId + " (" + nombreForm + ")");
            }
            cargarUsuarios();
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al guardar usuario", e.getMessage());
            FacesContext.getCurrentInstance().validationFailed();
        }
    }

    public void cambiarEstado(UsuarioResumen u, boolean activar) {
        if (u == null) return;
        try {
            perfilesService.cambiarEstadoUsuario(SesionActual.token(), u.id(), activar);
            cargarUsuarios();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Estado Actualizado",
                    "La cuenta de " + u.nombre() + " fue " + (activar ? "reactivada" : "desactivada") + ".");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
        }
    }

    public void prepararRolesEIdentidad(UsuarioResumen u) {
        this.usuarioIdentidadSel = u;
        this.rolesSeleccionados = new ArrayList<>();
        if (u != null && u.roles() != null) {
            u.roles().forEach(r -> rolesSeleccionados.add(r.name()));
        }
        boolean interno = u != null && ("ADMINISTRADOR".equals(u.tipo()) || "AUTORIDAD".equals(u.tipo()));
        this.origenIdentidadForm = interno ? "LDAP" : "GUB";
        this.uidExternoForm = "";
    }

    public void guardarRoles() {
        if (usuarioIdentidadSel == null || rolesSeleccionados == null || rolesSeleccionados.isEmpty()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Seleccione al menos un rol.");
            return;
        }
        try {
            Set<TipoUsuario> set = EnumSet.noneOf(TipoUsuario.class);
            for (String r : rolesSeleccionados) {
                set.add(TipoUsuario.valueOf(r));
            }
            identidadesService.roles(SesionActual.token(), usuarioIdentidadSel.id(), set);
            cargarUsuarios();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Roles Actualizados",
                    "Se actualizaron los roles del usuario #" + usuarioIdentidadSel.id());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al asignar roles", e.getMessage());
        }
    }

    public void vincularIdentidadExterna() {
        if (usuarioIdentidadSel == null || uidExternoForm == null || uidExternoForm.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "Ingrese el UID / Subject externo a vincular.");
            return;
        }
        try {
            identidadesService.vincular(SesionActual.token(), usuarioIdentidadSel.id(), origenIdentidadForm, uidExternoForm.trim());
            cargarUsuarios();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Identidad Vinculada",
                    "Se vinculó la identidad " + origenIdentidadForm + " (" + uidExternoForm + ") al usuario #" + usuarioIdentidadSel.id());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al vincular identidad", e.getMessage());
        }
    }

    public long getTotalActivos() {
        if (usuarios == null) return 0;
        return usuarios.stream().filter(UsuarioResumen::activo).count();
    }

    public long getTotalInternos() {
        if (usuarios == null) return 0;
        return usuarios.stream()
                .filter(u -> "ADMINISTRADOR".equals(u.tipo()) || "AUTORIDAD".equals(u.tipo()) || "FUNCIONARIO".equals(u.tipo()))
                .count();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<UsuarioResumen> getUsuarios() {
        return usuarios;
    }

    public List<UsuarioResumen> getUsuariosFiltrados() {
        return usuariosFiltrados;
    }

    public void setUsuariosFiltrados(List<UsuarioResumen> usuariosFiltrados) {
        this.usuariosFiltrados = usuariosFiltrados;
    }

    public boolean isModoEdicion() {
        return modoEdicion;
    }

    public TipoUsuario getTipoForm() {
        return tipoForm;
    }

    public void setTipoForm(TipoUsuario tipoForm) {
        this.tipoForm = tipoForm;
    }

    public String getNombreForm() {
        return nombreForm;
    }

    public void setNombreForm(String nombreForm) {
        this.nombreForm = nombreForm;
    }

    public String getMailForm() {
        return mailForm;
    }

    public void setMailForm(String mailForm) {
        this.mailForm = mailForm;
    }

    public LocalDate getFechaNacForm() {
        return fechaNacForm;
    }

    public void setFechaNacForm(LocalDate fechaNacForm) {
        this.fechaNacForm = fechaNacForm;
    }

    public int getCiForm() {
        return ciForm;
    }

    public void setCiForm(int ciForm) {
        this.ciForm = ciForm;
    }

    public String getPasswordForm() {
        return passwordForm;
    }

    public void setPasswordForm(String passwordForm) {
        this.passwordForm = passwordForm;
    }

    public Integer getNroFuncionarioForm() {
        return nroFuncionarioForm;
    }

    public void setNroFuncionarioForm(Integer nroFuncionarioForm) {
        this.nroFuncionarioForm = nroFuncionarioForm;
    }

    public String getDepartamentoForm() {
        return departamentoForm;
    }

    public void setDepartamentoForm(String departamentoForm) {
        this.departamentoForm = departamentoForm;
    }

    public String getCargoForm() {
        return cargoForm;
    }

    public void setCargoForm(String cargoForm) {
        this.cargoForm = cargoForm;
    }

    public UsuarioResumen getUsuarioIdentidadSel() {
        return usuarioIdentidadSel;
    }

    public String getOrigenIdentidadForm() {
        return origenIdentidadForm;
    }

    public void setOrigenIdentidadForm(String origenIdentidadForm) {
        this.origenIdentidadForm = origenIdentidadForm;
    }

    public String getUidExternoForm() {
        return uidExternoForm;
    }

    public void setUidExternoForm(String uidExternoForm) {
        this.uidExternoForm = uidExternoForm;
    }

    public List<String> getRolesSeleccionados() {
        return rolesSeleccionados;
    }

    public void setRolesSeleccionados(List<String> rolesSeleccionados) {
        this.rolesSeleccionados = rolesSeleccionados;
    }

    public TipoUsuario[] getTiposUsuario() {
        return TipoUsuario.values();
    }
}
