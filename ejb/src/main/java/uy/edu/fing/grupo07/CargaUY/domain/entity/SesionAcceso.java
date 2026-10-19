package uy.edu.fing.grupo07.CargaUY.domain.entity;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Cacheable(false)
@Table(name = "sesiones_acceso")
public class SesionAcceso {
    @Id @Column(length = 64) 
    private String hash;
    
    @ManyToOne(optional = false) 
    @JoinColumn(name = "usuario_id") 
    private Usuario usuario;
    
    @Column(nullable = false) 
    private Instant vence;
    
    protected SesionAcceso() {
    }
    
    public SesionAcceso(String hash, Usuario usuario, Instant vence) {
        this.hash = hash; this.usuario = usuario; this.vence = vence;
    }
    
    public Usuario getUsuario() { 
    	return usuario; 
    }
    public Instant getVence() { 
    	return vence; 
    }
}