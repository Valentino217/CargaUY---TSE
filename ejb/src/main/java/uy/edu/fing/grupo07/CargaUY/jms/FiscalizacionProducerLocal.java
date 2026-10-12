package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.Local;

/**
 * Interfaz local para publicar solicitudes de fiscalización asíncrona en FiscalizacionQueue.
 */
@Local
public interface FiscalizacionProducerLocal {

    void solicitarEvaluacion(Long guiaId);
}
