package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Interfaz local para la generación de reportes y métricas consolidadas del dashboard.
 */
@Local
public interface DashboardServiceLocal {

    DashboardResumenDTO obtenerResumen();

    default DashboardResumenDTO obtenerResumenPublico() {
        return obtenerResumen();
    }

    default DashboardResumenDTO obtenerResumen(String token) {
        return obtenerResumen();
    }

    default DashboardResumenDTO obtenerResumenPortal(String token) {
        return obtenerResumen();
    }

    default List<String> listarRubrosDisponibles() {
        return List.of();
    }

    default List<String> listarRubrosDisponibles(String token) {
        return listarRubrosDisponibles();
    }

    default List<String> listarTiposCargaDisponibles() {
        return List.of();
    }

    default List<String> listarTiposCargaDisponibles(String token) {
        return listarTiposCargaDisponibles();
    }

    default List<String> listarUbicacionesDisponibles() {
        return List.of();
    }

    default List<String> listarUbicacionesDisponibles(String token) {
        return listarUbicacionesDisponibles();
    }

    default DashboardResumenDTO obtenerResumenFiltrado(
            String rubro,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return obtenerResumen();
    }

    default DashboardResumenDTO obtenerResumenFiltrado(
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return obtenerResumenFiltrado(rubro, ubicacion, fechaDesde, fechaHasta);
    }

    default DashboardResumenDTO obtenerResumenFiltrado(
            String token,
            String rubro,
            String tipoCarga,
            String ubicacion,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return obtenerResumenFiltrado(rubro, tipoCarga, ubicacion, fechaDesde, fechaHasta);
    }
}
