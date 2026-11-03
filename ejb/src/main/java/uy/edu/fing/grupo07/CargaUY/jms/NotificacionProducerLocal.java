package uy.edu.fing.grupo07.CargaUY.jms;

import jakarta.ejb.Local;

/**
 * Interfaz local para publicar solicitudes asíncronas de notificación a empresas transportistas
 * en {@code NotificacionQueue} cuando el motor de fiscalización detecta un nuevo caso.
 */
@Local
public interface NotificacionProducerLocal {

    void solicitarNotificacion(int idCaso);
}
