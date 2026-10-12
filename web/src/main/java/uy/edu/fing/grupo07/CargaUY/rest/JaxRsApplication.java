package uy.edu.fing.grupo07.CargaUY.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Configuración base de JAX-RS para exponer endpoints bajo el path raíz /api.
 */
@ApplicationPath("/api")
public class JaxRsApplication extends Application {
}
