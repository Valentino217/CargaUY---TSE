package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;

/**
 * Interfaz local del servicio Mock de Tracking GPS para guías de viaje activas (EN_CURSO).
 */
@Local
public interface TrackingMockServiceLocal {

    /**
     * Inicia o actualiza el envío periódico automático de posiciones para todas las guías EN_CURSO.
     *
     * @param eventosPorMinuto cantidad de ciclos de emisión por minuto (1..120)
     * @param cantidadDesvios  cantidad de episodios de desvío a simular durante el trayecto hacia el destino (0 = sin desvíos)
     */
    void iniciar(int eventosPorMinuto, int cantidadDesvios);

    /**
     * Detiene el envío automático periódico de posiciones.
     */
    void detener();

    /**
     * Reinicia el progreso del recorrido simulado de las guías activas para comenzar nuevamente desde el origen.
     */
    void reiniciarRecorridos();

    /**
     * Ejecuta un paso inmediato del mock emitiendo un punto de posición para cada guía activa (EN_CURSO).
     *
     * @return cantidad de eventos de posición emitidos en este paso
     */
    int emitirPasoGuiasActivas();

    boolean estaActivo();

    int getEventosPorMinuto();

    int getCantidadDesvios();

    long getTotalEventosEmitidos();

    long getTotalDesviosEmitidos();

    String getUltimoResultado();
}
