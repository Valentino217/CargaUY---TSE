package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
@WebServlet("/p1/pruebas")
public class P1PruebasServlet extends HttpServlet {
    @Override protected void service(HttpServletRequest r,HttpServletResponse s)throws IOException {
        s.sendError(410,"Pantalla de pruebas retirada; ingresar por /login");
    }
}
