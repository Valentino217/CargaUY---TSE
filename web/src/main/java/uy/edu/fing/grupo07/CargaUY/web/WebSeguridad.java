package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.net.InetAddress;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

public final class WebSeguridad {
    private WebSeguridad() {}
    public static void preparar(HttpServletRequest r, HttpServletResponse s) {
        s.setHeader("Cache-Control","no-store");
        s.setHeader("X-Content-Type-Options","nosniff");
        s.setHeader("X-Frame-Options","DENY");
        s.setHeader("Content-Security-Policy","default-src 'self'; form-action 'self'; frame-ancestors 'none'");
        HttpSession h=r.getSession();
        if(h.getAttribute("csrf")==null) h.setAttribute("csrf",Cripto.aleatorio());
        r.setAttribute("csrf",h.getAttribute("csrf"));
    }
    public static boolean csrf(HttpServletRequest r) {
        HttpSession s=r.getSession(false);
        return s!=null && Cripto.igual((String)s.getAttribute("csrf"),r.getParameter("csrf"));
    }
    public static String token(HttpServletRequest r) {
        HttpSession s=r.getSession(false);
        return s==null?null:(String)s.getAttribute("token");
    }
    public static void iniciar(HttpServletRequest r, SesionesService.Login login) {
        r.getSession(); r.changeSessionId();
        r.getSession().setAttribute("token",login.token());
        r.getSession().setAttribute("csrf",Cripto.aleatorio());
        r.getSession().setMaxInactiveInterval(15*60);
    }
    public static boolean transporte(HttpServletRequest r) throws IOException {
        if (r.isSecure()) return true;
        if (!Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))) return false;
        InetAddress addr = InetAddress.getByName(r.getRemoteAddr());
        return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
    }
    public static void error(HttpServletResponse s, int codigo,String texto) throws IOException {
        s.setStatus(codigo); s.setContentType("text/plain;charset=UTF-8"); s.getWriter().println(texto);
    }

    /** Titulo corto y entendible para el codigo HTTP, sin jerga tecnica. */
    static String tituloError(int codigo) {
        return switch (codigo) {
            case 400 -> "La solicitud no es v\u00e1lida";
            case 401 -> "Tu sesi\u00f3n no es v\u00e1lida o venci\u00f3";
            case 403 -> "No ten\u00e9s permiso para acceder";
            case 404 -> "No encontramos lo que busc\u00e1bas";
            case 409 -> "No se pudo completar la operaci\u00f3n";
            case 503 -> "El servicio no est\u00e1 disponible por ahora";
            default -> codigo >= 500 ? "Ocurri\u00f3 un problema en el servidor" : "No pudimos completar la operaci\u00f3n";
        };
    }

    /** Sugerencia de que hacer a continuacion, segun el codigo HTTP. */
    static String ayudaError(int codigo) {
        return switch (codigo) {
            case 401 -> "Volv\u00e9 a ingresar para continuar.";
            case 403 -> "Si cre\u00e9s que deber\u00edas tener acceso, consult\u00e1 con un administrador de CargaUY.";
            case 503 -> "Esper\u00e1 unos minutos e intent\u00e1 nuevamente. Si el problema contin\u00faa, avis\u00e1 a un administrador.";
            default -> codigo >= 500
                ? "No es un problema tuyo. Intent\u00e1 nuevamente en unos minutos; si persiste, avis\u00e1 a un administrador."
                : "Revis\u00e1 los datos e intent\u00e1 de nuevo desde el inicio.";
        };
    }

    static String escapar(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Cartel de error legible (HTML) para flujos que responden fuera de JSF
     * (login, callback de gub.uy, filtros de seguridad). Reemplaza el texto plano.
     */
    public static void error(HttpServletRequest r, HttpServletResponse s, int codigo, String texto) throws IOException {
        s.setStatus(codigo);
        s.setContentType("text/html;charset=UTF-8");
        String ctx = escapar(r.getContextPath());
        s.getWriter().println("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"/>"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
            + "<title>CargaUY - " + escapar(tituloError(codigo)) + "</title>"
            + "<link rel=\"stylesheet\" href=\"" + ctx + "/css/plataforma.css\"/></head>"
            + "<body><main class=\"login-container\"><section class=\"card\" role=\"alert\">"
            + "<h1>" + escapar(tituloError(codigo)) + "</h1>"
            + "<p>" + escapar(texto) + "</p>"
            + "<p>" + escapar(ayudaError(codigo)) + "</p>"
            + "<p><a class=\"button button-full\" href=\"" + ctx + "/login\">Volver a ingresar</a></p>"
            + "<p><a href=\"" + ctx + "/\">Ir al inicio</a></p>"
            + "</section></main></body></html>");
    }
}