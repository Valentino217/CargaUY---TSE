package uy.edu.fing.grupo07.CargaUY.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vinculos_empresa")
public class VinculoEmpresa {
    @Id @Column(length = 64) 
    private String clave;
    
    @ManyToOne(optional = false) 
    @JoinColumn(name = "usuario_id") 
    private Usuario usuario;
    
    @ManyToOne(optional = false) 
    @JoinColumn(name = "nro_empresa") 
    private Empresa empresa;
    
    @Column(nullable = false) 
    private boolean responsable;
    
    @Column(nullable = false) 
    private boolean chofer;
    
    @Version private long version;
    
    protected VinculoEmpresa() {
    }
    
    public VinculoEmpresa(Usuario u, Empresa e) {
        clave = clave(e.getNroEmpresa(), u.getId()); usuario = u; empresa = e;
    }
    
    public static String clave(int empresa, int usuario) { 
    	return empresa + ":" + usuario; 
    }
    
    public Usuario getUsuario() { 
    	return usuario; 
    }
    
    public Empresa getEmpresa() {
		return empresa; 
	}
    
    public boolean isResponsable() { 
    	return responsable; 
    }
    
    public boolean isChofer() { 
    	return chofer; 
    }
    
    public void setResponsable(boolean valor) { 
    	responsable = valor; 
    }
    
    public void setChofer(boolean valor) { 
    	chofer = valor; 
    }
}