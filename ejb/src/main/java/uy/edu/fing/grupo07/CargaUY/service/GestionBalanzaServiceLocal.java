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

    default PesadaBalanzaDTO registrarPesada(String token, PesadaBalanzaDTO dto) {
        return registrarPesada(dto);
    }

    /**
     * Consulta el mock de balanza periférica y registra automáticamente la pesada resultante.
     */
    PesadaBalanzaDTO registrarPesadaDesdeMock();

    default PesadaBalanzaDTO registrarPesadaDesdeMock(String token) {
        return registrarPesadaDesdeMock();
    }

    /**
     * Consulta el mock de balanza periférica y registra la pesada en la guía indicada como respaldo.
     */
    default PesadaBalanzaDTO registrarPesadaDesdeMock(Long guiaIdFallback) {
        return registrarPesadaDesdeMock();
    }

    default PesadaBalanzaDTO registrarPesadaDesdeMock(String token, Long guiaIdFallback) {
        return registrarPesadaDesdeMock(guiaIdFallback);
    }

    /**
     * Sincroniza (request-response) las pesadas registradas en el nodo periférico de balanzas
     * para el vehículo y fecha de la guía indicada, evitando duplicados.
     */
    default List<PesadaBalanzaDTO> sincronizarPesadasDeGuia(Long guiaId, String correlacion) {
        return List.of();
    }

    default List<PesadaBalanzaDTO> sincronizarPesadasDeGuia(String token, Long guiaId, String correlacion) {
        return sincronizarPesadasDeGuia(guiaId, correlacion);
    }

    /**
     * Registra una pesada en el nodo periférico de balanzas (POST /pesadas) simulando
     * el paso por peaje, quedando disponible para su posterior consulta (request-response).
     */
    default PesadaBalanzaDTO registrarPesadaViaPeriferico(PesadaBalanzaDTO dto, String peaje, String correlacion) {
        return registrarPesada(dto);
    }

    default PesadaBalanzaDTO registrarPesadaViaPeriferico(
            String token,
            PesadaBalanzaDTO dto,
            String peaje,
            String correlacion
    ) {
        return registrarPesadaViaPeriferico(dto, peaje, correlacion);
    }

    /**
     * Retorna todas las pesadas asociadas a una guía de viaje ordenadas cronológicamente.
     */
    List<PesadaBalanzaDTO> listarPesadasPorGuia(Long guiaId);

    default List<PesadaBalanzaDTO> listarPesadasPorGuia(String token, Long guiaId) {
        return listarPesadasPorGuia(guiaId);
    }

    /**
     * Ejecuta un ciclo de sondeo periódico sobre las balanzas periféricas habilitadas:
     * registra el paso de viajes EN_CURSO según la configuración de cada balanza activa,
     * importa las pesadas nuevas en forma idempotente y evalúa vehículos sin guía activa.
     *
     * @return cantidad total de nuevas pesadas procesadas e importadas al sistema central
     */
    default int sondearBalanzasPeriodico() {
        return 0;
    }

    /**
     * Registra una lectura externa en un nodo de balanza habilitado (uso de panel de nodos),
     * quedando sujeta al flujo normal de ingesta y fiscalización periódica.
     */
    default PesadaBalanzaDTO registrarLecturaExternaEnNodo(String codigoNodo, String matricula, int pesoKg) {
        throw new UnsupportedOperationException("No implementado");
    }

    default PesadaBalanzaDTO registrarLecturaExternaEnNodo(
            String token,
            String codigoNodo,
            String matricula,
            int pesoKg
    ) {
        return registrarLecturaExternaEnNodo(codigoNodo, matricula, pesoKg);
    }
}
