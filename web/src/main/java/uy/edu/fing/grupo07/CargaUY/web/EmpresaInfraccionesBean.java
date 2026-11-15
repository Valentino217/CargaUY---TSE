package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
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
 * Managed Bean para /empresa/infracciones.xhtml.
 * Permite a la empresa consultar exclusivamente sus propias infracciones notificadas,
 * ver la evidencia técnica, presentar descargos con control de acceso por empresa,
 * y emitir/firmar actas oficiales de infracción en PDF.
 */
@Named("empresaInfraccionesBean")
@ViewScoped
public class EmpresaInfraccionesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    @EJB
    private ActaServiceEJB actaEJB;
    
    @EJB
    private CertificadoDigitalEJB certificadoEJB;
    
    @EJB
    private AutenticacionService auth;

    private List<InfraccionDTO> infracciones;
    private List<InfraccionDTO> infraccionesFiltradas;
    private InfraccionDTO seleccionada;
    private String textoDescargo;


    private String passwordCertificado;
    private StreamedContent pdfFirmadoDescarga;

    public EmpresaInfraccionesBean() {
    }

    public EmpresaInfraccionesBean(GestionInfraccionesServiceLocal infraccionesService) {
        this.infraccionesService = infraccionesService;
    }

    public EmpresaInfraccionesBean(GestionInfraccionesServiceLocal infraccionesService, ActaServiceEJB actaEJB) {
        this.infraccionesService = infraccionesService;
        this.actaEJB = actaEJB;
    }

    @PostConstruct
    public void init() {
        cargarInfracciones();
    }

    public void cargarInfracciones() {
        try {
            this.infracciones = infraccionesService.listarInfraccionesParaEmpresa(SesionActual.token());
        } catch (Exception e) {
            this.infracciones = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar las infracciones: " + e.getMessage());
        }
    }

    public void seleccionarParaDescargo(InfraccionDTO inf) {
        this.seleccionada = inf;
        this.textoDescargo = inf != null && inf.textoDescargo() != null ? inf.textoDescargo() : "";
    }

    public boolean puedePresentarDescargo(InfraccionDTO inf) {
        if (inf == null) {
            return false;
        }
        return inf.estado() == EstadoInfraccion.NOTIFICADA
                || inf.estado() == EstadoInfraccion.EN_DESCARGO;
    }

    public void presentarDescargo() {
        enviarDescargo();
    }

    public void enviarDescargo() {
        if (seleccionada == null) {
            return;
        }
        if (textoDescargo == null || textoDescargo.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Atención", "Debe redactar el fundamento del descargo.");
            marcarValidacionFallida();
            return;
        }
        try {
            boolean yaExistia = seleccionada.textoDescargo() != null && !seleccionada.textoDescargo().isBlank();
            seleccionada = infraccionesService.presentarDescargo(
                    SesionActual.token(),
                    seleccionada.idCaso(),
                    new DescargoRequestDTO(textoDescargo.trim())
            );
            cargarInfracciones();
            mostrarMensaje(FacesMessage.SEVERITY_INFO,
                    yaExistia ? "Descargo Editado" : "Descargo Presentado",
                    yaExistia
                            ? "Su descargo para el expediente #" + seleccionada.idCaso() + " fue actualizado exitosamente."
                            : "Su descargo para el expediente #" + seleccionada.idCaso() + " fue registrado exitosamente.");
        } catch (Exception e) {
            cargarInfracciones();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al presentar descargo", e.getMessage());
            marcarValidacionFallida();
        }
    }

    public StreamedContent emitirYFirmarActa() {
        if (seleccionada == null || actaEJB == null) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Atención", "Debe seleccionar una infracción.");
            return null;
        }
        try {
            
        	// 1. Obtener token de sesión
        	String token = (String) FacesContext
        	        .getCurrentInstance()
        	        .getExternalContext()
        	        .getSessionMap()
        	        .get("token");

        	// 2. Validar token y obtener usuario
        	Acceso acceso = auth.validarToken(token);

        	if (acceso == null) {
        	    mostrarMensaje(
        	        FacesMessage.SEVERITY_ERROR,
        	        "Sesión inválida",
        	        "Debe iniciar sesión nuevamente."
        	    );
        	    return null;
        	}

        	// 3. Obtener CI del inspector
        	int ciInspector = acceso.ci();
        	       	     	
        	// 1. Recuperar certificado válido desde PostgreSQL
        	CertificadoDigital certificadoDB = certificadoEJB.buscarCertificadoActivoPorCi(token, ciInspector);

        	if (certificadoDB == null) {
        	    mostrarMensaje(
        	        FacesMessage.SEVERITY_WARN,
        	        "Atención",
        	        "No dispone de un certificado activo y vigente."
        	    );
        	    return null;
        	}

        	// 2. Obtener archivo P12 almacenado
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
            String fechaString = this.seleccionada.fecha() != null ? this.seleccionada.fecha().format(formato) : "";

            byte[] pdfOriginal = actaEJB.emitirYFirmarActa(
                    token,
                    this.seleccionada.idCaso(),
                    this.seleccionada.nombreEmpresa(),
                    this.seleccionada.matricula(),
                    fechaString,
                    this.seleccionada.tipo() != null ? this.seleccionada.tipo().toString() : "",
                    this.seleccionada.evidencia()
            );

            if (pdfOriginal == null) {
                mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudo generar el documento PDF base.");
                return null;
            }
            

            CredencialesFirma credenciales = actaEJB.abrirCredenciales(token, archivoCertificado, passwordCertificado);
            if (credenciales == null) {
                mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error de Firma", "No se pudo abrir el certificado con la clave provista.");
                return null;
            }

            byte[] pdfFirmado = actaEJB.firmarPdf(
                    token,
                    pdfOriginal,
                    credenciales.getClavePrivada(),
                    credenciales.getCertificado()
            );

            this.pdfFirmadoDescarga = DefaultStreamedContent.builder()
                    .name("Acta_Oficial_" + this.seleccionada.idCaso() + ".pdf")
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

    public long getTotalActivas() {
        if (infracciones == null) return 0;
        return infracciones.stream()
                .filter(i -> i.estado() == EstadoInfraccion.NOTIFICADA)
                .count();
    }

    public long getTotalEnDescargo() {
        if (infracciones == null) return 0;
        return infracciones.stream().filter(i -> i.estado() == EstadoInfraccion.EN_DESCARGO).count();
    }

    public long getTotalConfirmadas() {
        if (infracciones == null) return 0;
        return infracciones.stream().filter(i -> i.estado() == EstadoInfraccion.CONFIRMADA).count();
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

    public List<InfraccionDTO> getInfracciones() {
        return infracciones;
    }

    public List<InfraccionDTO> getInfraccionesFiltradas() {
        return infraccionesFiltradas;
    }

    public void setInfraccionesFiltradas(List<InfraccionDTO> infraccionesFiltradas) {
        this.infraccionesFiltradas = infraccionesFiltradas;
    }

    public InfraccionDTO getSeleccionada() {
        return seleccionada;
    }

    public void setSeleccionada(InfraccionDTO seleccionada) {
        this.seleccionada = seleccionada;
    }

    public String getTextoDescargo() {
        return textoDescargo;
    }

    public void setTextoDescargo(String textoDescargo) {
        this.textoDescargo = textoDescargo;
    }

    public String getPasswordCertificado() {
        return passwordCertificado;
    }

    public void setPasswordCertificado(String passwordCertificado) {
        this.passwordCertificado = passwordCertificado;
    }

    public String getpasswordCertificado() {
        return passwordCertificado;
    }

    public void setpasswordCertificado(String passwordCertificado) {
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
