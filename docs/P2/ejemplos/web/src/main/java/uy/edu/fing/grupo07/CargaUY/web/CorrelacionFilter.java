package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import jakarta.json.Json;

@WebFilter("/*")
public class CorrelacionFilter implements Filter {
    public static final LongAdder solicitudes=new LongAdder(), errores=new LongAdder(), nanos=new LongAdder();
    private static final Logger LOG=Logger.getLogger(CorrelacionFilter.class.getName());
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)
            throws IOException,ServletException{
        var r=(HttpServletRequest)request;var s=(HttpServletResponse)response;
        String corr=r.getHeader("X-Correlation-ID");
        try{corr=UUID.fromString(corr).toString();}catch(Exception e){corr=UUID.randomUUID().toString();}
        r.setAttribute("correlacion",corr);s.setHeader("X-Correlation-ID",corr);
        long inicio=System.nanoTime();
        try{chain.doFilter(r,s);}
        finally{
            long duracion=System.nanoTime()-inicio;
            solicitudes.increment();nanos.add(duracion);if(s.getStatus()>=400)errores.increment();
            LOG.info(Json.createObjectBuilder().add("evento","http.solicitud")
                .add("correlacion",corr).add("metodo",r.getMethod()).add("status",s.getStatus())
                .add("duracionMs",duracion/1000000).build().toString());
        }
    }
}
