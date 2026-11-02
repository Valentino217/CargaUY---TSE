package uy.edu.fing.grupo07.CargaUY.exception;

import jakarta.ejb.ApplicationException;

/**
 * Excepción de regla de negocio para la plataforma CargaUY.
 */
@ApplicationException(rollback = false)
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
