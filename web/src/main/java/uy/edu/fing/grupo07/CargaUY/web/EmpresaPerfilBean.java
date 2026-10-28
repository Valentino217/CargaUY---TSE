package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.dto.DatosUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.EmpresaResumen;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.dto.UsuarioResumen;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.service.GestionPerfilesService;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Managed Bean para la vista /empresa/perfil.xhtml.
 * Gestiona los datos del ciudadano activo y sus empresas vinculadas.
 */
@Named("empresaPerfilBean")
@ViewScoped
public class EmpresaPerfilBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionPerfilesService perfilesService;

    private UsuarioResumen perfilUsuario;
    private String nombreEdit;
    private String mailEdit;
    private LocalDate fechaNacEdit;

    private List<EmpresaResumen> misEmpresas;
    private EmpresaResumen empresaSeleccionada;
    private String razonSocialEdit;
    private String nombrePublicoEdit;
    private String direccionEdit;

    @PostConstruct
    public void init() {
        cargarDatos();
    }

    public void cargarDatos() {
        try {
            String token = SesionActual.token();
            Acceso actor = SesionActual.actor();
            perfilUsuario = perfilesService.consultarUsuario(token, actor.id());
            if (perfilUsuario != null) {
                nombreEdit = perfilUsuario.nombre();
                mailEdit = perfilUsuario.mail();
                fechaNacEdit = perfilUsuario.fechaNac();
            }
            misEmpresas = perfilesService.listarEmpresas(token, 0, 50);
        } catch (Exception e) {
            misEmpresas = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Aviso", "No se pudieron cargar todos los datos del perfil: " + e.getMessage());
        }
    }

    public void actualizarPerfilPersonal() {
        if (perfilUsuario == null) {
            return;
        }
        try {
            String token = SesionActual.token();
            TipoUsuario tipo = TipoUsuario.valueOf(perfilUsuario.tipo());
            DatosUsuario datos = new DatosUsuario(
                    tipo,
                    nombreEdit,
                    mailEdit,
                    fechaNacEdit,
                    perfilUsuario.ci(),
                    null,
                    perfilUsuario.nroFuncionario(),
                    perfilUsuario.departamento(),
                    perfilUsuario.cargo()
            );
            perfilesService.modificarUsuario(token, perfilUsuario.id(), perfilUsuario.version(), datos);
            cargarDatos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Perfil Actualizado", "Tus datos personales fueron guardados correctamente.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al guardar", e.getMessage());
        }
    }

    public void prepararEdicionEmpresa(EmpresaResumen emp) {
        this.empresaSeleccionada = emp;
        if (emp != null) {
            this.razonSocialEdit = emp.razonSocial();
            this.nombrePublicoEdit = emp.nombrePublico();
            this.direccionEdit = emp.direccion();
        }
    }

    public void guardarEmpresa() {
        if (empresaSeleccionada == null) {
            return;
        }
        try {
            String token = SesionActual.token();
            perfilesService.modificarEmpresa(
                    token,
                    empresaSeleccionada.numero(),
                    empresaSeleccionada.version(),
                    razonSocialEdit,
                    nombrePublicoEdit,
                    direccionEdit
            );
            cargarDatos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Empresa Actualizada",
                    "Se actualizaron los datos de " + razonSocialEdit);
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al actualizar empresa", e.getMessage());
        }
    }

    private void mostrarMensaje(FacesMessage.Severity severidad, String resumen, String detalle) {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null) {
            context.addMessage(null, new FacesMessage(severidad, resumen, detalle));
        }
    }

    public UsuarioResumen getPerfilUsuario() {
        return perfilUsuario;
    }

    public String getNombreEdit() {
        return nombreEdit;
    }

    public void setNombreEdit(String nombreEdit) {
        this.nombreEdit = nombreEdit;
    }

    public String getMailEdit() {
        return mailEdit;
    }

    public void setMailEdit(String mailEdit) {
        this.mailEdit = mailEdit;
    }

    public LocalDate getFechaNacEdit() {
        return fechaNacEdit;
    }

    public void setFechaNacEdit(LocalDate fechaNacEdit) {
        this.fechaNacEdit = fechaNacEdit;
    }

    public List<EmpresaResumen> getMisEmpresas() {
        return misEmpresas;
    }

    public EmpresaResumen getEmpresaSeleccionada() {
        return empresaSeleccionada;
    }

    public String getRazonSocialEdit() {
        return razonSocialEdit;
    }

    public void setRazonSocialEdit(String razonSocialEdit) {
        this.razonSocialEdit = razonSocialEdit;
    }

    public String getNombrePublicoEdit() {
        return nombrePublicoEdit;
    }

    public void setNombrePublicoEdit(String nombrePublicoEdit) {
        this.nombrePublicoEdit = nombrePublicoEdit;
    }

    public String getDireccionEdit() {
        return direccionEdit;
    }

    public void setDireccionEdit(String direccionEdit) {
        this.direccionEdit = direccionEdit;
    }
}
