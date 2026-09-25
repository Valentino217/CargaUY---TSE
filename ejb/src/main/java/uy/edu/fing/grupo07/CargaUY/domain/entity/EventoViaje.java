package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import uy.edu.fing.grupo07.cargauy.domain.enums.TipoEvento;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad EventoViaje que modela los hitos de una guía de viaje (carga, descarga, incidente, etc.).
 * Incluye UUID de cliente para idempotencia y deduplicación (AC012, AC014).
 */
@Entity
@Table(name = "eventos_viaje", indexes = {
    @Index(name = "idx_eventos_uuid", columnList = "uuid", unique = true),
    @Index(name = "idx_eventos_guia_tiempo", columnList = "guia_id, tiempo")
})
public class EventoViaje implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String uuid;

    @Column(nullable = false)
    private LocalDateTime tiempo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEvento tipo;

    @Column(nullable = false)
    private double latitud;

    @Column(nullable = false)
    private double longitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_id", nullable = false)
    private GuiaDeViaje guia;

    @OneToOne(mappedBy = "evento", cascade = CascadeType.ALL, optional = true, orphanRemoval = true)
    private IncidenteRuta incidente;

    public EventoViaje() {
        this.uuid = UUID.randomUUID().toString();
    }

    public EventoViaje(String uuid, LocalDateTime tiempo, TipoEvento tipo, double latitud, double longitud, GuiaDeViaje guia) {
        this.uuid = (uuid != null && !uuid.isBlank()) ? uuid : UUID.randomUUID().toString();
        this.tiempo = tiempo;
        this.tipo = tipo;
        this.latitud = latitud;
        this.longitud = longitud;
        this.guia = guia;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public LocalDateTime getTiempo() {
        return tiempo;
    }

    public void setTiempo(LocalDateTime tiempo) {
        this.tiempo = tiempo;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public void setTipo(TipoEvento tipo) {
        this.tipo = tipo;
    }

    public double getLatitud() {
        return latitud;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }

    public GuiaDeViaje getGuia() {
        return guia;
    }

    public void setGuia(GuiaDeViaje guia) {
        this.guia = guia;
    }

    public IncidenteRuta getIncidente() {
        return incidente;
    }

    public void setIncidente(IncidenteRuta incidente) {
        this.incidente = incidente;
        if (incidente != null) {
            incidente.setEvento(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventoViaje that = (EventoViaje) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
