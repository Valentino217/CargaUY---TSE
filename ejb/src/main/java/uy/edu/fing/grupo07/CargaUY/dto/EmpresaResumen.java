package uy.edu.fing.grupo07.CargaUY.dto;

import java.util.List;
import uy.edu.fing.grupo07.CargaUY.domain.entity.Empresa;

public record EmpresaResumen(int numero, String razonSocial, String nombrePublico,
        String direccion, boolean activa, List<Integer> ciudadanos, long version) implements java.io.Serializable {

    public int getNumero() { return numero; }
    public String getRazonSocial() { return razonSocial; }
    public String getNombrePublico() { return nombrePublico; }
    public String getDireccion() { return direccion; }
    public boolean isActiva() { return activa; }
    public List<Integer> getCiudadanos() { return ciudadanos; }
    public long getVersion() { return version; }
	
    public static EmpresaResumen desde(Empresa e) {
        return new EmpresaResumen(e.getNroEmpresa(), e.getRazonSocial(),
            e.getNombrePublico(), e.getDireccion(), e.isActiva(),
            e.getCiudadanos().stream().map(c -> c.getId()).toList(), e.getVersion());
    }
}