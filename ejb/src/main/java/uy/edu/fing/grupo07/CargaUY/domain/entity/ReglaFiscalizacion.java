package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Entidad ReglaFiscalizacion que almacena los umbrales configurables por el Administrador
 * para la detección automática de infracciones de carga y desvío de ruta.
 */
@Entity
@Table(name = "reglas_fiscalizacion")
public class ReglaFiscalizacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private int id = 1;

    @Column(name = "umbral_carga", nullable = false)
    private int umbralCarga;

    @Column(name = "umbral_tracking", nullable = false)
    private int umbralTracking;

    public ReglaFiscalizacion() {
        this.umbralCarga = 500;    // Tolerancia por defecto en kg (ej: 500 kg)
        this.umbralTracking = 5;   // Tolerancia por defecto en puntos desviados o km
    }

    public ReglaFiscalizacion(int umbralCarga, int umbralTracking) {
        this.id = 1;
        this.umbralCarga = umbralCarga;
        this.umbralTracking = umbralTracking;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUmbralCarga() {
        return umbralCarga;
    }

    public void setUmbralCarga(int umbralCarga) {
        this.umbralCarga = umbralCarga;
    }

    public int getUmbralTracking() {
        return umbralTracking;
    }

    public void setUmbralTracking(int umbralTracking) {
        this.umbralTracking = umbralTracking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReglaFiscalizacion that = (ReglaFiscalizacion) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
