package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService;
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService.RubroVista;
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService.TipoCargaVista;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Managed Bean dedicado exclusivamente a la gestión de catálogos maestros
 * (/admin/catalogos.xhtml): Rubros y Tipos de Carga.
 * Desacoplado del monitor de nodos periféricos para carga instantánea.
 */
@Named("adminCatalogosBean")
@ViewScoped
public class AdminCatalogosBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionPlataformaService plataformaService;

    private List<RubroVista> rubros = new ArrayList<>();
    private List<TipoCargaVista> tiposCarga = new ArrayList<>();

    private String nuevoRubroNombre;
    private String nuevoRubroDesc;
    private String nuevoTipoCargaDesc;

    @PostConstruct
    public void init() {
        cargarCatalogos();
    }

    public void cargarCatalogos() {
        try {
            String token = SesionActual.token();
            rubros = new ArrayList<>(plataformaService.listarRubros(token));
            tiposCarga = new ArrayList<>(plataformaService.listarTiposCarga(token));
        } catch (Exception e) {
            rubros = new ArrayList<>();
            tiposCarga = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al cargar catálogos", e.getMessage());
        }
    }

    public void crearRubro() {
        if (nuevoRubroNombre == null || nuevoRubroNombre.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Validación", "El nombre del rubro es obligatorio.");
            return;
        }
        try {
            plataformaService.crearRubro(SesionActual.token(), nuevoRubroNombre.trim(), nuevoRubroDesc != null ? nuevoRubroDesc.trim() : "");
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
            plataformaService.crearTipoCarga(SesionActual.token(), nuevoTipoCargaDesc.trim());
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

    public List<RubroVista> getRubros() {
        return rubros;
    }

    public List<TipoCargaVista> getTiposCarga() {
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
