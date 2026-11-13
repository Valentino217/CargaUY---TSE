package uy.edu.fing.grupo07.CargaUY.dto;

import java.io.Serializable;

/**
 * Configuración operativa en tiempo real de un nodo periférico administrable (Balanza o Tracking).
 */
public record NodoPerifericoConfig(
        String codigo,
        String nombre,
        CategoriaNodoPeriferico categoria,
        String endpointUrl,
        String estacionPeaje,
        boolean habilitado,
        ModoNodoPeriferico modoOperacion
) implements Serializable {

    public boolean esBalanza() {
        return categoria == CategoriaNodoPeriferico.BALANZA;
    }

    public boolean esTracking() {
        return categoria == CategoriaNodoPeriferico.TRACKING;
    }
}
