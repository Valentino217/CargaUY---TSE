package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad TipoCarga que especifica la naturaleza de la carga transportada.
 */
@Entity
@Table(name = "tipos_carga")
public class TipoCarga implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int codigo;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String desc;

    @ManyToMany(mappedBy = "tiposCarga")
    private List<GuiaDeViaje> guias = new ArrayList<>();

    public TipoCarga() {
    }

    public TipoCarga(String desc) {
        this.desc = desc;
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public List<GuiaDeViaje> getGuias() {
        return guias;
    }

    public void setGuias(List<GuiaDeViaje> guias) {
        this.guias = guias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TipoCarga tipoCarga = (TipoCarga) o;
        return codigo == tipoCarga.codigo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo);
    }
}
