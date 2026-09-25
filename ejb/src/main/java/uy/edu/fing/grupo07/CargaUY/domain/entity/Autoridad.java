package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad Autoridad con acceso a tableros y reportes generales.
 */
@Entity
@Table(name = "autoridades")
@DiscriminatorValue("AUTORIDAD")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Autoridad extends Usuario {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 100)
    private String cargo;

    public Autoridad() {
        super();
    }

    public Autoridad(String nombre, String mail, LocalDate fechaNac, int ci, String contraseña, String cargo) {
        super(nombre, mail, fechaNac, ci, contraseña);
        this.cargo = cargo;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }
}
