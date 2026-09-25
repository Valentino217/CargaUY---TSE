package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import uy.edu.fing.grupo07.cargauy.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.cargauy.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad Infraccion generada automáticamente por el motor de fiscalización
 * o gestionada por los funcionarios.
 */
@Entity
@Table(name = "infracciones")
public class Infraccion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caso")
    private int idCaso;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoInfraccion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoInfraccion estado;

    @Column(length = 500)
    private String resolucion;

    @Column(length = 2000)
    private String fundamento;

    @Column(name = "fecha_resolucion")
    private LocalDate fechaResolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_id", nullable = false)
    private GuiaDeViaje guia;

    @OneToOne(mappedBy = "infraccion", cascade = CascadeType.ALL, optional = true, orphanRemoval = true)
    private Descargo descargo;

    @OneToOne(cascade = CascadeType.PERSIST, optional = true)
    @JoinColumn(name = "registro_auditoria_id", unique = true)
    private RegistroAuditoria registroAuditoria;

    public Infraccion() {
    }

    public Infraccion(LocalDate fecha, TipoInfraccion tipo, EstadoInfraccion estado, GuiaDeViaje guia) {
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.tipo = tipo;
        this.estado = estado;
        this.guia = guia;
    }

    public int getIdCaso() {
        return idCaso;
    }

    public void setIdCaso(int idCaso) {
        this.idCaso = idCaso;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public TipoInfraccion getTipo() {
        return tipo;
    }

    public void setTipo(TipoInfraccion tipo) {
        this.tipo = tipo;
    }

    public EstadoInfraccion getEstado() {
        return estado;
    }

    public void setEstado(EstadoInfraccion estado) {
        this.estado = estado;
    }

    public String getResolucion() {
        return resolucion;
    }

    public void setResolucion(String resolucion) {
        this.resolucion = resolucion;
    }

    public String getFundamento() {
        return fundamento;
    }

    public void setFundamento(String fundamento) {
        this.fundamento = fundamento;
    }

    public LocalDate getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDate fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
    }

    public Descargo getDescargo() {
        return descargo;
    }

    public void setDescargo(Descargo descargo) {
        this.descargo = descargo;
        if (descargo != null) {
            descargo.setInfraccion(this);
        }
    }

    public RegistroAuditoria getRegistroAuditoria() {
        return registroAuditoria;
    }

    public void setRegistroAuditoria(RegistroAuditoria registroAuditoria) {
        this.registroAuditoria = registroAuditoria;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Infraccion that = (Infraccion) o;
        return idCaso == that.idCaso;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCaso);
    }
}
