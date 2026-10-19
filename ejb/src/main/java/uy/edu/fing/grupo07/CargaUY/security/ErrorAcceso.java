package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class ErrorAcceso extends RuntimeException {
    private final int estado;
    
    public ErrorAcceso(int estado, String mensaje) { 
    	super(mensaje); this.estado = estado; 
    }
    
    public int getEstado() { 
    	return estado; 
    }
}