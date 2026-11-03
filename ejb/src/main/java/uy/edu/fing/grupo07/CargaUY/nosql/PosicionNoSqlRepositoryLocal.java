package uy.edu.fing.grupo07.CargaUY.nosql;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;

import java.util.List;

/**
 * Contrato local del repositorio NoSQL / de serie temporal (MongoDB)
 * para la persistencia y consulta de los eventos de posición (Requerimiento Opcional 10).
 */
@Local
public interface PosicionNoSqlRepositoryLocal {

    /**
     * Persiste un evento de posición o telemetría GPS en la colección de serie temporal de MongoDB.
     */
    EventoViajeDTO guardarEventoPosicion(EventoViajeDTO dto, Long guiaId, String matricula);

    /**
     * Verifica por UUID si el evento de posición ya fue registrado en la base NoSQL (idempotencia AC012).
     */
    boolean existeEvento(String uuid);

    /**
     * Lista cronológicamente todos los eventos de posición asociados a una guía de viaje desde MongoDB.
     */
    List<EventoViajeDTO> listarEventosPorGuia(Long guiaId);

    /**
     * Obtiene el evento de posición con mayor distancia de desvío en kilómetros para una guía de viaje.
     */
    EventoViajeDTO buscarEventoConMayorDesvio(Long guiaId);

    /**
     * Cuenta la cantidad de eventos de posición almacenados en MongoDB para una guía.
     */
    long contarEventosPorGuia(Long guiaId);

    /**
     * Cuenta el total de documentos de posición almacenados en la colección de serie temporal.
     */
    long contarTotalEventos();

    /**
     * Indica si la conexión con el motor MongoDB se encuentra operativa.
     */
    boolean estaOperativo();

    /**
     * Retorna el descriptor del endpoint/colección configurado en MongoDB.
     */
    String obtenerDescriptorEndpoint();
}
