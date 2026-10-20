package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;

import java.util.List;

/**
 * Interfaz local para la gestión de casos de fiscalización y descargos (CU-C4).
 */
@Local
public interface GestionInfraccionesServiceLocal {

    List<InfraccionDTO> listarInfracciones(EstadoInfraccion estado, TipoInfraccion tipo);

    InfraccionDTO obtenerInfraccion(int idCaso);

    InfraccionDTO notificarInfraccion(int idCaso);

    InfraccionDTO presentarDescargo(int idCaso, DescargoRequestDTO descargoRequest);

    InfraccionDTO confirmarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion);

    InfraccionDTO desestimarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion);
}
