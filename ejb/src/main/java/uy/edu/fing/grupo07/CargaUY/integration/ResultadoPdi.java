package uy.edu.fing.grupo07.CargaUY.integration;

import java.time.LocalDate;

public record ResultadoPdi(Estado estado, Integer ci, String nombre, String apellido,
        LocalDate fechaNacimiento, Boolean vigente, String mensaje) {
    public enum Estado { DISPONIBLE, NO_ENCONTRADO, NO_DISPONIBLE }
    public static ResultadoPdi indisponible() {
        return new ResultadoPdi(Estado.NO_DISPONIBLE, null, null, null, null, null,
            "PDI temporalmente no disponible; los datos no se verificaron");
    }
}