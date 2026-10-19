package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad base abstracta Usuario con estrategia de herencia JOINED.
 */
@Entity
@Cacheable(false)
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING)
public abstract class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    @Version
    private long version;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name="origen_identidad", nullable=false, length=10)
    private String origenIdentidad = "LOCAL";
    @Column(name="uid_externo", length=200)
    private String uidExterno;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="usuario_roles", joinColumns=@JoinColumn(name="usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name="rol", nullable=false, length=30)
    private java.util.Set<uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario> roles = new java.util.HashSet<>();
    @OneToMany(mappedBy="chofer")
    private java.util.List<GuiaDeViaje> guias = new java.util.ArrayList<>();

    public long getVersion() { return version; }
    public String getOrigenIdentidad() { return origenIdentidad; }
    public void setOrigenIdentidad(String v) { origenIdentidad=v; }
    public String getUidExterno() { return uidExterno; }
    public void setUidExterno(String v) { uidExterno=v; }
    public java.util.Set<uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario> getRoles() { return roles; }
    public java.util.List<GuiaDeViaje> getGuias() { return guias; }
    public void setGuias(java.util.List<GuiaDeViaje> v) { guias=v; }


    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String mail;

    @Column(name = "fecha_nac", nullable = false)
    private LocalDate fechaNac;

    @Column(nullable = false, unique = true)
    private int ci;

    @Column(nullable = false, length = 255)
    private String password;

    public Usuario() {
    }

    public Usuario(String nombre, String mail, LocalDate fechaNac, int ci, String password) {
        this.nombre = nombre;
        this.mail = mail;
        this.fechaNac = fechaNac;
        this.ci = ci;
        this.password = password;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public LocalDate getFechaNac() {
        return fechaNac;
    }

    public void setFechaNac(LocalDate fechaNac) {
        this.fechaNac = fechaNac;
    }

    public int getCi() {
        return ci;
    }

    public void setCi(int ci) {
        this.ci = ci;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getContrasena() {
        return password;
    }

    public void setContrasena(String contrasena) {
        this.password = contrasena;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return ci == usuario.ci;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ci);
    }
    
    public boolean isActivo() { 
    	return activo; 
    }
    
    public void setActivo(boolean activo) { 
    	this.activo = activo;
    }
}
