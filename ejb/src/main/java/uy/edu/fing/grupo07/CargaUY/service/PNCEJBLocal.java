package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;

@Local
public interface PNCEJBLocal {
	void crear(PNC pnc);
	void modificar(PNC pnc);
	void eliminar(PNC pnc);
	List<PNC> listarPNCs();
	void crear(String token, PNC pnc);
	void modificar(String token, PNC pnc);
	void eliminar(String token, PNC pnc);
	List<PNC> listarPNCs(String token);
}
