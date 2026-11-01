package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record ValidacionEmpresaDTO(
        int numero, String razonSocial, boolean activa,
        LocalDate fechaConsulta, List<Unidad> unidades
) implements Serializable {
    public ValidacionEmpresaDTO {
        unidades = List.copyOf(unidades);
    }

    public record Unidad(
            String matricula, boolean habilitacionAdministrativa,
            LocalDate vencimientoPnc, String estadoPnc,
            LocalDate vencimientoItv, String estadoItv,
            String resultado
    ) implements Serializable {}

    public long habilitadas() {
        return unidades.stream().filter(u -> "VIGENTE".equals(u.resultado())).count();
    }
}