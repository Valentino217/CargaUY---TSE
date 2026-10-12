package uy.edu.fing.grupo07.CargaUY.dto;

import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * DTO inmutable (record) con los datos resumidos de la guía asignada a un chofer.
 */
public record GuiaResumenDTO(
        Long id,
        LocalDate fecha,
        String origen,
        String destino,
        String rubroCliente,
        float volumenCarga,
        TipoEstado estado,
        int version,
        Integer vehiculoMatricula,
        String vehiculoMarcaModelo,
        Integer nroEmpresa,
        String empresaRazonSocial,
        Integer choferId,
        String choferNombre
) implements Serializable {
}
