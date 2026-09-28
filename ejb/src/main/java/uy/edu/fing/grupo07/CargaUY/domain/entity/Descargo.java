package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad Descargo presentada por una empresa en respuesta a una infracción notificada.
 */
@Entity
@Table(name = "descargos")
public class Descargo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 4000)
    private String descargo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "infraccion_id", nullable = false, unique = true)
    private Infraccion infraccion;

    public Descargo() {
    }

    public Descargo(LocalDate fecha, String descargo, Infraccion infraccion) {
        this.fecha = fecha;
        this.descargo = descargo;
        this.infraccion = infraccion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDescargo() {
        return descargo;
    }

    public void setDescargo(String descargo) {
        this.descargo = descargo;
    }

    public Infraccion getInfraccion() {
        return infraccion;
    }

    public void setInfraccion(Infraccion infraccion) {
        this.infraccion = infraccion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Descargo descargo1 = (Descargo) o;
        return Objects.equals(id, descargo1.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
