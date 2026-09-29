package capa_negocio;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;

@Local
public interface FuncionarioEJBLocal {
	 public void crear(Funcionario funcionario);
}
