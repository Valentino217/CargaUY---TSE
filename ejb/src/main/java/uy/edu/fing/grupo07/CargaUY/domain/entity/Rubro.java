package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Rubro de actividad económica de las empresas.
 */
@Entity
@Table(name = "rubros")
public class Rubro implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String desc;

    @ManyToMany(mappedBy = "rubros")
    private List<Empresa> empresas = new ArrayList<>();

    public Rubro() {
    }

    public Rubro(String nombre, String desc) {
        this.nombre = nombre;
        this.desc = desc;
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public List<Empresa> getEmpresas() {
        return empresas;
    }

    public void setEmpresas(List<Empresa> empresas) {
        this.empresas = empresas;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rubro rubro = (Rubro) o;
        return codigo == rubro.codigo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo);
    }
}
