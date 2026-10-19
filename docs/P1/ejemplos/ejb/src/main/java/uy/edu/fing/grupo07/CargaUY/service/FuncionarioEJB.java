package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Funcionario;
import uy.edu.fing.grupo07.CargaUY.dto.*;

@Stateless
@LocalBean
public class FuncionarioEJB implements FuncionarioEJBLocal {
    @EJB private GestionPerfilesService perfiles;
    @Override
    public void crear(Funcionario funcionario) {
        int id = perfiles.crearUsuario(new DatosUsuario(TipoUsuario.FUNCIONARIO,
            funcionario.getNombre(), funcionario.getMail(), funcionario.getFechaNac(),
            funcionario.getCi(), funcionario.getPassword(), funcionario.getNroFuncionario(),
            funcionario.getDepartamento(), null));
        funcionario.setId(id);
    }
}
