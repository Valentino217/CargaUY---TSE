package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Column(name = "fecha_notificacion")
    private LocalDate fechaNotificacion;

    @Column(length = 1000)
    private String evidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_id", nullable = true)
    private GuiaDeViaje guia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_matricula", nullable = true)
    private Vehiculo vehiculo;

    @OneToOne(mappedBy = "infraccion", fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = true, orphanRemoval = true)
    private Descargo descargo;

    @OneToMany(mappedBy = "infraccion", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("timestampCreacion ASC, id ASC")
    private List<RegistroAuditoria> registrosAuditoria = new ArrayList<>();

    public Infraccion() {
    }

    public Infraccion(LocalDate fecha, TipoInfraccion tipo, EstadoInfraccion estado, GuiaDeViaje guia) {
        this(fecha, tipo, estado, guia, (String) null);
    }

    public Infraccion(LocalDate fecha, TipoInfraccion tipo, EstadoInfraccion estado, GuiaDeViaje guia, String evidencia) {
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.tipo = tipo;
        this.estado = estado;
        this.guia = guia;
        this.vehiculo = guia != null ? guia.getVehiculo() : null;
        this.evidencia = evidencia;
    }

    public Infraccion(LocalDate fecha, TipoInfraccion tipo, EstadoInfraccion estado, Vehiculo vehiculo, String evidencia) {
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.tipo = tipo;
        this.estado = estado;
        this.guia = null;
        this.vehiculo = vehiculo;
        this.evidencia = evidencia;
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

    public LocalDate getFechaNotificacion() {
        return fechaNotificacion;
    }

    public void setFechaNotificacion(LocalDate fechaNotificacion) {
        this.fechaNotificacion = fechaNotificacion;
    }

    public String getEvidencia() {
        return evidencia;
    }

    public void setEvidencia(String evidencia) {
        this.evidencia = evidencia;
    }

    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
        if (guia != null && this.vehiculo == null) {
            this.vehiculo = guia.getVehiculo();
        }
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public Vehiculo resolverVehiculo() {
        if (vehiculo != null) {
            return vehiculo;
        }
        if (guia != null) {
            return guia.getVehiculo();
        }
        return null;
    }

    public Empresa resolverEmpresa() {
        if (guia != null && guia.getEmpresa() != null) {
            return guia.getEmpresa();
        }
        Vehiculo v = resolverVehiculo();
        if (v != null && v.getEmpresa() != null) {
            return v.getEmpresa();
        }
        return null;
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

    public List<RegistroAuditoria> getRegistrosAuditoria() {
        return registrosAuditoria != null ? Collections.unmodifiableList(registrosAuditoria) : Collections.emptyList();
    }

    public void agregarRegistroAuditoria(RegistroAuditoria registro) {
        if (registro == null) {
            return;
        }
        if (registrosAuditoria == null) {
            registrosAuditoria = new ArrayList<>();
        }
        registro.asociarInfraccion(this);
        if (!registrosAuditoria.contains(registro)) {
            registrosAuditoria.add(registro);
        }
    }

    public RegistroAuditoria getRegistroAuditoria() {
        if (registrosAuditoria == null || registrosAuditoria.isEmpty()) {
            return null;
        }
        return registrosAuditoria.get(registrosAuditoria.size() - 1);
    }

    public void setRegistroAuditoria(RegistroAuditoria registroAuditoria) {
        agregarRegistroAuditoria(registroAuditoria);
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
