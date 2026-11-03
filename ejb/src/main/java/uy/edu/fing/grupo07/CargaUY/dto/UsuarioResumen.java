package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

public record UsuarioResumen(int id, String tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, boolean activo,
        Integer nroFuncionario, String departamento, String cargo, long version, java.util.Set<TipoUsuario> roles) implements java.io.Serializable {

    public int getId() { return id; }
    public String getTipo() { return tipo; }
    public String getNombre() { return nombre; }
    public String getMail() { return mail; }
    public LocalDate getFechaNac() { return fechaNac; }
    public int getCi() { return ci; }
    public boolean isActivo() { return activo; }
    public Integer getNroFuncionario() { return nroFuncionario; }
    public String getDepartamento() { return departamento; }
    public String getCargo() { return cargo; }
    public long getVersion() { return version; }
    public java.util.Set<TipoUsuario> getRoles() { return roles; }
	
    public static UsuarioResumen desde(Usuario u) {
        String tipo = u instanceof Ciudadano ? "CIUDADANO"
            : u instanceof Chofer ? "CHOFER"
            : u instanceof Funcionario ? "FUNCIONARIO"
            : u instanceof Autoridad ? "AUTORIDAD" : "ADMINISTRADOR";
        return new UsuarioResumen(u.getId(), tipo, u.getNombre(), u.getMail(),
            u.getFechaNac(), u.getCi(), u.isActivo(),
            u instanceof Funcionario f ? f.getNroFuncionario() : null,
            u instanceof Funcionario f ? f.getDepartamento() : null,
            u instanceof Autoridad a ? a.getCargo() : null, u.getVersion(), java.util.Set.copyOf(u.getRoles()));
    }
}