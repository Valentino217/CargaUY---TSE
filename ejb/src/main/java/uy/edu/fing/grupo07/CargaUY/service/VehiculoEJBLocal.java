package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;

@Local
public interface VehiculoEJBLocal {
	List<Vehiculo> listarVehiculos();
	List<Vehiculo> listarVehiculosPorEmpresa(int nroEmpresa);
	List<Empresa> listarEmpresasPorCiudadano(int ciudadanoId);
	List<Vehiculo> listarVehiculosPorCiudadano(int ciudadanoId);
	List<Empresa> listarEmpresasParaActor(String token);
	List<Empresa> listarEmpresasParaPortal(String token);
	List<Vehiculo> listarParaActor(String token);
	List<Vehiculo> listarParaPortal(String token);
	List<Vehiculo> listarParaFiscalizacion(String token);
	Vehiculo buscar(String token, String matricula);
	void crear(String token, Vehiculo vehiculo);
	void modificar(String token, Vehiculo vehiculo);
	void eliminar(String token, Vehiculo vehiculo);
	boolean tieneGuiaEnCurso(String token, String matricula);
	boolean estaAptoParaOperar(String token, String matricula);
	boolean estaAptoParaOperarEnPortal(String token, String matricula);
	List<Empresa> listarEmpresas();
	void crear(Vehiculo vehiculo);
	void modificar(Vehiculo vehiculo);
	void eliminar(Vehiculo vehiculo);
	Vehiculo buscar(String matricula);
	boolean tieneGuiaEnCurso(String matricula);
	boolean estaAptoParaOperar(String matricula);
	boolean estaAptoParaOperar(String matricula, java.time.LocalDate fechaReferencia);
}
