package uy.edu.fing.grupo07.CargaUY.web;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.service.GestionInfraccionesServiceLocal;
import uy.edu.fing.grupo07.CargaUY.service.VehiculoEJB;import uy.edu.fing.grupo07.CargaUY.service.ActaServiceEJB;import java.time.LocalDate;import java.time.format.DateTimeFormatter;import org.primefaces.model.file.UploadedFile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;import org.primefaces.model.StreamedContent;import org.primefaces.model.DefaultStreamedContent;import java.io.ByteArrayInputStream;

/**
 * Managed Bean para /empresa/infracciones.xhtml.
 * Permite a la empresa consultar sus infracciones, ver la evidencia técnica y presentar descargos.
 */
@Named("empresaInfraccionesBean")
@ViewScoped
public class EmpresaInfraccionesBean implements Serializable {

    private static final long serialVersionUID = 1L;
 // 1. Inyectas tu EJB    @EJB    private ActaServiceEJB actaEJB;
    @EJB
    private GestionInfraccionesServiceLocal infraccionesService;

    @EJB
    private VehiculoEJB vehiculoEJB;

    private List<InfraccionDTO> infracciones;
    private List<InfraccionDTO> infraccionesFiltradas;
    private InfraccionDTO seleccionada;
    private String textoDescargo;        //campos para cargar el certificado .p12////////////////////////////////////////////////////////////////////////////////            private UploadedFile certificado;    //recibe el archivo .p12 seleccionado desde el diálogo.    private String passwordCertificado;    //recibe la contraseña ingresada por el inspector.//////////////////////////////////////////////////////////////////////    // Variable para la descarga del PDF    private StreamedContent pdfFirmadoDescarga;

    @PostConstruct
    public void init() {
        cargarInfracciones();
    }

    public void cargarInfracciones() {
        try {
            List<InfraccionDTO> todasNotificadas = infraccionesService.listarInfracciones(null, null).stream()
                    .filter(i -> i.estado() != EstadoInfraccion.DETECTADA)
                    .collect(Collectors.toList());
            List<Empresa> misEmpresas = vehiculoEJB.listarEmpresasParaActor(SesionActual.token());
            if (misEmpresas != null && !misEmpresas.isEmpty()) {
                Set<String> nombres = misEmpresas.stream()
                        .map(Empresa::getRazonSocial)
                        .filter(r -> r != null)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
                infracciones = todasNotificadas.stream()
                        .filter(i -> i.nombreEmpresa() != null && nombres.contains(i.nombreEmpresa().toLowerCase()))
                        .collect(Collectors.toList());
                if (infracciones.isEmpty()) {
                    infracciones = todasNotificadas;
                }
            } else {
                infracciones = todasNotificadas;
            }
        } catch (Exception e) {
            infracciones = new ArrayList<>();
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

    public void enviarDescargo() {
        if (seleccionada == null) {
            return;
        }
        if (textoDescargo == null || textoDescargo.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Atención", "Debe redactar el fundamento del descargo.");
            return;
        }
        try {
            seleccionada = infraccionesService.presentarDescargo(seleccionada.idCaso(), new DescargoRequestDTO(textoDescargo.trim()));
            cargarInfracciones();
            mostrarMensaje(FacesMessage.SEVERITY_INFO, "Descargo Presentado",
                    "Su descargo para el expediente #" + seleccionada.idCaso() + " fue registrado exitosamente.");
        } catch (Exception e) {
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error al presentar descargo", e.getMessage());
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
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            fc.addMessage(null, new FacesMessage(sev, res, det));
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
    }        public void setpasswordCertificado(String password) {    	this.passwordCertificado = password;    }
        public String getpasswordCertificado() {    	return this.passwordCertificado;    }        public UploadedFile getCertificado() {        return certificado;    }    public void setCertificado(UploadedFile certificado) {        this.certificado = certificado;    }  
    public EstadoInfraccion[] getEstadosInfraccion() {
        return EstadoInfraccion.values();
    }

    public TipoInfraccion[] getTiposInfraccion() {
        return TipoInfraccion.values();
    }        ////////////////agrego codigo para descargar pdf acta     public StreamedContent emitirYFirmarActa() {           try {                           	      DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");        	       String fechaString =  this.seleccionada.fecha().format(formato);        	      // Le pedimos al EJB que haga el trabajo pesado de PDFBox y Bouncy Castle        	       byte[] pdfOriginal = actaEJB.emitirYFirmarActa(                   this.seleccionada.idCaso(),                   this.seleccionada.nombreEmpresa(),                   this.seleccionada.matricula(),                   fechaString,                   this.seleccionada.tipo().toString(),                   this.seleccionada.evidencia()                   );                                     //abrimos las credenciales        	       CredencialesFirma credenciales = actaEJB.abrirCredenciales(certificado.getContent(), passwordCertificado);                         	      //firmamos el pdf        	               	       byte[] pdfFirmado = actaEJB.firmarPdf(        	    	        pdfOriginal,        	    	        credenciales.getClavePrivada(),        	    	        credenciales.getCertificado()        	    	);                                      // Dejamos el PDF listo                   this.pdfFirmadoDescarga = DefaultStreamedContent.builder()                           .name("Acta_Oficial_" + this.seleccionada.idCaso() + ".pdf")                           .contentType("application/pdf")                           .stream(() -> new ByteArrayInputStream(pdfFirmado))                           .build();                   // RETORNAMOS el archivo para que el navegador lo descargue                   return this.pdfFirmadoDescarga;                                     } catch (Exception e) {                    FacesContext.getCurrentInstance().addMessage(null,                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", e.getMessage()));                                        return null;                 }finally {                        //limpio certificado y password luego de descargar el reporte                	    certificado = null;                	    passwordCertificado = null;                }      }         public StreamedContent getPdfFirmadoDescarga() {        return pdfFirmadoDescarga;     }          public void probarCargaCertificado() {    	    if (certificado == null || certificado.getSize() == 0) {    	        FacesContext.getCurrentInstance().addMessage(null,    	            new FacesMessage(    	                FacesMessage.SEVERITY_ERROR,    	                "Error",    	                "No se recibió ningún certificado."    	            ));    	        return;    	    }else {    	    FacesContext.getCurrentInstance().addMessage(null,    	        new FacesMessage(    	            FacesMessage.SEVERITY_INFO,    	            "Carga correcta",    	            "Archivo: " + certificado.getFileName()    	            + " - Tamaño: " + certificado.getSize() + " bytes"    	        ));    	    }    	        	    //verifico si abre el certificado    	        	    try {    	        byte[] archivo = certificado.getInputStream().readAllBytes();    	        actaEJB.abrirCredenciales(archivo, passwordCertificado);    	        FacesContext.getCurrentInstance().addMessage(null,    	                new FacesMessage("Certificado abierto correctamente"));    	    } catch (Exception e) {    	        FacesContext.getCurrentInstance().addMessage(null,    	                new FacesMessage(FacesMessage.SEVERITY_ERROR,    	                        "Error", "No se pudo abrir el certificado."));    	    }    	        	        	        	        	        	        	        	        	        	        	        	        	}
  }
