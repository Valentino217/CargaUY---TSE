package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;

/**
 * DTO para la confirmación o desestimación de una infracción por parte de un funcionario.
 */
public record ResolucionInfraccionDTO(
        Integer funcionarioId,
        String fundamento
) implements Serializable {
}
