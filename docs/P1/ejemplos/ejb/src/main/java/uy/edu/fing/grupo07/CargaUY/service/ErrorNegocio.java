package uy.edu.fing.grupo07.CargaUY.service;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class ErrorNegocio extends RuntimeException {
    public ErrorNegocio(String mensaje) { super(mensaje); }
}
