package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.primefaces.model.file.UploadedFile;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoCertificado;
import uy.edu.fing.grupo07.CargaUY.security.Acceso;
import uy.edu.fing.grupo07.CargaUY.security.AutenticacionService;
import uy.edu.fing.grupo07.CargaUY.service.CertificadoDigitalEJB;

@Named("certificadoDigitalBean")
@ViewScoped
public class CertificadoDigitalBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    private CertificadoDigitalEJB certificadoDigitalEJB;

    @EJB
    private AutenticacionService auth;

    private UploadedFile certificado;
    private String passwordCertificado;

    private List<CertificadoDigital> certificados;
    private List<CertificadoDigital> certificadosFiltrados;

    private CertificadoDigital certificadoSeleccionado;
    private String motivoRevocacion;

    @PostConstruct
    public void inicializar() {
        cargarCertificados();
    }

    private Acceso resolverUsuarioAutenticado() {
        String token = null;
        try {
            token = SesionActual.token();
        } catch (Throwable ignored) {
        }
        if (token == null && FacesContext.getCurrentInstance() != null) {
            token = (String) FacesContext.getCurrentInstance()
                    .getExternalContext()
                    .getSessionMap()
                    .get("token");
        }
        if (token == null || auth == null) {
            return null;
        }
        return auth.validarToken(token);
    }

    public void cargarCertificados() {
        try {
            Acceso actor = resolverUsuarioAutenticado();
            if (actor == null) {
                certificados = new ArrayList<>();
                return;
            }
            certificados = certificadoDigitalEJB.listarCertificadosPorUsuario(actor.id());
        } catch (Exception e) {
            certificados = new ArrayList<>();
            mostrarMensaje(FacesMessage.SEVERITY_ERROR, "Error", "No se pudieron cargar sus certificados: " + e.getMessage());
        }
    }

    public void registrarMiCertificado() {
        try {
            Acceso actor = resolverUsuarioAutenticado();
            if (actor == null) {
                throw new IllegalStateException("Debe iniciar sesión nuevamente para registrar un certificado.");
            }

            if (certificado == null || certificado.getSize() == 0) {
                throw new IllegalArgumentException("Debe seleccionar un archivo de certificado (.p12 o .pfx).");
            }

            byte[] archivo = certificado.getContent();

            CredencialesFirma credenciales = certificadoDigitalEJB.abrirCredenciales(
                    archivo,
                    passwordCertificado
            );

            X509Certificate x509 = credenciales.getCertificado();
            LocalDateTime vencimiento = certificadoDigitalEJB.obtenerFechaVencimiento(x509);

            certificadoDigitalEJB.registrarCertificado(
                    actor.ci(),
                    archivo,
                    certificado.getFileName(),
                    vencimiento
            );
            cargarCertificados();

            String vencStr = vencimiento != null ? vencimiento.format(FORMATO_FECHA) : "S/D";
            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Certificado registrado",
                    "Su certificado digital se validó y asoció correctamente. Vencimiento: " + vencStr
            );
        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "No se pudo registrar el certificado",
                    e.getMessage()
            );
            marcarValidacionFallida();
        } finally {
            passwordCertificado = null;
            certificado = null;
        }
    }

    public void prepararRevocacion(CertificadoDigital cert) {
        this.certificadoSeleccionado = cert;
        this.motivoRevocacion = "";
    }

    public void confirmarRevocacion() {
        if (certificadoSeleccionado == null) {
            return;
        }
        if (motivoRevocacion == null || motivoRevocacion.isBlank()) {
            mostrarMensaje(FacesMessage.SEVERITY_WARN, "Motivo obligatorio",
                    "Debe especificar el motivo de revocación del certificado.");
            marcarValidacionFallida();
            return;
        }
        revocarCertificado(certificadoSeleccionado.getId(), motivoRevocacion.trim());
    }

    public void revocarCertificado(int idCertificado, String motivo) {
        try {
            Acceso actor = resolverUsuarioAutenticado();
            if (actor == null) {
                throw new IllegalStateException("Debe iniciar sesión nuevamente.");
            }

            certificadoDigitalEJB.revocarCertificadoDeUsuario(idCertificado, actor.id(), motivo);
            cargarCertificados();

            mostrarMensaje(
                    FacesMessage.SEVERITY_INFO,
                    "Certificado revocado",
                    "El certificado #" + idCertificado + " fue revocado correctamente."
            );
        } catch (Exception e) {
            mostrarMensaje(
                    FacesMessage.SEVERITY_ERROR,
                    "Error al revocar",
                    e.getMessage()
            );
            marcarValidacionFallida();
        }
    }

    public CertificadoDigital getCertificadoActivo() {
        if (certificados == null) return null;
        return certificados.stream()
                .filter(c -> c.getEstado() == EstadoCertificado.ACTIVO)
                .findFirst()
                .orElse(null);
    }

    public CertificadoDigital getUltimoCertificadoRevocado() {
        if (certificados == null) return null;
        return certificados.stream()
                .filter(c -> c.getEstado() == EstadoCertificado.REVOCADO)
                .findFirst()
                .orElse(null);
    }

    public int getTotalCertificados() {
        return certificados != null ? certificados.size() : 0;
    }

    public long getTotalActivos() {
        if (certificados == null) return 0;
        return certificados.stream().filter(c -> c.getEstado() == EstadoCertificado.ACTIVO).count();
    }

    public long getTotalRevocados() {
        if (certificados == null) return 0;
        return certificados.stream().filter(c -> c.getEstado() == EstadoCertificado.REVOCADO).count();
    }

    public EstadoCertificado[] getEstadosCertificado() {
        return EstadoCertificado.values();
    }

    private void mostrarMensaje(FacesMessage.Severity sev, String res, String det) {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.addMessage(null, new FacesMessage(sev, res, det));
            }
        } catch (Throwable ignored) {
        }
    }

    private void marcarValidacionFallida() {
        try {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null) {
                fc.validationFailed();
            }
        } catch (Throwable ignored) {
        }
    }

    public List<CertificadoDigital> getCertificados() {
        return certificados;
    }

    public List<CertificadoDigital> getCertificadosFiltrados() {
        return certificadosFiltrados;
    }

    public void setCertificadosFiltrados(List<CertificadoDigital> certificadosFiltrados) {
        this.certificadosFiltrados = certificadosFiltrados;
    }

    public String getPasswordCertificado() {
        return passwordCertificado;
    }

    public void setPasswordCertificado(String passwordCertificado) {
        this.passwordCertificado = passwordCertificado;
    }

    public UploadedFile getCertificado() {
        return certificado;
    }

    public void setCertificado(UploadedFile certificado) {
        this.certificado = certificado;
    }

    public CertificadoDigital getCertificadoSeleccionado() {
        return certificadoSeleccionado;
    }

    public void setCertificadoSeleccionado(CertificadoDigital certificadoSeleccionado) {
        this.certificadoSeleccionado = certificadoSeleccionado;
    }

    public String getMotivoRevocacion() {
        return motivoRevocacion;
    }

    public void setMotivoRevocacion(String motivoRevocacion) {
        this.motivoRevocacion = motivoRevocacion;
    }
}
