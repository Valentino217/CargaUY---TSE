package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad PNC (Permiso Nacional de Circulación) asociado a un vehículo.
 */
@Entity
@Table(name = "pncs")
public class PNC implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int nro;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_matricula", nullable = false)
    private Vehiculo vehiculo;

    public PNC() {
    }

    public PNC(LocalDate fechaVencimiento, Vehiculo vehiculo) {
        this.fechaVencimiento = fechaVencimiento;
        this.vehiculo = vehiculo;
    }

    public int getNro() {
        return nro;
    }

    public void setNro(int nro) {
        this.nro = nro;
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
        PNC pnc = (PNC) o;
        return nro == pnc.nro;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nro);
    }
}
