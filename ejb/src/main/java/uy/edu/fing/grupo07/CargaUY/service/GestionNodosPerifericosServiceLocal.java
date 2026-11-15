package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.dto.ModoNodoPeriferico;
import uy.edu.fing.grupo07.CargaUY.dto.NodoPerifericoConfig;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz local para la administración centralizada de nodos periféricos (Balanzas y Tracking GPS).
 */
@Local
public interface GestionNodosPerifericosServiceLocal {

    List<NodoPerifericoConfig> listarNodos();

    List<NodoPerifericoConfig> listarBalanzasHabilitadas();

    List<NodoPerifericoConfig> listarNodosTracking();

    Optional<NodoPerifericoConfig> buscarPorCodigo(String codigo);

    boolean isNodoHabilitado(String codigo);

    boolean isTrackingHabilitado();

    boolean isAlgunaBalanzaHabilitada();

    ModoNodoPeriferico getModoTrackingActivo();

    String getEndpointTrackingActivo();

    void setHabilitado(String codigo, boolean habilitado);

    void setHabilitado(String token, String codigo, boolean habilitado);

    void cambiarModo(String codigo, ModoNodoPeriferico nuevoModo);

    void cambiarModo(String token, String codigo, ModoNodoPeriferico nuevoModo);

    void actualizarEndpoint(String codigo, String nuevoEndpoint);

    void actualizarEndpoint(String token, String codigo, String nuevoEndpoint);

    NodoPerifericoConfig agregarBalanza(
            String codigo,
            String nombre,
            String estacionPeaje,
            String endpointUrl,
            ModoNodoPeriferico modo,
            boolean habilitado
    );

    NodoPerifericoConfig agregarBalanza(
            String token,
            String codigo,
            String nombre,
            String estacionPeaje,
            String endpointUrl,
            ModoNodoPeriferico modo,
            boolean habilitado
    );

    boolean eliminarBalanzaPersonalizada(String codigo);

    boolean eliminarBalanzaPersonalizada(String token, String codigo);

    long getVersionConfiguracion();

    boolean yaEmitioAutoPesada(Long guiaId, String codigoBalanza, ModoNodoPeriferico modo);

    void marcarAutoPesadaEmitida(Long guiaId, String codigoBalanza, ModoNodoPeriferico modo);

    boolean isWatermarkInicializado(String endpointUrl);

    void inicializarWatermark(String endpointUrl, int maxIdInicial);

    int getWatermarkIdPesada(String endpointUrl);

    void actualizarWatermarkIdPesada(String endpointUrl, int idPesada);
}
