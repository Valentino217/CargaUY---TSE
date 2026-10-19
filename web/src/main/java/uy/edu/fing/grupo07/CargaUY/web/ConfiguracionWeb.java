package uy.edu.fing.grupo07.CargaUY.web;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import uy.edu.fing.grupo07.CargaUY.security.Config;

@WebListener
public class ConfiguracionWeb implements ServletContextListener {
    @Override public void contextInitialized(ServletContextEvent e) {
        var c=e.getServletContext().getSessionCookieConfig();
        c.setHttpOnly(true); c.setAttribute("SameSite","Lax");
        c.setSecure(!Boolean.parseBoolean(Config.valor("p1.entorno.local","false")));
        e.getServletContext().setSessionTrackingModes(java.util.Set.of(SessionTrackingMode.COOKIE));
    }
}