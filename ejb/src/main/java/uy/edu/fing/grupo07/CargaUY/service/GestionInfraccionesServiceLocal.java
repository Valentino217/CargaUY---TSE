package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DescargoRequestDTO;
import uy.edu.fing.grupo07.CargaUY.dto.InfraccionDTO;
import uy.edu.fing.grupo07.CargaUY.dto.ResolucionInfraccionDTO;

import java.util.List;

/**
 * Interfaz local para la gestión de casos de fiscalización, notificaciones, descargos y auditoría (CU-C4).
 */
@Local
public interface GestionInfraccionesServiceLocal {

    List<InfraccionDTO> listarInfracciones(EstadoInfraccion estado, TipoInfraccion tipo);

    List<InfraccionDTO> listarInfracciones(String token, EstadoInfraccion estado, TipoInfraccion tipo);

    List<InfraccionDTO> listarInfraccionesParaEmpresa(String token);

    InfraccionDTO obtenerInfraccion(int idCaso);

    InfraccionDTO obtenerInfraccion(String token, int idCaso);

    InfraccionDTO notificarInfraccion(int idCaso);

    InfraccionDTO notificarInfraccion(String token, int idCaso);

    InfraccionDTO procesarNotificacionAutomatica(int idCaso);

    InfraccionDTO presentarDescargo(int idCaso, DescargoRequestDTO descargoRequest);

    InfraccionDTO presentarDescargo(String token, int idCaso, DescargoRequestDTO descargoRequest);

    InfraccionDTO confirmarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion);

    InfraccionDTO confirmarInfraccion(String token, int idCaso, String fundamento);

    InfraccionDTO desestimarInfraccion(int idCaso, ResolucionInfraccionDTO resolucion);

    InfraccionDTO desestimarInfraccion(String token, int idCaso, String fundamento);
}
