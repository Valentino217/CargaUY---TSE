package uy.edu.fing.grupo07.CargaUY.dto;

import java.time.LocalDate;

// No usar este record para respuestas ni registrarlo en logs: contiene una contraseña.
public record DatosUsuario(TipoUsuario tipo, String nombre, String mail,
        LocalDate fechaNac, int ci, String password,
        Integer nroFuncionario, String departamento, String cargo) {}