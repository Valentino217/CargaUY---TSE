package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.AutenticacionService;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * Managed Bean de sesión/autenticación para evaluar RBAC en las vistas Facelets
 * y exponer los datos del usuario activo sin acoplar la vista a los detalles de LDAP/gub.uy.
 */
@Named("authBean")
@RequestScoped
public class AuthBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private AutenticacionService autenticacionService;

    /**
     * Provee una vista compatible con JavaBeans para Expression Language (EL).
     */
    public static class UsuarioActivoVista implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;
        private final int ci;
        private final String nombre;
        private final Set<TipoUsuario> roles;

        public UsuarioActivoVista(Acceso acceso) {
            this.id = acceso.id();
            this.ci = acceso.ci();
            this.nombre = acceso.nombre();
            this.roles = acceso.roles();
        }

        public int getId() {
            return id;
        }

        public int getCi() {
            return ci;
        }

        public String getNombre() {
            return nombre;
        }

        public Set<TipoUsuario> getRoles() {
            return roles;
        }

        @Override
        public String toString() {
            return nombre != null ? nombre : "";
        }
    }

    private Acceso resolverAcceso() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context == null) {
            return null;
        }
        HttpServletRequest request = (HttpServletRequest) context.getExternalContext().getRequest();
        Object attr = request.getAttribute("actor");
        if (attr instanceof Acceso acceso) {
            return acceso;
        }
        String token = WebSeguridad.token(request);
        if (token != null && !token.isBlank() && autenticacionService != null) {
            try {
                Acceso validado = autenticacionService.validarToken(token);
                request.setAttribute("actor", validado);
                return validado;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean esAnonimo() {
        return resolverAcceso() == null;
    }

    public boolean esCiudadanoEmpresa() {
        Acceso actor = resolverAcceso();
        return actor != null && (actor.tiene(TipoUsuario.CIUDADANO) || actor.tiene(TipoUsuario.ADMINISTRADOR));
    }

    public boolean esFuncionario() {
        Acceso actor = resolverAcceso();
        return actor != null && (actor.tiene(TipoUsuario.FUNCIONARIO) || actor.tiene(TipoUsuario.ADMINISTRADOR));
    }

    public boolean esAutoridad() {
        Acceso actor = resolverAcceso();
        return actor != null && (actor.tiene(TipoUsuario.AUTORIDAD) || actor.tiene(TipoUsuario.ADMINISTRADOR));
    }

    public boolean esAdministrador() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.ADMINISTRADOR);
    }

    public boolean isAdmin() {
        return esAdministrador();
    }

    public boolean esChofer() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.CHOFER);
    }

    public UsuarioActivoVista getUsuarioActivo() {
        Acceso actor = resolverAcceso();
        if (actor == null) {
            return new UsuarioActivoVista(new Acceso(0, 0, "Visitante Anónimo", Collections.emptySet()));
        }
        return new UsuarioActivoVista(actor);
    }

    public String getRolPrincipalLabel() {
        Acceso actor = resolverAcceso();
        if (actor == null) {
            return "Público";
        }
        if (actor.tiene(TipoUsuario.ADMINISTRADOR)) {
            return "Administrador";
        }
        if (actor.tiene(TipoUsuario.AUTORIDAD)) {
            return "Autoridad MTOP";
        }
        if (actor.tiene(TipoUsuario.FUNCIONARIO)) {
            return "Funcionario Fiscalizador";
        }
        if (actor.tiene(TipoUsuario.CIUDADANO)) {
            return "Ciudadano Empresa";
        }
        if (actor.tiene(TipoUsuario.CHOFER)) {
            return "Chofer";
        }
        return "Usuario";
    }

    public void verificarAutenticacion() throws IOException {
        if (esAnonimo()) {
            FacesContext faces = FacesContext.getCurrentInstance();
            if (faces != null) {
                ExternalContext ext = faces.getExternalContext();
                ext.redirect(ext.getRequestContextPath() + "/login");
            }
        }
    }

    public void cerrarSesion() throws IOException {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            return;
        }
        ExternalContext ext = faces.getExternalContext();
        HttpServletRequest req = (HttpServletRequest) ext.getRequest();
        String token = WebSeguridad.token(req);
        if (token != null && autenticacionService != null) {
            try {
                autenticacionService.logout(token);
            } catch (Exception ignored) {
            }
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ext.redirect(req.getContextPath() + "/login");
    }
}
