package uy.edu.fing.grupo07.CargaUY.security;

import java.io.Serializable;
import java.util.Set;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

public record Acceso(int id, int ci, String nombre, Set<TipoUsuario> roles)
        implements Serializable {
    public Acceso { roles = Set.copyOf(roles); }
    public boolean tiene(TipoUsuario rol) { return roles.contains(rol); }
}
