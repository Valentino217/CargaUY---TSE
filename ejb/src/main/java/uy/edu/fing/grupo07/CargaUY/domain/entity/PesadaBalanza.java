package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Entidad PesadaBalanza que registra las pesadas de control en estaciones periféricas.
 */
@Entity
@Table(name = "pesadas_balanza")
public class PesadaBalanza implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pesada")
    private int idPesada;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(name = "peso_registrado", nullable = false)
    private int pesoRegistrado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_id", nullable = false)
    private GuiaDeViaje guia;

    public PesadaBalanza() {
    }

    public PesadaBalanza(LocalDate fecha, LocalTime hora, int pesoRegistrado, GuiaDeViaje guia) {
        this.fecha = fecha;
        this.hora = hora;
        this.pesoRegistrado = pesoRegistrado;
        this.guia = guia;
    }

    public int getIdPesada() {
        return idPesada;
    }

    public void setIdPesada(int idPesada) {
        this.idPesada = idPesada;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public int getPesoRegistrado() {
        return pesoRegistrado;
    }

    public void setPesoRegistrado(int pesoRegistrado) {
        this.pesoRegistrado = pesoRegistrado;
    }

    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PesadaBalanza that = (PesadaBalanza) o;
        return idPesada == that.idPesada;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idPesada);
    }
}
