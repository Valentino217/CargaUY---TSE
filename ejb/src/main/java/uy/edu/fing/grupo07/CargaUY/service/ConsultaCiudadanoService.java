package uy.edu.fing.grupo07.CargaUY.service;
import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.security.*;
import uy.edu.fing.grupo07.CargaUY.integration.*;
import uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ConsultaCiudadanoService {
    @EJB private ControlAcceso control;
    @EJB private ClientePdi pdi;
    public ResultadoPdi consultar(String token,int ci,String correlacion){
        Acceso a=control.exigir(token);
        if(a.ci()!=ci&&!a.tiene(TipoUsuario.ADMINISTRADOR)&&!a.tiene(TipoUsuario.FUNCIONARIO))
            throw new ErrorAcceso(403,"No podés consultar datos personales de otra persona");
        return pdi.consultar(ci,correlacion);
    }
}