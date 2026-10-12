package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

/**
 * Interfaz local para la generación de reportes y métricas consolidadas del dashboard.
 */
@Local
public interface DashboardServiceLocal {

    DashboardResumenDTO obtenerResumen();
}
