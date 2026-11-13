package uy.edu.fing.grupo07.CargaUY.web;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import jakarta.json.Json;
import uy.edu.fing.grupo07.CargaUY.integration.TraceContext;

@WebFilter("/*")
public class CorrelacionFilter implements Filter {
    public static final LongAdder solicitudes = new LongAdder(),
            errores = new LongAdder(),
            errores4xx = new LongAdder(),
            errores5xx = new LongAdder(),
            nanos = new LongAdder();
    private static final Logger LOG = Logger.getLogger(CorrelacionFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        var r = (HttpServletRequest) request;
        var s = (HttpServletResponse) response;
        String corr = TraceContext.normalizarCorrelacion(r.getHeader(TraceContext.HEADER_CORRELACION));
        String traceparent = TraceContext.normalizarTraceparent(r.getHeader(TraceContext.HEADER_TRACEPARENT), corr);
        r.setAttribute("correlacion", corr);
        r.setAttribute("traceparent", traceparent);
        s.setHeader(TraceContext.HEADER_CORRELACION, corr);
        s.setHeader(TraceContext.HEADER_TRACEPARENT, traceparent);
        TraceContext.Estado previo = TraceContext.capturar();
        TraceContext.fijar(corr, traceparent);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(r, s);
        } finally {
            TraceContext.restaurar(previo);
            long duracion = System.nanoTime() - inicio;
            solicitudes.increment();
            nanos.add(duracion);
            if (s.getStatus() >= 400) {
                errores.increment();
                if (s.getStatus() < 500) {
                    errores4xx.increment();
                } else {
                    errores5xx.increment();
                }
            }
            LOG.info(Json.createObjectBuilder().add("evento", "http.solicitud")
                .add("correlacion", corr).add("traceparent", traceparent)
                .add("metodo", r.getMethod()).add("status", s.getStatus())
                .add("duracionMs", duracion / 1000000).build().toString());
        }
    }
}
