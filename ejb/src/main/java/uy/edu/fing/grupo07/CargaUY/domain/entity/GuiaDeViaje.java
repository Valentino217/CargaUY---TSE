package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import uy.edu.fing.grupo07.cargauy.domain.enums.TipoEstado;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad central GuiaDeViaje que coordina el transporte, vehículos, choferes y eventos de ruta.
 */
@Entity
@Table(name = "guias_de_viaje")
public class GuiaDeViaje implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String origen;

    @Column(nullable = false, length = 150)
    private String destino;

    @Column(name = "rubro_cliente", nullable = false, length = 100)
    private String rubroCliente;

    @Column(name = "volumen_carga", nullable = false)
    private float volumenCarga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEstado estado;

    /**
     * Versión para bloqueo optimista (Optimistic Locking) requerido para
     * resolución de conflictos en sincronización móvil offline (CU-C2 / AC014).
     */
    @Version
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nro_empresa", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_matricula", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chofer_id", nullable = false)
    private Chofer chofer;

    @ManyToMany
    @JoinTable(
        name = "guia_tipos_carga",
        joinColumns = @JoinColumn(name = "guia_id"),
        inverseJoinColumns = @JoinColumn(name = "tipo_carga_codigo")
    )
    private List<TipoCarga> tiposCarga = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PuntoTracking> puntosTracking = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PesadaBalanza> pesadas = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventoViaje> eventos = new ArrayList<>();

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL)
    private List<Infraccion> infracciones = new ArrayList<>();

    public GuiaDeViaje() {
        this.estado = TipoEstado.SIN_INICIAR;
    }

    public GuiaDeViaje(LocalDate fecha, String origen, String destino, String rubroCliente,
                       float volumenCarga, Empresa empresa, Vehiculo vehiculo, Chofer chofer) {
        this.fecha = fecha != null ? fecha : LocalDate.now();
        this.origen = origen;
        this.destino = destino;
        this.rubroCliente = rubroCliente;
        this.volumenCarga = volumenCarga;
        this.empresa = empresa;
        this.vehiculo = vehiculo;
        this.chofer = chofer;
        this.estado = TipoEstado.SIN_INICIAR;
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

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getRubroCliente() {
        return rubroCliente;
    }

    public void setRubroCliente(String rubroCliente) {
        this.rubroCliente = rubroCliente;
    }

    public float getVolumenCarga() {
        return volumenCarga;
    }

    public void setVolumenCarga(float volumenCarga) {
        this.volumenCarga = volumenCarga;
    }

    public TipoEstado getEstado() {
        return estado;
    }

    public void setEstado(TipoEstado estado) {
        this.estado = estado;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public Chofer getChofer() {
        return chofer;
    }

    public void setChofer(Chofer chofer) {
        this.chofer = chofer;
    }

    public List<TipoCarga> getTiposCarga() {
        return tiposCarga;
    }

    public void setTiposCarga(List<TipoCarga> tiposCarga) {
        this.tiposCarga = tiposCarga;
    }

    public List<PuntoTracking> getPuntosTracking() {
        return puntosTracking;
    }

    public void setPuntosTracking(List<PuntoTracking> puntosTracking) {
        this.puntosTracking = puntosTracking;
    }

    public List<PesadaBalanza> getPesadas() {
        return pesadas;
    }

    public void setPesadas(List<PesadaBalanza> pesadas) {
        this.pesadas = pesadas;
    }

    public List<EventoViaje> getEventos() {
        return eventos;
    }

    public void setEventos(List<EventoViaje> eventos) {
        this.eventos = eventos;
    }

    public List<Infraccion> getInfracciones() {
        return infracciones;
    }

    public void setInfracciones(List<Infraccion> infracciones) {
        this.infracciones = infracciones;
    }

    public void agregarPuntoTracking(PuntoTracking punto) {
        puntosTracking.add(punto);
        punto.setGuia(this);
    }

    public void agregarPesada(PesadaBalanza pesada) {
        pesadas.add(pesada);
        pesada.setGuia(this);
    }

    public void agregarEvento(EventoViaje evento) {
        eventos.add(evento);
        evento.setGuia(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuiaDeViaje that = (GuiaDeViaje) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
