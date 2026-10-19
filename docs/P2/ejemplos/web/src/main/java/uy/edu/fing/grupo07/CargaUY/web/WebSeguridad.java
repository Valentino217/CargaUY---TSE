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
        return r.isSecure() || Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && InetAddress.getByName(r.getRemoteAddr()).isLoopbackAddress();
    }
    public static void error(HttpServletResponse s, int codigo,String texto) throws IOException {
        s.setStatus(codigo); s.setContentType("text/plain;charset=UTF-8"); s.getWriter().println(texto);
    }
}
