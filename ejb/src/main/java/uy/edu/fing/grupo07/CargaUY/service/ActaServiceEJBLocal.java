package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;

@Local
public interface ActaServiceEJBLocal {
	 public byte[] emitirYFirmarActa(int idCaso, String empresa, String matricula, String fecha, String tipo, String evidencia);
}
