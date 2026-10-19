package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import uy.edu.fing.grupo07.CargaUY.security.ErrorAcceso;
@Provider
public class ErroresApi implements ExceptionMapper<ErrorAcceso> {
    @Override public Response toResponse(ErrorAcceso e){
        return Response.status(e.getEstado()).entity(java.util.Map.of("error",e.getMessage())).build();
    }
}
