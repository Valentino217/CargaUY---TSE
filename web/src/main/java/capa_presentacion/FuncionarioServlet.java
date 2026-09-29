package capa_presentacion;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;

import java.io.IOException;
import java.time.LocalDate;
import capa_negocio.FuncionarioEJB;

/**
 * Servlet implementation class FuncionarioServlet
 */
@WebServlet("/FuncionarioServlet")
public class FuncionarioServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	@EJB
	private FuncionarioEJB FuncionarioEJB;   
    /**
     * @see HttpServlet#HttpServlet()
     */
    public FuncionarioServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		response.getWriter().append("Served at: ").append(request.getContextPath());
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		 // 1. Obtener datos del formulario
        String nombre = request.getParameter("nombre");
        String mail = request.getParameter("mail");
        String fechaNacString = request.getParameter("fechaNac");
        String ciString = request.getParameter("ci");
        String contraseña = request.getParameter("password");
        String nroFuncionarioString = request.getParameter("nroFuncionario");
        String departamento = request.getParameter("departamento");

        // 2. Convertir los datos
        LocalDate fechaNac = LocalDate.parse(fechaNacString);
        int ci = Integer.parseInt(ciString);
        int nroFuncionario = Integer.parseInt(nroFuncionarioString);

        // 3. Crear el Funcionario
        Funcionario funcionario = new Funcionario(
                nombre,
                mail,
                fechaNac,
                ci,
                contraseña,
                nroFuncionario,
                departamento
        );

        // 4. Mandarlo al EJB
        FuncionarioEJB.crear(funcionario);

        // 5. Volver a una página
        response.sendRedirect("FuncionarioPrueba.jsp");
    }
		
	

}
