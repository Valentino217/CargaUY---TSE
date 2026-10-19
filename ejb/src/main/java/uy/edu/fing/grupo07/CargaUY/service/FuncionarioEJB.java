package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;

@Stateless @LocalBean
public class FuncionarioEJB implements FuncionarioEJBLocal {
    @Override public void crear(Funcionario f) {
        throw new ErrorNegocio("Alta antigua retirada: usar gestión con identidad autenticada");
    }
}