package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.PesadaBalanzaDTO;

import java.util.List;

/**
 * Interfaz local para el servicio de ingesta y gestión de pesadas de balanza.
 */
@Local
public interface GestionBalanzaServiceLocal {

    /**
     * Registra una pesada asociándola a la guía de viaje activa del vehículo.
     */
    PesadaBalanzaDTO registrarPesada(PesadaBalanzaDTO dto);

    /**
     * Consulta el mock de balanza periférica y registra automáticamente la pesada resultante.
     */
    PesadaBalanzaDTO registrarPesadaDesdeMock();

    /**
     * Consulta el mock de balanza periférica y registra la pesada en la guía indicada como respaldo.
     */
    default PesadaBalanzaDTO registrarPesadaDesdeMock(Long guiaIdFallback) {
        return registrarPesadaDesdeMock();
    }

    /**
     * Sincroniza (request-response) las pesadas registradas en el nodo periférico de balanzas
     * para el vehículo y fecha de la guía indicada, evitando duplicados.
     */
    default List<PesadaBalanzaDTO> sincronizarPesadasDeGuia(Long guiaId, String correlacion) {
        return List.of();
    }

    /**
     * Registra una pesada en el nodo periférico de balanzas (POST /pesadas) simulando
     * el paso por peaje, quedando disponible para su posterior consulta (request-response).
     */
    default PesadaBalanzaDTO registrarPesadaViaPeriferico(PesadaBalanzaDTO dto, String peaje, String correlacion) {
        return registrarPesada(dto);
    }

    /**
     * Retorna todas las pesadas asociadas a una guía de viaje ordenadas cronológicamente.
     */
    List<PesadaBalanzaDTO> listarPesadasPorGuia(Long guiaId);
}
