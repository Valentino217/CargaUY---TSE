package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad Funcionario, encargada de la fiscalización y resolución de infracciones.
 */
@Entity
@Table(name = "funcionarios")
@DiscriminatorValue("FUNCIONARIO")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Funcionario extends Usuario {

    private static final long serialVersionUID = 1L;

    @Column(name = "nro_funcionario", nullable = false)
    private int nroFuncionario;

    @Column(nullable = false, length = 100)
    private String departamento;

    public Funcionario() {
        super();
    }

    public Funcionario(String nombre, String mail, LocalDate fechaNac, int ci, String contraseña,
                       int nroFuncionario, String departamento) {
        super(nombre, mail, fechaNac, ci, contraseña);
        this.nroFuncionario = nroFuncionario;
        this.departamento = departamento;
    }

    public int getNroFuncionario() {
        return nroFuncionario;
    }

    public void setNroFuncionario(int nroFuncionario) {
        this.nroFuncionario = nroFuncionario;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }
}
