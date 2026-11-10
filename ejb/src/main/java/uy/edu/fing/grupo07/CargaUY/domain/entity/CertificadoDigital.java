package uy.edu.fing.grupo07.CargaUY.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoCertificado;

@Entity
@Table(name = "certificados_digitales")
public class CertificadoDigital implements Serializable {

	    private static final long serialVersionUID = 1L;

	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private int id;
	    
	    //Identificador del certificado

	    // Usuario propietario del certificado
	    @ManyToOne(fetch = FetchType.LAZY, optional = false)
	    @JoinColumn(name = "usuario_id", nullable = false)
	    private Usuario usuario;
	    //ID del usuario de CargaUY

	    // Archivo PKCS#12 cifrado
	    @Column(name = "archivo_certificado", nullable = false)
	    private byte[] archivoCertificado;

	    @Column(name = "nombre_archivo", nullable = false, length = 255)
	    private String nombreArchivo;

	    @Column(name = "fecha_registro", nullable = false)
	    private LocalDateTime fechaRegistro;

	    @Column(name = "fecha_vencimiento")
	    private LocalDateTime fechaVencimiento;

	    @Enumerated(EnumType.STRING)
	    @Column(nullable = false, length = 20)
	    private EstadoCertificado estado = EstadoCertificado.ACTIVO;

	    @Column(name = "fecha_revocacion")
	    private LocalDateTime fechaRevocacion;

	    @Column(name = "motivo_revocacion", length = 255)
	    private String motivoRevocacion;

	    public CertificadoDigital() {
	    }

	    public int getId() {
	        return id;
	    }

	    public Usuario getUsuario() {
	        return usuario;
	    }

	    public void setUsuario(Usuario usuario) {
	        this.usuario = usuario;
	    }

	    public byte[] getArchivoCertificado() {
	        return archivoCertificado;
	    }

	    public void setArchivoCertificado(byte[] archivoCertificado) {
	        this.archivoCertificado = archivoCertificado;
	    }

	    public String getNombreArchivo() {
	        return nombreArchivo;
	    }

	    public void setNombreArchivo(String nombreArchivo) {
	        this.nombreArchivo = nombreArchivo;
	    }

	    public LocalDateTime getFechaRegistro() {
	        return fechaRegistro;
	    }

	    public void setFechaRegistro(LocalDateTime fechaRegistro) {
	        this.fechaRegistro = fechaRegistro;
	    }

	    public LocalDateTime getFechaVencimiento() {
	        return fechaVencimiento;
	    }

	    public void setFechaVencimiento(LocalDateTime fechaVencimiento) {
	        this.fechaVencimiento = fechaVencimiento;
	    }

	    public EstadoCertificado getEstado() {
	        return estado;
	    }

	    public void setEstado(EstadoCertificado estado) {
	        this.estado = estado;
	    }

	    public LocalDateTime getFechaRevocacion() {
	        return fechaRevocacion;
	    }

	    public void setFechaRevocacion(LocalDateTime fechaRevocacion) {
	        this.fechaRevocacion = fechaRevocacion;
	    }

	    public String getMotivoRevocacion() {
	        return motivoRevocacion;
	    }

	    public void setMotivoRevocacion(String motivoRevocacion) {
	        this.motivoRevocacion = motivoRevocacion;
	    }

}
