package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad Administrador de la plataforma carga.uy.
 */
@Entity
@Table(name = "administradores")
@DiscriminatorValue("ADMINISTRADOR")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Administrador extends Usuario {

    private static final long serialVersionUID = 1L;

    public Administrador() {
        super();
    }

    public Administrador(String nombre, String mail, LocalDate fechaNac, int ci, String contraseña) {
        super(nombre, mail, fechaNac, ci, contraseña);
    }
}
