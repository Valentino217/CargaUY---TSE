package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.AutenticacionService;
import uy.edu.fing.grupo07.CargaUY.service.ActaServiceEJB;
import uy.edu.fing.grupo07.CargaUY.service.CertificadoDigitalEJB;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Managed Bean para /funcionario/bandeja-fiscalizacion.xhtml.
 * Bandeja interactiva de evaluación de evidencia, revisión de descargos, resolución administrativa
 * y emisión de actas firmadas digitalmente con control de acceso por sesión autenticada (Punto D.5).
 */
@Named("bandejaFiscalizacionBean")
@ViewScoped
public class BandejaFiscalizacionBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    @EJB
    private ActaServiceEJB actaEJB;

    @EJB
    private CertificadoDigitalEJB certificadoEJB;

    @EJB
    private AutenticacionService auth;

    private List<InfraccionDTO> casos;
    private List<InfraccionDTO> casosFiltrados;
    private InfraccionDTO casoSeleccionado;

    private String fundamentosResolucion;
    private String passwordCertificado;
    private StreamedContent pdfFirmadoDescarga;

    public BandejaFiscalizacionBean() {
    }

    public BandejaFiscalizacionBean(GestionInfraccionesServiceLocal infraccionesService) {
        this.infraccionesService = infraccionesService;
    }

    @PostConstruct
    public void init() {
        cargarCasos();
    }

    public void cargarCasos() {
        try {
            this.casos = infraccionesService.listarInfracciones(SesionActual.token(), null, null);
        } catch (Exception e) {
            this.casos = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar los casos: " + e.getMessage());
        }
    }

    public void seleccionarCaso(InfraccionDTO caso) {
        this.casoSeleccionado = caso;
        this.fundamentosResolucion = caso != null && caso.fundamento() != null ? caso.fundamento() : "";
        this.passwordCertificado = null;
    }

    public boolean isCasoResuelto() {
        if (casoSeleccionado == null) return true;
        return casoSeleccionado.estado() == EstadoInfraccion.CONFIRMADA
                || casoSeleccionado.estado() == EstadoInfraccion.DESESTIMADA;
    }

    public void notificarEmpresa() {
        if (casoSeleccionado == null) return;
        try {
            casoSeleccionado = infraccionesService.notificarInfraccion(
                    SesionActual.token(),
                    casoSeleccionado.idCaso()
            );
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Empresa Notificada",
                    "Se envió notificación oficial a la empresa para el caso #" + casoSeleccionado.idCaso());
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al notificar", e.getMessage());
        }
    }

    public void confirmarSancion() {
        if (casoSeleccionado == null) return;
        if (fundamentosResolucion == null || fundamentosResolucion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Fundamento Obligatorio",
                    "Debe ingresar los fundamentos técnicos/legales para confirmar la sanción.");
            marcarValidacionFallida();
            return;
        }
        if (casoSeleccionado.estado() == EstadoInfraccion.DETECTADA) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Notificación Requerida",
                    "Debe notificar formalmente a la empresa antes de poder confirmar una sanción.");
            marcarValidacionFallida();
            return;
        }
        try {
            casoSeleccionado = infraccionesService.confirmarInfraccion(
                    SesionActual.token(),
                    casoSeleccionado.idCaso(),
                    fundamentosResolucion.trim()
            );
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Sanción Confirmada",
                    "El caso #" + casoSeleccionado.idCaso() + " quedó CONFIRMADO y sellado en auditoría inmutable.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al confirmar sanción", e.getMessage());
            marcarValidacionFallida();
        }
    }

    public void desestimarCaso() {
        if (casoSeleccionado == null) return;
        if (fundamentosResolucion == null || fundamentosResolucion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Fundamento Obligatorio",
                    "Debe ingresar el motivo de desestimación para el registro de auditoría.");
            marcarValidacionFallida();
            return;
        }
        try {
            casoSeleccionado = infraccionesService.desestimarInfraccion(
                    SesionActual.token(),
                    casoSeleccionado.idCaso(),
                    fundamentosResolucion.trim()
            );
            cargarCasos();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Caso Desestimado",
                    "El expediente #" + casoSeleccionado.idCaso() + " fue DESESTIMADO y archivado.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al desestimar", e.getMessage());
            marcarValidacionFallida();
        }
    }

    public StreamedContent emitirYFirmarActa() {
        if (casoSeleccionado == null || actaEJB == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Atención", "Debe seleccionar una infracción.");
            return null;
        }
        try {
            String token = SesionActual.token();
            if (token == null && FacesContext.getCurrentInstance() != null) {
                token = (String) FacesContext.getCurrentInstance()
                        .getExternalContext()
                        .getSessionMap()
                        .get("token");
            }

            Acceso acceso = auth != null ? auth.validarToken(token) : null;
            if (acceso == null) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_ERROR,
                        "Sesión inválida",
                        "Debe iniciar sesión nuevamente."
                );
                return null;
            }

            int ciInspector = acceso.ci();
            CertificadoDigital certificadoDB = certificadoEJB.buscarCertificadoActivoPorCi(ciInspector);

            if (certificadoDB == null) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_WARN,
                        "Atención",
                        "No dispone de un certificado activo y vigente."
                );
                return null;
            }

            byte[] archivoCertificado = certificadoDB.getArchivoCertificado();
            if (archivoCertificado == null || archivoCertificado.length == 0) {
                mostrarMensaje(
                        FacesMessage.SEVERITY_ERROR,
                        "Error",
                        "El certificado registrado no contiene información."
                );
                return null;
            }

            DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String fechaString = this.casoSeleccionado.fecha() != null
                    ? this.casoSeleccionado.fecha().format(formato)
                    : "";

            byte[] pdfOriginal = actaEJB.emitirYFirmarActa(
                    this.casoSeleccionado.idCaso(),
                    this.casoSeleccionado.nombreEmpresa(),
                    this.casoSeleccionado.matricula(),
                    fechaString,
                    this.casoSeleccionado.tipo() != null ? this.casoSeleccionado.tipo().toString() : "",
                    this.casoSeleccionado.evidencia()
            );

            if (pdfOriginal == null) {
                mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo generar el documento PDF base.");
                return null;
            }

            CredencialesFirma credenciales = actaEJB.abrirCredenciales(archivoCertificado, passwordCertificado);
            if (credenciales == null) {
                mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error de Firma", "No se pudo abrir el certificado con la clave provista.");
                return null;
            }

            byte[] pdfFirmado = actaEJB.firmarPdf(
                    pdfOriginal,
                    credenciales.getClavePrivada(),
                    credenciales.getCertificado()
            );

            this.pdfFirmadoDescarga = DefaultStreamedContent.builder()
                    .name("Acta_Oficial_" + this.casoSeleccionado.idCaso() + ".pdf")
                    .contentType("application/pdf")
                    .stream(() -> new ByteArrayInputStream(pdfFirmado))
                    .build();

            return this.pdfFirmadoDescarga;
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage());
            return null;
        } finally {
            passwordCertificado = null;
        }
    }

    private void marcarValidacionFallida() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.validationFailed();
            }
        } catch (Throwable ignored) {
            // Entorno fuera de contenedor JSF (tests unitarios)
        }
    }

    public int getTotalCasos() {
        return casos != null ? casos.size() : 0;
    }

    public long getTotalPendientesRevision() {
        if (casos == null) return 0;
        return casos.stream()
                .filter(c -> c.estado() == EstadoInfraccion.DETECTADA
                        || c.estado() == EstadoInfraccion.NOTIFICADA
                        || c.estado() == EstadoInfraccion.EN_DESCARGO)
                .count();
    }

    public long getTotalConfirmadas() {
        if (casos == null) return 0;
        return casos.stream().filter(c -> c.estado() == EstadoInfraccion.CONFIRMADA).count();
    }

    public long getTotalDesestimadas() {
        if (casos == null) return 0;
        return casos.stream().filter(c -> c.estado() == EstadoInfraccion.DESESTIMADA).count();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.addMessage(null, new FacesMessage(sev, res, det));
            }
        } catch (Throwable ignored) {
            // Entorno fuera de contenedor JSF (tests unitarios)
        }
    }

    public List<InfraccionDTO> getCasos() {
        return casos;
    }

    public List<InfraccionDTO> getCasosFiltrados() {
        return casosFiltrados;
    }

    public void setCasosFiltrados(List<InfraccionDTO> casosFiltrados) {
        this.casosFiltrados = casosFiltrados;
    }

    public InfraccionDTO getCasoSeleccionado() {
        return casoSeleccionado;
    }

    public void setCasoSeleccionado(InfraccionDTO casoSeleccionado) {
        this.casoSeleccionado = casoSeleccionado;
    }

    public String getFundamentosResolucion() {
        return fundamentosResolucion;
    }

    public void setFundamentosResolucion(String fundamentosResolucion) {
        this.fundamentosResolucion = fundamentosResolucion;
    }

    public String getPasswordCertificado() {
        return passwordCertificado;
    }

    public void setPasswordCertificado(String passwordCertificado) {
        this.passwordCertificado = passwordCertificado;
    }

    public StreamedContent getPdfFirmadoDescarga() {
        return pdfFirmadoDescarga;
    }

    public EstadoInfraccion[] getEstadosInfraccion() {
        return EstadoInfraccion.values();
    }

    public TipoInfraccion[] getTiposInfraccion() {
        return TipoInfraccion.values();
    }
}
