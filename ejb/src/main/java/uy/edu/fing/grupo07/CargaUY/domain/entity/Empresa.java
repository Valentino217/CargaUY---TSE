package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Empresa titular de vehículos y responsable de guías de viaje.
 */
@Entity
@Table(name = "empresas")
public class Empresa implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "nro_empresa")
    private int nroEmpresa;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nombre_publico", nullable = false, length = 150)
    private String nombrePublico;

    @Column(nullable = false, length = 255)
    private String direccion;

    @ManyToMany
    @JoinTable(
        name = "empresa_ciudadanos",
        joinColumns = @JoinColumn(name = "nro_empresa"),
        inverseJoinColumns = @JoinColumn(name = "ciudadano_id")
    )
    private List<Ciudadano> ciudadanos = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "empresa_rubros",
        joinColumns = @JoinColumn(name = "nro_empresa"),
        inverseJoinColumns = @JoinColumn(name = "rubro_codigo")
    )
    private List<Rubro> rubros = new ArrayList<>();

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vehiculo> vehiculos = new ArrayList<>();

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL)
    private List<GuiaDeViaje> guias = new ArrayList<>();

    public Empresa() {
    }

    public Empresa(int nroEmpresa, String razonSocial, String nombrePublico, String direccion) {
        this.nroEmpresa = nroEmpresa;
        this.razonSocial = razonSocial;
        this.nombrePublico = nombrePublico;
        this.direccion = direccion;
    }

    public int getNroEmpresa() {
        return nroEmpresa;
    }

    public void setNroEmpresa(int nroEmpresa) {
        this.nroEmpresa = nroEmpresa;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNombrePublico() {
        return nombrePublico;
    }

    public void setNombrePublico(String nombrePublico) {
        this.nombrePublico = nombrePublico;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public List<Ciudadano> getCiudadanos() {
        return ciudadanos;
    }

    public void setCiudadanos(List<Ciudadano> ciudadanos) {
        this.ciudadanos = ciudadanos;
    }

    public List<Rubro> getRubros() {
        return rubros;
    }

    public void setRubros(List<Rubro> rubros) {
        this.rubros = rubros;
    }

    public List<Vehiculo> getVehiculos() {
        return vehiculos;
    }

    public void setVehiculos(List<Vehiculo> vehiculos) {
        this.vehiculos = vehiculos;
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
        Empresa empresa = (Empresa) o;
        return nroEmpresa == empresa.nroEmpresa;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nroEmpresa);
    }
}
