package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

/** Identidad establecida por el filtro; nunca se toma un usuario fijo del formulario. */
public final class SesionActual {
    private SesionActual() {}
    private static HttpServletRequest request() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) throw new ErrorAcceso(401,"Se requiere una sesión web");
        return (HttpServletRequest) faces.getExternalContext().getRequest();
    }
    public static Acceso actor() {
        Object actor = request().getAttribute("actor");
        if (!(actor instanceof Acceso acceso)) throw new ErrorAcceso(401,"Sesión no autenticada");
        return acceso;
    }
    public static String token() { return WebSeguridad.token(request()); }
}
