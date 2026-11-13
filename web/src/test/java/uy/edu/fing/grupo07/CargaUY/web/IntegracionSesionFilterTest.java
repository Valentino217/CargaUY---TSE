package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.AutenticacionService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

import static org.mockito.Mockito.*;

class IntegracionSesionFilterTest {

    private IntegracionSesionFilter filtro;
    private AutenticacionService auth;
    private HttpServletRequest req;
    private HttpServletResponse res;
    private FilterChain chain;

    @BeforeEach
    void preparar() throws Exception {
        filtro = new IntegracionSesionFilter();
        auth = mock(AutenticacionService.class);
        var field = IntegracionSesionFilter.class.getDeclaredField("auth");
        field.setAccessible(true);
        field.set(filtro, auth);

        req = mock(HttpServletRequest.class);
        res = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        HttpSession sesion = mock(HttpSession.class);

        when(req.getSession()).thenReturn(sesion);
        when(req.getSession(false)).thenReturn(sesion);
        when(sesion.getAttribute("token")).thenReturn("tok");
        when(req.getRequestURI()).thenReturn("/CargaUY-web/portal/info-publica.xhtml");
        when(req.getContextPath()).thenReturn("/CargaUY-web");
        when(req.getMethod()).thenReturn("GET");
        when(req.isSecure()).thenReturn(true);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    void choferSoloAccedeAPortalComoCiudadanoComunYSeBloqueaChoferMisViajes() throws Exception {
        when(auth.validarToken("tok")).thenReturn(new Acceso(50, 11223344, "Carlos Chofer", Set.of(TipoUsuario.CHOFER)));

        for (String permitida : new String[]{"/portal/info-publica.xhtml", "/portal/validar-empresa.xhtml", "/index.xhtml", "/completar-perfil.xhtml"}) {
            when(req.getServletPath()).thenReturn(permitida);
            filtro.doFilter(req, res, chain);
        }
        verify(chain, times(4)).doFilter(req, res);

        for (String bloqueada : new String[]{
                "/chofer/mis-viajes.xhtml",
                "/empresa/viajes.xhtml",
                "/funcionario/empresas.xhtml",
                "/backoffice/dashboard.xhtml",
                "/admin/usuarios.xhtml",
                "/GuiaDeViajeJSF.xhtml",
                "/detalleViaje.xhtml"
        }) {
            when(req.getServletPath()).thenReturn(bloqueada);
            filtro.doFilter(req, res, chain);
        }
        verify(res, times(7)).setStatus(403);
        verifyNoMoreInteractions(chain);
    }

    @Test
    void choferTieneMismosPermisosWebQueCiudadanoComun() throws Exception {
        for (TipoUsuario rol : new TipoUsuario[]{TipoUsuario.CHOFER, TipoUsuario.CIUDADANO_COMUN}) {
            reset(chain, res);
            when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            when(auth.validarToken("tok")).thenReturn(new Acceso(50, 11223344, "Usuario", Set.of(rol)));

            when(req.getServletPath()).thenReturn("/portal/info-publica.xhtml");
            filtro.doFilter(req, res, chain);
            verify(chain, times(1)).doFilter(req, res);

            when(req.getServletPath()).thenReturn("/chofer/mis-viajes.xhtml");
            filtro.doFilter(req, res, chain);
            verify(res).setStatus(403);
            verifyNoMoreInteractions(chain);
        }
    }
}
