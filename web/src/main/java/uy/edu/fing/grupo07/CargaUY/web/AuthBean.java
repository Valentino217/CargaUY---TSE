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
import java.util.List;
import java.util.Map;
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
        return actor != null && actor.tiene(TipoUsuario.CIUDADANO);
    }

    public boolean esSoloCiudadanoEmpresa() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.CIUDADANO);
    }

    public boolean esFuncionario() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.FUNCIONARIO);
    }

    public boolean esAutoridad() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.AUTORIDAD);
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

    public boolean esCiudadanoComun() {
        Acceso actor = resolverAcceso();
        return actor != null && actor.tiene(TipoUsuario.CIUDADANO_COMUN);
    }

    public UsuarioActivoVista getUsuarioActivo() {
        Acceso actor = resolverAcceso();
        if (actor == null) {
            return new UsuarioActivoVista(new Acceso(0, 0, "Visitante Anónimo", Collections.emptySet()));
        }
        return new UsuarioActivoVista(actor);
    }

    private record RolVisual(String etiqueta, String temaCss, String iconoCss) {
    }

    private static final List<TipoUsuario> PRIORIDAD_ROLES = List.of(
            TipoUsuario.ADMINISTRADOR,
            TipoUsuario.AUTORIDAD,
            TipoUsuario.FUNCIONARIO,
            TipoUsuario.CIUDADANO,
            TipoUsuario.CIUDADANO_COMUN,
            TipoUsuario.CHOFER
    );

    private static final Map<TipoUsuario, RolVisual> ESTILOS_ROL = Map.of(
            TipoUsuario.ADMINISTRADOR, new RolVisual("Administrador", "cuy-rol-admin", "pi pi-cog"),
            TipoUsuario.AUTORIDAD, new RolVisual("Autoridad", "cuy-rol-autoridad", "pi pi-briefcase"),
            TipoUsuario.FUNCIONARIO, new RolVisual("Funcionario", "cuy-rol-funcionario", "pi pi-verified"),
            TipoUsuario.CIUDADANO, new RolVisual("Ciudadano Empresa", "cuy-rol-empresa", "pi pi-building"),
            TipoUsuario.CIUDADANO_COMUN, new RolVisual("Ciudadano", "cuy-rol-ciudadano", "pi pi-user"),
            TipoUsuario.CHOFER, new RolVisual("Chofer", "cuy-rol-ciudadano", "pi pi-car")
    );

    private static final RolVisual ROL_PUBLICO = new RolVisual("Público", "cuy-rol-ciudadano", "pi pi-globe");
    private static final RolVisual ROL_USUARIO = new RolVisual("Usuario", "cuy-rol-ciudadano", "pi pi-user");

    private RolVisual resolverRolVisual() {
        Acceso actor = resolverAcceso();
        if (actor == null) {
            return ROL_PUBLICO;
        }
        for (TipoUsuario rol : PRIORIDAD_ROLES) {
            if (actor.tiene(rol)) {
                return ESTILOS_ROL.get(rol);
            }
        }
        return ROL_USUARIO;
    }

    public String getRolPrincipalLabel() {
        return resolverRolVisual().etiqueta();
    }

    public String getRolThemeClass() {
        return resolverRolVisual().temaCss();
    }

    public String getRolIconClass() {
        return resolverRolVisual().iconoCss();
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

    public void redirigirAPortalPublico() throws IOException {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces != null) {
            ExternalContext ext = faces.getExternalContext();
            String destino = esAnonimo() ? "/login" : "/portal/info-publica.xhtml";
            ext.redirect(ext.getRequestContextPath() + destino);
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
        String destino = WebSeguridad.destinoLogout(req);
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ext.setResponseHeader("Cache-Control", "no-store");
        ext.redirect(destino);
    }
}
