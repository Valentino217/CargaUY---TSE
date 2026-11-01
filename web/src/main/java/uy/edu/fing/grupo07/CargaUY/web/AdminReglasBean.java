package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.service.GestionPlataformaService;

import java.io.Serializable;

/**
 * Managed Bean para /admin/reglas-fiscalizacion.xhtml.
 * Parametrización en caliente de umbrales de exceso de carga (kg) y desvío de ruta (km).
 */
@Named("adminReglasBean")
@ViewScoped
public class AdminReglasBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionPlataformaService plataformaService;

    private boolean reglaExistente;
    private int cargaKg = 500;
    private double desvioKm = 2.0;
    private long version = 0;

    @PostConstruct
    public void init() {
        cargarReglaActual();
    }

    public void cargarReglaActual() {
        try {
            GestionPlataformaService.ReglaVista r = plataformaService.consultarRegla(SesionActual.token());
            if (r != null) {
                this.reglaExistente = true;
                this.cargaKg = r.cargaKg();
                this.desvioKm = r.desvioKm();
                this.version = r.version();
            }
        } catch (Exception e) {
            this.reglaExistente = false;
            this.cargaKg = 500;
            this.desvioKm = 2.0;
            this.version = 0;
        }
    }

    public void guardarReglas() {
        try {
            String token = SesionActual.token();
            if (reglaExistente) {
                plataformaService.editarRegla(token, cargaKg, desvioKm, version);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Umbrales Actualizados en Caliente",
                        "Tolerancia Carga: " + cargaKg + " kg | Tolerancia Desvío: " + desvioKm + " km.");
            } else {
                plataformaService.crearRegla(token, cargaKg, desvioKm);
                mostrarMensaje(FacesMessage.SEVERITY_INFO, "Regla Inicializada",
                        "Se registraron los umbrales iniciales del motor de fiscalización.");
            }
            cargarReglaActual();
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al parametrizar reglas", e.getMessage());
        }
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
        }
    }

    public boolean isReglaExistente() {
        return reglaExistente;
    }

    public int getCargaKg() {
        return cargaKg;
    }

    public void setCargaKg(int cargaKg) {
        this.cargaKg = cargaKg;
    }

    public double getDesvioKm() {
        return desvioKm;
    }

    public void setDesvioKm(double desvioKm) {
        this.desvioKm = desvioKm;
    }

    public long getVersion() {
        return version;
    }
}
