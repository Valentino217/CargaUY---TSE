package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad ITV (Inspección Técnica Vehicular) de un vehículo.
 */
@Entity
@Table(name = "itvs")
public class ITV implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int nroCertificado;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_matricula", nullable = false, unique = true)
    private Vehiculo vehiculo;

    public ITV() {
    }

    public ITV(LocalDate fechaVencimiento, Vehiculo vehiculo) {
        this.fechaVencimiento = fechaVencimiento;
        this.vehiculo = vehiculo;
    }

    public int getNroCertificado() {
        return nroCertificado;
    }

    public void setNroCertificado(int nroCertificado) {
        this.nroCertificado = nroCertificado;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public boolean isVigente() {
        return fechaVencimiento != null && !fechaVencimiento.isBefore(LocalDate.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ITV itv = (ITV) o;
        return nroCertificado == itv.nroCertificado;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nroCertificado);
    }
}
