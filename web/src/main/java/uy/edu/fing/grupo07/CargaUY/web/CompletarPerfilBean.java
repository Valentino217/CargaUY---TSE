package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
import uy.edu.fing.grupo07.CargaUY.security.SesionesService;

@Named("completarPerfilBean") @ViewScoped
public class CompletarPerfilBean implements Serializable {
    private static final long serialVersionUID=1L;
    private static final Logger LOG=Logger.getLogger(CompletarPerfilBean.class.getName());
    @EJB private SesionesService sesiones;
    private LocalDate fechaNacimiento;

    public void guardar() throws IOException {
        try {
            sesiones.completarNacimiento(SesionActual.token(),fechaNacimiento);
        } catch (ErrorAcceso e) { mensaje(e.getMessage());return; }
          catch (RuntimeException e) {
            LOG.log(Level.SEVERE,"No se pudo completar el nacimiento",e);
            mensaje("No se pudo guardar la fecha. Intentá nuevamente.");return;
        }
        var context=FacesContext.getCurrentInstance();
        context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath()+"/index.xhtml");
    }
    private void mensaje(String texto) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR,"Revisá la fecha",texto));
    }
    public LocalDate getFechaNacimiento(){return fechaNacimiento;}
    public void setFechaNacimiento(LocalDate fecha){fechaNacimiento=fecha;}
    public LocalDate getHoy(){return LocalDate.now();}
}
