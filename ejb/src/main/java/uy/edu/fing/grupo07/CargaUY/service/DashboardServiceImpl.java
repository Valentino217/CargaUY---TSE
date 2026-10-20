package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import uy.edu.fing.grupo07.CargaUY.domain.enums.EstadoInfraccion;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoEstado;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;
import uy.edu.fing.grupo07.CargaUY.dto.DashboardResumenDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación del servicio de métricas consolidadas del Dashboard (CU-C4 / Autoridad).
 */
@Stateless
public class DashboardServiceImpl implements DashboardServiceLocal {

    @PersistenceContext(unitName = "CargaUYPersistenceUnit")
    private EntityManager em;

    public DashboardServiceImpl() {
    }

    public DashboardServiceImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public DashboardResumenDTO obtenerResumen() {
        Map<TipoEstado, Long> guiasPorEstado = inicializarMapaGuias();
        Map<EstadoInfraccion, Long> infraccionesPorEstado = inicializarMapaInfraccionesPorEstado();
        Map<TipoInfraccion, Long> infraccionesPorTipo = inicializarMapaInfraccionesPorTipo();

        cargarMetricasGuias(guiasPorEstado);
        cargarMetricasInfraccionesPorEstado(infraccionesPorEstado);
        cargarMetricasInfraccionesPorTipo(infraccionesPorTipo);

        Long resultadoPesadas = em.createQuery(
                "SELECT COUNT(p) FROM PesadaBalanza p",
                Long.class
        ).getSingleResult();

        long totalPesadas = 0L;
        if (resultadoPesadas != null) {
            totalPesadas = resultadoPesadas;
        }

        return new DashboardResumenDTO(
                guiasPorEstado,
                infraccionesPorEstado,
                infraccionesPorTipo,
                totalPesadas
        );
    }

    private Map<TipoEstado, Long> inicializarMapaGuias() {
        Map<TipoEstado, Long> mapa = new LinkedHashMap<>();
        for (TipoEstado estado : TipoEstado.values()) {
            mapa.put(estado, 0L);
        }
        return mapa;
    }

    private Map<EstadoInfraccion, Long> inicializarMapaInfraccionesPorEstado() {
        Map<EstadoInfraccion, Long> mapa = new LinkedHashMap<>();
        for (EstadoInfraccion estado : EstadoInfraccion.values()) {
            mapa.put(estado, 0L);
        }
        return mapa;
    }

    private Map<TipoInfraccion, Long> inicializarMapaInfraccionesPorTipo() {
        Map<TipoInfraccion, Long> mapa = new LinkedHashMap<>();
        for (TipoInfraccion tipo : TipoInfraccion.values()) {
            mapa.put(tipo, 0L);
        }
        return mapa;
    }

    private void cargarMetricasGuias(Map<TipoEstado, Long> guiasPorEstado) {
        List<Object[]> filas = em.createQuery(
                "SELECT g.estado, COUNT(g) FROM GuiaDeViaje g GROUP BY g.estado",
                Object[].class
        ).getResultList();

        for (Object[] fila : filas) {
            TipoEstado estado = (TipoEstado) fila[0];
            Long cantidad = (Long) fila[1];
            if (estado != null && cantidad != null) {
                guiasPorEstado.put(estado, cantidad);
            }
        }
    }

    private void cargarMetricasInfraccionesPorEstado(Map<EstadoInfraccion, Long> infraccionesPorEstado) {
        List<Object[]> filas = em.createQuery(
                "SELECT i.estado, COUNT(i) FROM Infraccion i GROUP BY i.estado",
                Object[].class
        ).getResultList();

        for (Object[] fila : filas) {
            EstadoInfraccion estado = (EstadoInfraccion) fila[0];
            Long cantidad = (Long) fila[1];
            if (estado != null && cantidad != null) {
                infraccionesPorEstado.put(estado, cantidad);
            }
        }
    }

    private void cargarMetricasInfraccionesPorTipo(Map<TipoInfraccion, Long> infraccionesPorTipo) {
        List<Object[]> filas = em.createQuery(
                "SELECT i.tipo, COUNT(i) FROM Infraccion i GROUP BY i.tipo",
                Object[].class
        ).getResultList();

        for (Object[] fila : filas) {
            TipoInfraccion tipo = (TipoInfraccion) fila[0];
            Long cantidad = (Long) fila[1];
            if (tipo != null && cantidad != null) {
                infraccionesPorTipo.put(tipo, cantidad);
            }
        }
    }
}
