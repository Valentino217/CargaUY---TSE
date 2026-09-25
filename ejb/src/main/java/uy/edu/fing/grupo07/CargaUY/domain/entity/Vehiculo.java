package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Vehiculo registrada por una empresa para el transporte de carga.
 */
@Entity
@Table(name = "vehiculos")
public class Vehiculo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(nullable = false)
    private int matricula;

    @Column(nullable = false, length = 100)
    private String marca;

    @Column(nullable = false, length = 100)
    private String modelo;

    @Column(nullable = false)
    private int peso;

    @Column(name = "capacidad_carga", nullable = false)
    private int capacidadCarga;

    @Column(name = "estado_habilitacion", nullable = false)
    private boolean estadoHabilitacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nro_empresa", nullable = false)
    private Empresa empresa;

    @OneToMany(mappedBy = "vehiculo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PNC> pncs = new ArrayList<>();

    @OneToOne(mappedBy = "vehiculo", cascade = CascadeType.ALL, orphanRemoval = true)
    private ITV itv;

    @OneToMany(mappedBy = "vehiculo")
    private List<GuiaDeViaje> guias = new ArrayList<>();

    public Vehiculo() {
    }

    public Vehiculo(int matricula, String marca, String modelo, int peso, int capacidadCarga,
                    boolean estadoHabilitacion, Empresa empresa) {
        this.matricula = matricula;
        this.marca = marca;
        this.modelo = modelo;
        this.peso = peso;
        this.capacidadCarga = capacidadCarga;
        this.estadoHabilitacion = estadoHabilitacion;
        this.empresa = empresa;
    }

    public int getMatricula() {
        return matricula;
    }

    public void setMatricula(int matricula) {
        this.matricula = matricula;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public int getPeso() {
        return peso;
    }

    public void setPeso(int peso) {
        this.peso = peso;
    }

    public int getCapacidadCarga() {
        return capacidadCarga;
    }

    public void setCapacidadCarga(int capacidadCarga) {
        this.capacidadCarga = capacidadCarga;
    }

    public boolean isEstadoHabilitacion() {
        return estadoHabilitacion;
    }

    public void setEstadoHabilitacion(boolean estadoHabilitacion) {
        this.estadoHabilitacion = estadoHabilitacion;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public List<PNC> getPncs() {
        return pncs;
    }

    public void setPncs(List<PNC> pncs) {
        this.pncs = pncs;
    }

    public ITV getItv() {
        return itv;
    }

    public void setItv(ITV itv) {
        this.itv = itv;
    }

    public List<GuiaDeViaje> getGuias() {
        return guias;
    }

    public void setGuias(List<GuiaDeViaje> guias) {
        this.guias = guias;
    }

    /**
     * Verifica si el vehículo cuenta con habilitación vigente (PNC e ITV al día).
     */
    public boolean verificarHabilitacionVigente() {
        boolean itvValido = itv != null && itv.isVigente();
        boolean pncValido = pncs != null && pncs.stream().anyMatch(PNC::isVigente);
        return estadoHabilitacion && itvValido && pncValido;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehiculo vehiculo = (Vehiculo) o;
        return matricula == vehiculo.matricula;
    }

    @Override
    public int hashCode() {
        return Objects.hash(matricula);
    }
}
