package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.ejb.EJB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse s) throws IOException {
        if(!WebSeguridad.transporte(r)||!WebSeguridad.csrf(r)) { s.sendError(403); return; }
        auth.logout(WebSeguridad.token(r)); r.getSession().invalidate();
        s.sendRedirect(r.getContextPath()+"/login");
    }
}