package uy.edu.fing.grupo07.CargaUY.web;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;

import org.primefaces.model.file.UploadedFile;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CredencialesFirma;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.service.CertificadoDigitalEJB;
import uy.edu.fing.grupo07.CargaUY.domain.entity.CertificadoDigital;

@Named("certificadoDigitalBean")
@ViewScoped
public class CertificadoDigitalBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private CertificadoDigitalEJB certificadoDigitalEJB;

    private int ci;

    private String mensaje;

    private Usuario usuarioEncontrado;
    
    private UploadedFile certificado;

    private String passwordCertificado;
    
    private List<CertificadoDigital> certificados;
    
    private String motivoRevocacion;
    
    @PostConstruct
    public void inicializar() {
        cargarCertificados();
    }

    public void cargarCertificados() {
        certificados = certificadoDigitalEJB.listarCertificados();
    }

    public void revocarCertificado(int idCertificado,String motivo) {
        try {
            certificadoDigitalEJB.revocarCertificado(
                idCertificado,
                motivo
            );

            cargarCertificados();

            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_INFO,
                    "Certificado revocado",
                    "El certificado fue revocado correctamente."
                )
            );

        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Error",
                    "No se pudo revocar el certificado: " + e.getMessage()
                )
            );
        }
    }

    public List<CertificadoDigital> getCertificados() {
        return certificados;
    }

    public void buscarUsuario() {
        try {
            usuarioEncontrado = certificadoDigitalEJB.buscarUsuarioPorCi(ci);

            if (usuarioEncontrado != null) {
                mensaje = "Usuario encontrado: "
                        + usuarioEncontrado.getNombre();
            } else {
                mensaje = "No existe un usuario con esa cédula.";
            }

        } catch (Exception e) {
            mensaje = "Error al buscar usuario: " + e.getMessage();
            e.printStackTrace();
        }
    }
    
    public void probarRegistro() {
        try {
            certificadoDigitalEJB.registrarCertificado(
                ci,
                new byte[] {1, 2, 3},
                "certificado_prueba.p12",
                java.time.LocalDateTime.now().plusYears(1)
            );

            mensaje = "Certificado de prueba registrado correctamente.";

        } catch (Exception e) {
            mensaje = "Error al registrar: " + e.getMessage();
            e.printStackTrace();
        }
    }

    public void probarCargaCertificado() {

        try {

            // 1. Comprobar que se haya seleccionado un usuario.
            if (ci <= 0) {
                throw new IllegalArgumentException(
                    "Primero debe buscar un usuario por cédula.");
            }

            // 2. Comprobar que se haya seleccionado un archivo.
            if (certificado == null || certificado.getSize() == 0) {
                throw new IllegalArgumentException(
                    "Debe seleccionar un certificado .p12 o .pfx.");
            }

            // 3. Obtener los bytes originales del archivo.
            byte[] archivo = certificado.getContent();

            // 4. Abrir y validar el certificado con su contraseña.
            CredencialesFirma credenciales =
                certificadoDigitalEJB.abrirCredenciales(
                    archivo,
                    passwordCertificado
                );

            // 5. Obtener el certificado X.509.
            X509Certificate x509 = credenciales.getCertificado();

            // 6. Obtener su fecha de vencimiento.
            LocalDateTime vencimiento =
                certificadoDigitalEJB.obtenerFechaVencimiento(x509);

            // 7. Registrar el archivo original en PostgreSQL.
            certificadoDigitalEJB.registrarCertificado(
                ci,
                archivo,
                certificado.getFileName(),
                vencimiento
            );
            cargarCertificados();
            // 8. Mostrar confirmación.
            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_INFO,
                    "Certificado registrado",
                    "El certificado se registró correctamente. Vencimiento: "
                        + vencimiento
                )
            );

        } catch (Exception e) {

            FacesContext.getCurrentInstance().addMessage(
                null,
                new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "No se pudo registrar el certificado",
                    e.getMessage()
                )
            );

        } finally {

            // Limpiar los datos temporales.
            passwordCertificado = null;
            certificado = null;
        }
    }
    
    
    public int getCi() {
        return ci;
    }

    public void setCi(int ci) {
        this.ci = ci;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Usuario getUsuarioEncontrado() {
        return usuarioEncontrado;
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
    
    public String getMotivoRevocacion() {
        return motivoRevocacion;
    }

    public void setMotivoRevocacion(String motivoRevocacion) {
        this.motivoRevocacion = motivoRevocacion;
    }
}
