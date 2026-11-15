package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;

/**
 * Interfaz local del servicio de integración y control del Nodo Periférico de Tracking GPS
 * para guías de viaje activas (EN_CURSO).
 */
@Local
public interface TrackingMockServiceLocal {

    /**
     * Inicia o actualiza el envío periódico automático de posiciones en el nodo periférico de Tracking
     * para todas las guías EN_CURSO.
     *
     * @param eventosPorMinuto cantidad de ciclos de emisión por minuto (1..120)
     * @param cantidadDesvios  cantidad de episodios de desvío a simular durante el trayecto hacia el destino (0 = sin desvíos)
     */
    void iniciar(int eventosPorMinuto, int cantidadDesvios);

    default void iniciar(String token, int eventosPorMinuto, int cantidadDesvios) {
        iniciar(eventosPorMinuto, cantidadDesvios);
    }

    /**
     * Detiene el envío automático periódico de posiciones en el nodo periférico de Tracking.
     */
    void detener();

    default void detener(String token) {
        detener();
    }

    /**
     * Reinicia el progreso del recorrido simulado de las guías activas en el nodo periférico para comenzar nuevamente desde el origen.
     */
    void reiniciarRecorridos();

    default void reiniciarRecorridos(String token) {
        reiniciarRecorridos();
    }

    /**
     * Ejecuta un paso inmediato en el nodo periférico emitiendo un punto de posición para cada guía activa (EN_CURSO).
     *
     * @return cantidad de eventos de posición emitidos en este paso
     */
    int emitirPasoGuiasActivas();

    default int emitirPasoGuiasActivas(String token) {
        return emitirPasoGuiasActivas();
    }

    /**
     * Sincroniza la lista de guías EN_CURSO con el nodo periférico de Tracking y drena los eventos emitidos.
     */
    default void sincronizarConNodoPeriferico() {
    }

    default void sincronizarConNodoPeriferico(String token) {
        sincronizarConNodoPeriferico();
    }

    default boolean isNodoPerifericoOnline() {
        return false;
    }

    default String getEndpointNodoPeriferico() {
        return "http://localhost:8085";
    }

    boolean estaActivo();

    int getEventosPorMinuto();

    int getCantidadDesvios();

    long getTotalEventosEmitidos();

    long getTotalDesviosEmitidos();

    String getUltimoResultado();

    /**
     * Ejecuta un paso automático del scheduler central respetando la configuración
     * operativa de los nodos periféricos (encendido/apagado y modo normal o con desvío).
     */
    default int ejecutarPasoAutomaticoTracking() {
        return 0;
    }

    /**
     * Notifica al coordinador que la configuración del nodo de tracking fue modificada
     * desde el panel de administración (/admin/nodos.xhtml).
     */
    default void notificarCambioConfiguracionNodos() {
    }

    default void notificarCambioConfiguracionNodos(String token) {
        notificarCambioConfiguracionNodos();
    }

    /**
     * Notifica al coordinador que cambió la configuración o el estado de una balanza
     * periférica, disparando únicamente el sondeo de balanzas sin emitir pasos GPS.
     */
    default void notificarCambioBalanza() {
    }

    default void notificarCambioBalanza(String token) {
        notificarCambioBalanza();
    }
}
