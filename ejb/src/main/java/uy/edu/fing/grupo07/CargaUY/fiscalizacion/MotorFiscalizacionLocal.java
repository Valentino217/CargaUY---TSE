package uy.edu.fing.grupo07.CargaUY.fiscalizacion;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.enums.TipoInfraccion;

import java.util.List;

/**
 * Interfaz local del motor de fiscalización automática de guías de viaje (CU-C3).
 */
@Local
public interface MotorFiscalizacionLocal {

    List<TipoInfraccion> evaluarGuia(Long guiaId);
}
