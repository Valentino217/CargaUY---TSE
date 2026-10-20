package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;

/**
 * DTO para la presentación de descargos ante una infracción notificada.
 */
public record DescargoRequestDTO(
        String texto
) implements Serializable {
}
