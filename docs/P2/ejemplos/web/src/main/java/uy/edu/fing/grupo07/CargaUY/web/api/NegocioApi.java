package uy.edu.fing.grupo07.CargaUY.web.api;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import uy.edu.fing.grupo07.CargaUY.service.ErrorNegocio;
@Provider
public class NegocioApi implements ExceptionMapper<ErrorNegocio>{
 public Response toResponse(ErrorNegocio e){return Response.status(400)
  .entity(java.util.Map.of("error",e.getMessage())).build();}
}
