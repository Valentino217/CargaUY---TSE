package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "reglas_fiscalizacion")
public class ReglaFiscalizacion implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private int id = 1;
    @Version
    private long version;
    @Column(name = "umbral_carga", nullable = false)
    private int umbralCarga;
    // Distancia en kilómetros; NO cantidad de puntos.
    @Column(name = "umbral_tracking", nullable = false)
    private double umbralTracking;

    public ReglaFiscalizacion() { this(500, 5.0); }
    public ReglaFiscalizacion(int umbralCarga, double umbralTracking) {
        this.umbralCarga = umbralCarga;
        this.umbralTracking = umbralTracking;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getVersion() { return version; }
    public int getUmbralCarga() { return umbralCarga; }
    public void setUmbralCarga(int valor) { this.umbralCarga = valor; }
    public double getUmbralTracking() { return umbralTracking; }
    public void setUmbralTracking(double valor) { this.umbralTracking = valor; }
    @Override public boolean equals(Object o) {
        return this == o || o instanceof ReglaFiscalizacion r && id == r.id;
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
