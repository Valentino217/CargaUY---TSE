package uy.edu.fing.grupo07.CargaUY.web.api;

import java.lang.reflect.Field;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

class AuthFilterIntegracionTest {
    @Test void sinTokenDevuelve401ConJsonAntesDeSeleccionarElRecurso()throws Exception {
        String anterior=System.getProperty("p1.entorno.local");
        try {
            System.setProperty("p1.entorno.local","true");
            var http=mock(HttpServletRequest.class);when(http.getRemoteAddr()).thenReturn("127.0.0.1");
            var auth=mock(AutenticacionService.class);
            when(auth.validarToken(null)).thenThrow(new ErrorAcceso(401,"Sesión inválida"));
            var req=mock(ContainerRequestContext.class);var uri=mock(UriInfo.class);
            when(req.getUriInfo()).thenReturn(uri);when(uri.getPath()).thenReturn("chofer/guias");
            when(req.getMethod()).thenReturn("GET");
            var filtro=new AuthFilter();
            for(var e:java.util.Map.of("auth",auth,"http",http).entrySet()) {
                Field f=AuthFilter.class.getDeclaredField(e.getKey());f.setAccessible(true);f.set(filtro,e.getValue());
            }
            filtro.filter(req);
            var respuesta=ArgumentCaptor.forClass(Response.class);verify(req).abortWith(respuesta.capture());
            assertEquals(401,respuesta.getValue().getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE,respuesta.getValue().getMediaType());
        } finally {
            if(anterior==null)System.clearProperty("p1.entorno.local");else System.setProperty("p1.entorno.local",anterior);
        }
    }
}
