package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.ITV;
import uy.edu.fing.grupo07.CargaUY.domain.entity.PNC;

@Local
public interface ITVEJBLocal {
	void crear(ITV itv);
	void modificar(ITV itv);
	void eliminar(ITV itv);
	List<ITV> listarITVs();
}
