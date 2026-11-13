package uy.edu.fing.grupo07.CargaUY.web.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.AutenticacionService;
import uy.edu.fing.grupo07.CargaUY.security.ControlAcceso;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Pruebas Unitarias - AuthFilter (FaaS secret y eventos sin guía por vehículo)")
class AuthFilterTest {

    private AuthFilter crearFiltro(AutenticacionService auth, ControlAcceso control, HttpServletRequest http)
            throws Exception {
        AuthFilter filtro = new AuthFilter();
        for (Map.Entry<String, Object> entry : Map.of(
                "auth", auth,
                "control", control,
                "http", http
        ).entrySet()) {
            Field f = AuthFilter.class.getDeclaredField(entry.getKey());
            f.setAccessible(true);
            f.set(filtro, entry.getValue());
        }
        return filtro;
    }

    @Test
    @DisplayName("POST chofer/eventos/faas admite secreto válido X-FaaS-Secret sin token Bearer")
    void testFaasSecretValidoSinBearer() throws Exception {
        String prevLocal = System.getProperty("p1.entorno.local");
        String prevSecret = System.getProperty("cargauy.faas.secret");
        try {
            System.setProperty("p1.entorno.local", "true");
            System.setProperty("cargauy.faas.secret", "secreto-faas-test-2026");

            HttpServletRequest http = mock(HttpServletRequest.class);
            when(http.getRemoteAddr()).thenReturn("127.0.0.1");
            AutenticacionService auth = mock(AutenticacionService.class);
            ControlAcceso control = mock(ControlAcceso.class);

            ContainerRequestContext req = mock(ContainerRequestContext.class);
            UriInfo uri = mock(UriInfo.class);
            when(req.getUriInfo()).thenReturn(uri);
            when(uri.getPath()).thenReturn("chofer/eventos/faas");
            when(req.getMethod()).thenReturn("POST");
            when(req.getHeaderString("X-FaaS-Secret")).thenReturn("secreto-faas-test-2026");
            when(req.getLength()).thenReturn(512);

            AuthFilter filtro = crearFiltro(auth, control, http);
            filtro.filter(req);

            verify(req, never()).abortWith(any());
            verifyNoInteractions(auth);
        } finally {
            if (prevLocal == null) System.clearProperty("p1.entorno.local");
            else System.setProperty("p1.entorno.local", prevLocal);
            if (prevSecret == null) System.clearProperty("cargauy.faas.secret");
            else System.setProperty("cargauy.faas.secret", prevSecret);
        }
    }

    @Test
    @DisplayName("POST chofer/eventos/faas con secreto válido pero carga > 1 MiB responde 413")
    void testFaasSecretValidoCargaExcesiva413() throws Exception {
        String prevLocal = System.getProperty("p1.entorno.local");
        String prevSecret = System.getProperty("cargauy.faas.secret");
        try {
            System.setProperty("p1.entorno.local", "true");
            System.setProperty("cargauy.faas.secret", "secreto-faas-test-2026");

            HttpServletRequest http = mock(HttpServletRequest.class);
            when(http.getRemoteAddr()).thenReturn("127.0.0.1");
            AutenticacionService auth = mock(AutenticacionService.class);
            ControlAcceso control = mock(ControlAcceso.class);

            ContainerRequestContext req = mock(ContainerRequestContext.class);
            UriInfo uri = mock(UriInfo.class);
            when(req.getUriInfo()).thenReturn(uri);
            when(uri.getPath()).thenReturn("chofer/eventos/faas");
            when(req.getMethod()).thenReturn("POST");
            when(req.getHeaderString("X-FaaS-Secret")).thenReturn("secreto-faas-test-2026");
            when(req.getLength()).thenReturn(1_048_577);

            AuthFilter filtro = crearFiltro(auth, control, http);
            filtro.filter(req);

            ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
            verify(req).abortWith(captor.capture());
            assertEquals(413, captor.getValue().getStatus());
        } finally {
            if (prevLocal == null) System.clearProperty("p1.entorno.local");
            else System.setProperty("p1.entorno.local", prevLocal);
            if (prevSecret == null) System.clearProperty("cargauy.faas.secret");
            else System.setProperty("cargauy.faas.secret", prevSecret);
        }
    }

    @Test
    @DisplayName("CHOFER en sync sin guía valida vehiculoChofer y rechaza con 403 matrícula ajena")
    void testChoferSyncSinGuiaValidaVehiculoChofer() throws Exception {
        String prevLocal = System.getProperty("p1.entorno.local");
        try {
            System.setProperty("p1.entorno.local", "true");
            HttpServletRequest http = mock(HttpServletRequest.class);
            when(http.getRemoteAddr()).thenReturn("127.0.0.1");

            AutenticacionService auth = mock(AutenticacionService.class);
            Acceso chofer = new Acceso(7, 45678901, "Chofer", Set.of(TipoUsuario.CHOFER));
            when(auth.validarToken("tok-chofer")).thenReturn(chofer);

            ControlAcceso control = mock(ControlAcceso.class);
            doThrow(new ErrorAcceso(403, "El vehículo no pertenece a una empresa vinculada al chofer autenticado"))
                    .when(control).vehiculoChofer("tok-chofer", "AJENA99");

            ContainerRequestContext req = mock(ContainerRequestContext.class);
            UriInfo uri = mock(UriInfo.class);
            when(req.getUriInfo()).thenReturn(uri);
            when(uri.getPath()).thenReturn("chofer/eventos/sync");
            when(uri.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
            when(req.getMethod()).thenReturn("POST");
            when(req.getHeaderString("Authorization")).thenReturn("Bearer tok-chofer");
            String body = "[{\"uuid\":\"u-1\",\"guiaId\":null,\"matricula\":\"AJENA99\"}]";
            when(req.getEntityStream()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            AuthFilter filtro = crearFiltro(auth, control, http);
            filtro.filter(req);

            verify(control).vehiculoChofer("tok-chofer", "AJENA99");
            ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
            verify(req).abortWith(captor.capture());
            assertEquals(403, captor.getValue().getStatus());
        } finally {
            if (prevLocal == null) System.clearProperty("p1.entorno.local");
            else System.setProperty("p1.entorno.local", prevLocal);
        }
    }

    @Test
    @DisplayName("CHOFER en sync sin guía con vehículo propio vinculado pasa y reinyecta el stream")
    void testChoferSyncSinGuiaVehiculoPropioPasa() throws Exception {
        String prevLocal = System.getProperty("p1.entorno.local");
        try {
            System.setProperty("p1.entorno.local", "true");
            HttpServletRequest http = mock(HttpServletRequest.class);
            when(http.getRemoteAddr()).thenReturn("127.0.0.1");

            AutenticacionService auth = mock(AutenticacionService.class);
            Acceso chofer = new Acceso(7, 45678901, "Chofer", Set.of(TipoUsuario.CHOFER));
            when(auth.validarToken("tok-chofer")).thenReturn(chofer);

            ControlAcceso control = mock(ControlAcceso.class);
            doNothing().when(control).vehiculoChofer("tok-chofer", "PROPIA1");

            ContainerRequestContext req = mock(ContainerRequestContext.class);
            UriInfo uri = mock(UriInfo.class);
            when(req.getUriInfo()).thenReturn(uri);
            when(uri.getPath()).thenReturn("chofer/eventos/sync");
            when(uri.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
            when(req.getMethod()).thenReturn("POST");
            when(req.getHeaderString("Authorization")).thenReturn("Bearer tok-chofer");
            String body = "[{\"uuid\":\"u-2\",\"guiaId\":null,\"matricula\":\"PROPIA1\"}]";
            when(req.getEntityStream()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            AuthFilter filtro = crearFiltro(auth, control, http);
            filtro.filter(req);

            verify(control).vehiculoChofer("tok-chofer", "PROPIA1");
            verify(req, never()).abortWith(any());
            verify(req).setEntityStream(any(ByteArrayInputStream.class));
            assertTrue(true);
            assertNotNull(req);
        } finally {
            if (prevLocal == null) System.clearProperty("p1.entorno.local");
            else System.setProperty("p1.entorno.local", prevLocal);
        }
    }
}
