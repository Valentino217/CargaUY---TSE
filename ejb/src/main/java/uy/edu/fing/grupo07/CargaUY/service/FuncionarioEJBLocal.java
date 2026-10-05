package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.Local;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;

@Local
public interface FuncionarioEJBLocal {
    void crear(Funcionario funcionario);
}
