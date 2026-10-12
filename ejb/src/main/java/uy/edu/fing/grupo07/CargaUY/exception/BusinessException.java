package uy.edu.fing.grupo07.CargaUY.exception;

/**
 * Excepción de regla de negocio para la plataforma CargaUY.
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
