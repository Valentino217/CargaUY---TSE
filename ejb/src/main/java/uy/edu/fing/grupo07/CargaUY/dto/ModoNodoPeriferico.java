package uy.edu.fing.grupo07.CargaUY.dto;

/**
 * Perfil operativo asignado a un nodo periférico desde el panel de administración.
 */
public enum ModoNodoPeriferico {
    CALIBRADA("Pesaje Calibrado (Normal)", CategoriaNodoPeriferico.BALANZA),
    SOBRECARGA("Control Severo (Sobrecarga)", CategoriaNodoPeriferico.BALANZA),
    RUTA_NORMAL("Corredor Normal (0 desvíos)", CategoriaNodoPeriferico.TRACKING),
    CON_DESVIO("Incidencia (Desvío en Ruta)", CategoriaNodoPeriferico.TRACKING);

    private final String etiqueta;
    private final CategoriaNodoPeriferico categoria;

    ModoNodoPeriferico(String etiqueta, CategoriaNodoPeriferico categoria) {
        this.etiqueta = etiqueta;
        this.categoria = categoria;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public CategoriaNodoPeriferico getCategoria() {
        return categoria;
    }

    public boolean esCompatibleCon(CategoriaNodoPeriferico cat) {
        return this.categoria == cat;
    }
}
