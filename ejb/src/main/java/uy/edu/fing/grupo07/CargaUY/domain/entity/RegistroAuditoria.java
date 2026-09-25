package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad RegistroAuditoria estrictamente inmutable (append-only) para fiscalización (R005 / AC004).
 * No expone setters públicos para garantizar la integridad histórica de las decisiones de los funcionarios.
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String resolucion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "timestamp_creacion", nullable = false, updatable = false)
    private LocalDateTime timestampCreacion;

    @Column(nullable = false, length = 150, updatable = false)
    private String usuario;

    @Column(nullable = false, length = 100, updatable = false)
    private String accion;

    @Column(nullable = false, length = 2000, updatable = false)
    private String fundamento;

    /**
     * Constructor protegido requerido por la especificación JPA.
     */
    protected RegistroAuditoria() {
    }

    /**
     * Constructor público único para instanciación. No se permiten modificaciones posteriores.
     */
    public RegistroAuditoria(String resolucion, LocalDate fecha, String usuario, String accion, String fundamento) {
        this.resolucion = resolucion;
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.timestampCreacion = LocalDateTime.now();
        this.usuario = usuario;
        this.accion = accion;
        this.fundamento = fundamento;
    }

    public Long getId() {
        return id;
    }

    public String getResolucion() {
        return resolucion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalDateTime getTimestampCreacion() {
        return timestampCreacion;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getFundamento() {
        return fundamento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistroAuditoria that = (RegistroAuditoria) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
