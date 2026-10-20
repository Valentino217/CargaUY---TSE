package uy.edu.fing.grupo07.CargaUY.service;

import java.util.List;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Usuario;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;
import uy.edu.fing.grupo07.CargaUY.domain.entity.GuiaDeViaje;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Vehiculo;

@Local
public interface GuiaDeViajeEJBLocal {
	void crear(GuiaDeViaje guia);
	void modificar(GuiaDeViaje guia);
	void eliminar(GuiaDeViaje guia);
	GuiaDeViaje buscarGuias(Long id);
	List<GuiaDeViaje> listarGuias();
	List<Usuario> listarChoferesPorEmpresa(int nroEmpresa);
	List<GuiaDeViaje> listarGuiasPorCiudadano(int ciudadanoId);
	
}
