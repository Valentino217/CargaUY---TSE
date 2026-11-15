package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uy.edu.fing.grupo07.CargaUY.dto.CategoriaNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;

import java.io.Serializable;

/**
 * Configuración persistente de cada nodo periférico administrable (balanzas y tracking GPS).
 */
@Entity
@Table(name = "nodos_perifericos")
public class ConfiguracionNodoPeriferico implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "codigo", length = 64)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 160)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 32)
    private CategoriaNodoPeriferico categoria;

    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;

    @Column(name = "estacion_peaje", length = 160)
    private String estacionPeaje;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_operacion", nullable = false, length = 32)
    private ModoNodoPeriferico modoOperacion;

    @Column(name = "watermark_id", nullable = false)
    private int watermarkId;

    @Column(name = "watermark_inicializado", nullable = false)
    private boolean watermarkInicializado;

    public ConfiguracionNodoPeriferico() {
    }

    public ConfiguracionNodoPeriferico(NodoPerifericoConfig dto) {
        aplicarDto(dto);
    }

    public final void aplicarDto(NodoPerifericoConfig dto) {
        this.codigo = dto.codigo();
        this.nombre = dto.nombre();
        this.categoria = dto.categoria();
        this.endpointUrl = dto.endpointUrl();
        this.estacionPeaje = dto.estacionPeaje();
        this.habilitado = dto.habilitado();
        this.modoOperacion = dto.modoOperacion();
    }

    public NodoPerifericoConfig toDto() {
        return new NodoPerifericoConfig(
                codigo,
                nombre,
                categoria,
                endpointUrl,
                estacionPeaje,
                habilitado,
                modoOperacion
        );
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public CategoriaNodoPeriferico getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaNodoPeriferico categoria) {
        this.categoria = categoria;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getEstacionPeaje() {
        return estacionPeaje;
    }

    public void setEstacionPeaje(String estacionPeaje) {
        this.estacionPeaje = estacionPeaje;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public ModoNodoPeriferico getModoOperacion() {
        return modoOperacion;
    }

    public void setModoOperacion(ModoNodoPeriferico modoOperacion) {
        this.modoOperacion = modoOperacion;
    }

    public int getWatermarkId() {
        return watermarkId;
    }

    public void setWatermarkId(int watermarkId) {
        this.watermarkId = watermarkId;
    }

    public boolean isWatermarkInicializado() {
        return watermarkInicializado;
    }

    public void setWatermarkInicializado(boolean watermarkInicializado) {
        this.watermarkInicializado = watermarkInicializado;
    }
}
