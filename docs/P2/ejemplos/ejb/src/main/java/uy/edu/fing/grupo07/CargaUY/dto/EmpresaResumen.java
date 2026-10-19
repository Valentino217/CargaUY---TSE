package uy.edu.fing.grupo07.CargaUY.dto;

import java.util.List;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;

public record EmpresaResumen(int numero, String razonSocial, String nombrePublico,
        String direccion, boolean activa, List<Integer> ciudadanos, long version) {
	
    public static EmpresaResumen desde(Empresa e) {
        return new EmpresaResumen(e.getNroEmpresa(), e.getRazonSocial(),
            e.getNombrePublico(), e.getDireccion(), e.isActiva(),
            e.getCiudadanos().stream().map(c -> c.getId()).toList(), e.getVersion());
    }
}
