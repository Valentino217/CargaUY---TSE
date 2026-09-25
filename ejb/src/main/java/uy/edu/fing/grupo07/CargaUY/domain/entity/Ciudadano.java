package uy.edu.fing.grupo07.cargauy.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Ciudadano (propietario o representante de empresas de transporte).
 */
@Entity
@Table(name = "ciudadanos")
@DiscriminatorValue("CIUDADANO")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Ciudadano extends Usuario {

    private static final long serialVersionUID = 1L;

    @ManyToMany(mappedBy = "ciudadanos")
    private List<Empresa> empresas = new ArrayList<>();

    public Ciudadano() {
        super();
    }

    public Ciudadano(String nombre, String mail, LocalDate fechaNac, int ci, String contraseña) {
        super(nombre, mail, fechaNac, ci, contraseña);
    }

    public List<Empresa> getEmpresas() {
        return empresas;
    }

    public void setEmpresas(List<Empresa> empresas) {
        this.empresas = empresas;
    }
}
