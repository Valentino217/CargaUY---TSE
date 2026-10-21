package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s)
            throws IOException,ServletException {
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        r.getRequestDispatcher("/WEB-INF/p2/login.xhtml").forward(r,s);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s)
            throws IOException,ServletException {
        r.setCharacterEncoding("UTF-8");
        if(!WebSeguridad.transporte(r)||!WebSeguridad.csrf(r)) {
            WebSeguridad.error(s,403,"Recargá el formulario antes de ingresar"); return;
        }
        try {
            var login=auth.login(r.getParameter("uid"),r.getParameter("password"));
            WebSeguridad.iniciar(r,login);
            s.sendRedirect(r.getContextPath()+"/index.xhtml");
        } catch(ErrorAcceso e) {
            r.setAttribute("error",e.getMessage()); s.setStatus(e.getEstado()); doGet(r,s);
        }
    }
}
