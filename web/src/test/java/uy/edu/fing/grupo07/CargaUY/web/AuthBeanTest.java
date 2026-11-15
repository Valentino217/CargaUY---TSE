package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuthBeanTest {

    @Test
    void sinFacesContextRetornaTemaPublico() {
        try (MockedStatic<FacesContext> facesStatic = mockStatic(FacesContext.class)) {
            facesStatic.when(FacesContext::getCurrentInstance).thenReturn(null);
            AuthBean bean = new AuthBean();
            assertEquals("Público", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-ciudadano", bean.getRolThemeClass());
            assertEquals("pi pi-globe", bean.getRolIconClass());
        }
    }

    @Test
    void resuelveEtiquetaTemaEIconoSegunRolYAnonimo() {
        try (MockedStatic<FacesContext> facesStatic = mockStatic(FacesContext.class)) {
            FacesContext faces = mock(FacesContext.class);
            ExternalContext ext = mock(ExternalContext.class);
            HttpServletRequest req = mock(HttpServletRequest.class);
            facesStatic.when(FacesContext::getCurrentInstance).thenReturn(faces);
            when(faces.getExternalContext()).thenReturn(ext);
            when(ext.getRequest()).thenReturn(req);

            AuthBean bean = new AuthBean();
            assertEquals("Público", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-ciudadano", bean.getRolThemeClass());
            assertEquals("pi pi-globe", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(10, 10000001, "Admin", Set.of(TipoUsuario.ADMINISTRADOR)));
            assertEquals("Administrador", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-admin", bean.getRolThemeClass());
            assertEquals("pi pi-cog", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(20, 20000002, "Autoridad", Set.of(TipoUsuario.AUTORIDAD)));
            assertEquals("Autoridad", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-autoridad", bean.getRolThemeClass());
            assertEquals("pi pi-briefcase", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(30, 12345678, "Funcionario", Set.of(TipoUsuario.FUNCIONARIO)));
            assertEquals("Funcionario", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-funcionario", bean.getRolThemeClass());
            assertEquals("pi pi-verified", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(40, 87654321, "Empresa", Set.of(TipoUsuario.CIUDADANO)));
            assertEquals("Ciudadano Empresa", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-empresa", bean.getRolThemeClass());
            assertEquals("pi pi-building", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(43, 45678901, "Comun", Set.of(TipoUsuario.CIUDADANO_COMUN)));
            assertEquals("Ciudadano", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-ciudadano", bean.getRolThemeClass());
            assertEquals("pi pi-user", bean.getRolIconClass());

            when(req.getAttribute("actor")).thenReturn(new Acceso(50, 11223344, "Chofer", Set.of(TipoUsuario.CHOFER)));
            assertEquals("Chofer", bean.getRolPrincipalLabel());
            assertEquals("cuy-rol-ciudadano", bean.getRolThemeClass());
            assertEquals("pi pi-car", bean.getRolIconClass());
        }
    }
}
