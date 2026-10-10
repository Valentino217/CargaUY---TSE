package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;

@Local
public interface VehiculoEJBLocal {
	List<Vehiculo> listarVehiculos();
	List<Empresa> listarEmpresas();
	void crear(Vehiculo vehiculo);
	void modificar(Vehiculo vehiculo);
	void eliminar(Vehiculo vehiculo);
	Vehiculo buscar(String matricula);
}
