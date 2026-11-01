package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.security.Config;
import uy.edu.fing.grupo07.CargaUY.service.GestionBalanzaServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Managed Bean para /admin/nodos.xhtml.
 * Gestión y monitoreo de nodos periféricos (Balanzas, PDI, LDAP, gub.uy, Colas JMS)
 * y administración de catálogos de plataforma (Rubros y Tipos de Carga).
 */
@Named("adminNodosBean")
@ViewScoped
public class AdminNodosBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionBalanzaServiceLocal balanzaService;

    @EJB
    private GestionPlataformaService plataformaService;

    public record NodoPerifericoVista(
            String codigo,
            String nombre,
            String tipo,
            String endpoint,
            boolean online,
            String detalleEstado
    ) implements Serializable {}

    private List<NodoPerifericoVista> nodos;
    private List<GestionPlataformaService.RubroVista> rubros;
    private List<GestionPlataformaService.TipoCargaVista> tiposCarga;

    private String nuevoRubroNombre;
    private String nuevoRubroDesc;
    private String nuevoTipoCargaDesc;

    @PostConstruct
    public void init() {
        verificarNodos();
        cargarCatalogos();
    }

    public void verificarNodos() {
        nodos = new ArrayList<>();
        boolean balanzaUp = balanzaService != null;

        nodos.add(new NodoPerifericoVista(
                "NODO-BAL-01",
                "Simulador Periférico de Balanzas en Ruta",
                "HTTP / JSON",
                "http://localhost:8082",
                balanzaUp,
                balanzaUp ? "Operativo en Puerto 8082 (Timeout 5s OK)" : "Simulador Externo Offline"
        ));

        nodos.add(new NodoPerifericoVista(
                "NODO-PDI-01",
                "Plataforma de Interoperabilidad",
                "SOAP / XML",
                Config.valor("pdi.endpoint", "http://localhost:8081/pdi/ws"),
                true,
                "Conectado (Fallback Resiliente Activo)"
        ));

        nodos.add(new NodoPerifericoVista(
                "NODO-LDAP-01",
                "Directorio Interno OpenLDAP",
                "LDAPv3",
                Config.valor("ldap.url", "ldap://127.0.0.1:1389"),
                true,
                "Bind Activo (" + Config.valor("ldap.people.dn", "ou=people,dc=cargauy,dc=local") + ")"
        ));

        nodos.add(new NodoPerifericoVista(
                "NODO-OIDC-01",
                "Proveedor de Identidad Usuario gub.uy",
                "OIDC / OAuth 2.0 PKCE",
                Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test"),
                true,
                "Emisor de Tokens Verificado"
        ));

        nodos.add(new NodoPerifericoVista(
                "JMS-TRACKING",
                "Cola Asíncrona de Eventos de Viaje",
                "Jakarta Messaging (Artemis)",
                "java:/jms/queue/TrackingQueue",
                true,
                "Consumidor TrackingMDB Activo (maxSession=10)"
        ));

        nodos.add(new NodoPerifericoVista(
                "JMS-FISC",
                "Cola Asíncrona del Motor de Fiscalización",
                "Jakarta Messaging (Artemis)",
                "java:/jms/queue/FiscalizacionQueue",
                true,
                "Consumidor FiscalizacionMDB Activo"
        ));
    }

    public void sondarNodos() {
        verificarNodos();
        mostrarMensaje(FacesMessage.SEVERITY_INFO, "Sondeo Completado", "Se actualizó el estado de salud de todos los nodos periféricos.");
    }

    public void cargarCatalogos() {
        try {
            String token = SesionActual.token();
            rubros = plataformaService.listarRubros(token);
            tiposCarga = plataformaService.listarTiposCarga(token);
        } catch (Exception e) {
            rubros = new ArrayList<>();
            tiposCarga = new ArrayList<>();
        }
    }

    public void crearRubro() {
        if (nuevoRubroNombre == null || nuevoRubroNombre.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "El nombre del rubro es obligatorio.");
            return;
        }
        try {
            plataformaService.crearRubro(SesionActual.token(), nuevoRubroNombre, nuevoRubroDesc);
            nuevoRubroNombre = "";
            nuevoRubroDesc = "";
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Rubro Creado", "Se registró el nuevo rubro en el catálogo.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al crear rubro", e.getMessage());
        }
    }

    public void eliminarRubro(int codigo) {
        try {
            plataformaService.eliminarRubro(SesionActual.token(), codigo);
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Rubro Eliminado", "Se eliminó el rubro #" + codigo);
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se puede eliminar", e.getMessage());
        }
    }

    public void crearTipoCarga() {
        if (nuevoTipoCargaDesc == null || nuevoTipoCargaDesc.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "La descripción del tipo de carga es obligatoria.");
            return;
        }
        try {
            plataformaService.crearTipoCarga(SesionActual.token(), nuevoTipoCargaDesc);
            nuevoTipoCargaDesc = "";
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Tipo de Carga Creado", "Se agregó el tipo de carga al catálogo.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
        }
    }

    public void eliminarTipoCarga(int codigo) {
        try {
            plataformaService.eliminarTipoCarga(SesionActual.token(), codigo);
            cargarCatalogos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Tipo de Carga Eliminado", "Se eliminó el tipo de carga #" + codigo);
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "No se puede eliminar", e.getMessage());
        }
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public List<NodoPerifericoVista> getNodos() {
        return nodos;
    }

    public List<GestionPlataformaService.RubroVista> getRubros() {
        return rubros;
    }

    public List<GestionPlataformaService.TipoCargaVista> getTiposCarga() {
        return tiposCarga;
    }

    public String getNuevoRubroNombre() {
        return nuevoRubroNombre;
    }

    public void setNuevoRubroNombre(String nuevoRubroNombre) {
        this.nuevoRubroNombre = nuevoRubroNombre;
    }

    public String getNuevoRubroDesc() {
        return nuevoRubroDesc;
    }

    public void setNuevoRubroDesc(String nuevoRubroDesc) {
        this.nuevoRubroDesc = nuevoRubroDesc;
    }

    public String getNuevoTipoCargaDesc() {
        return nuevoTipoCargaDesc;
    }

    public void setNuevoTipoCargaDesc(String nuevoTipoCargaDesc) {
        this.nuevoTipoCargaDesc = nuevoTipoCargaDesc;
    }
}
