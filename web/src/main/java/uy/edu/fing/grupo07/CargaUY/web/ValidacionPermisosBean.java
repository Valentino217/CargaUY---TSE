package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import uy.edu.fing.grupo07.CargaUY.dto.ValidacionEmpresaDTO;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.service.ValidacionPermisosService;

@Named("validacionPermisosBean")
@ViewScoped
public class ValidacionPermisosBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ValidacionPermisosBean.class.getName());

    @EJB private ValidacionPermisosService servicio;
    private Integer numeroEmpresa;
    private ValidacionEmpresaDTO resultado;
    private List<ValidacionEmpresaDTO.Unidad> unidades = new ArrayList<>();

    public void consultar() {
        resultado = null;
        unidades.clear();
        try {
            if (numeroEmpresa == null || numeroEmpresa <= 0) {
                mensaje("Ingresá un número de empresa válido");
                return;
            }
            resultado = servicio.consultar(SesionActual.token(), numeroEmpresa);
            unidades.addAll(resultado.unidades());
        } catch (ErrorAcceso e) {
            mensaje(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo validar la empresa " + numeroEmpresa, e);
            mensaje("Consulta temporalmente no disponible. Intentá nuevamente.");
        }
    }

    private void mensaje(String texto) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, "Consulta de permisos", texto));
    }

    public Integer getNumeroEmpresa() { return numeroEmpresa; }
    public void setNumeroEmpresa(Integer numeroEmpresa) { this.numeroEmpresa = numeroEmpresa; }
    public ValidacionEmpresaDTO getResultado() { return resultado; }
    public List<ValidacionEmpresaDTO.Unidad> getUnidades() { return unidades; }
}
