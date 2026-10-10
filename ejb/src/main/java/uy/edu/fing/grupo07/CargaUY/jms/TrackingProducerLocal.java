package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.EventoViajeDTO;

import java.util.List;

/**
 * Interfaz local para el productor de mensajes JMS de tracking.
 */
@Local
public interface TrackingProducerLocal {

    /**
     * Encola un evento de viaje de forma asíncrona en la TrackingQueue.
     */
    void enviarEvento(EventoViajeDTO evento);

    /**
     * Encola una lista de eventos de viaje de forma asíncrona.
     */
    void enviarEventos(List<EventoViajeDTO> eventos);
}
