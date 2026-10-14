package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Chofer encargada de operar los vehículos y reportar eventos en ruta.
 */
@Entity
@Table(name = "choferes")
@DiscriminatorValue("CHOFER")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Chofer extends Usuario {

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "chofer")
    private List<GuiaDeViaje> guias = new ArrayList<>();
    
    //se agrega para la relacion muchos a muchos desde choferes a empresas
    @ManyToMany(mappedBy = "choferes")
    private List<Empresa> empresas = new ArrayList<>();

    

    public Chofer() {
        super();
    }

    public Chofer(String nombre, String mail, LocalDate fechaNac, int ci, String password) {
        super(nombre, mail, fechaNac, ci, password);
    }

    public List<GuiaDeViaje> getGuias() {
        return guias;
    }

    public void setGuias(List<GuiaDeViaje> guias) {
        this.guias = guias;
    }
    
    public List<Empresa> getEmpresas() {
        return empresas;
    }

    public void setEmpresas(List<Empresa> empresas) {
        this.empresas = empresas;
    }
}

