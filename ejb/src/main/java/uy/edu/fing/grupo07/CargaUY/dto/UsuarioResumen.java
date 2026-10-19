package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;
import uy.edu.fing.grupo07.CargaUY.domain.entity.*;

public record UsuarioResumen(int id, String tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, boolean activo,
        Integer nroFuncionario, String departamento, String cargo, long version, java.util.Set<TipoUsuario> roles) {
	
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