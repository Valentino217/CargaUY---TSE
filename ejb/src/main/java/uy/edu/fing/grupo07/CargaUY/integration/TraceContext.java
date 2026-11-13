package uy.edu.fing.grupo07.CargaUY.integration;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto distribuido por hilo para propagar {@code X-Correlation-ID} y W3C {@code traceparent}
 * entre filtros web, clientes HTTP/SOAP y productores/consumidores JMS.
 */
public final class TraceContext {

    public static final String HEADER_CORRELACION = "X-Correlation-ID";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String JMS_PROP_CORRELACION = "X_Correlation_ID";
    public static final String JMS_PROP_TRACEPARENT = "traceparent";

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";

    private static final ThreadLocal<Estado> ACTUAL = new ThreadLocal<>();

    public record Estado(String correlacion, String traceparent) {
    }

    private TraceContext() {
    }

    public static String normalizarCorrelacion(String correlacion) {
        if (correlacion != null && !correlacion.isBlank()) {
            try {
                return UUID.fromString(correlacion.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Caer al contexto o generar un UUID nuevo cuando el formato recibido no es UUID.
            }
        }
        String delHilo = obtenerCorrelacion();
        if (delHilo != null && !delHilo.isBlank()) {
            return delHilo;
        }
        return UUID.randomUUID().toString();
    }

    public static String normalizarTraceparent(String traceparent, String correlacion) {
        if (traceparent != null && !traceparent.isBlank()) {
            String limpio = traceparent.trim().toLowerCase(Locale.ROOT);
            Matcher matcher = TRACEPARENT_PATTERN.matcher(limpio);
            if (matcher.matches()
                    && !ZERO_TRACE_ID.equals(matcher.group(1))
                    && !ZERO_SPAN_ID.equals(matcher.group(2))) {
                return limpio;
            }
        }
        return "00-" + derivarTraceId(correlacion) + "-" + generarSpanIdHex() + "-01";
    }

    public static String siguienteTraceparentHijo(String traceparentBase, String correlacion) {
        String efectivo = (traceparentBase != null && !traceparentBase.isBlank())
                ? traceparentBase
                : obtenerTraceparent();
        String padreValido = normalizarTraceparent(efectivo, correlacion);
        Matcher matcher = TRACEPARENT_PATTERN.matcher(padreValido);
        if (matcher.matches()) {
            return "00-" + matcher.group(1) + "-" + generarSpanIdHex() + "-" + matcher.group(3);
        }
        return "00-" + derivarTraceId(correlacion) + "-" + generarSpanIdHex() + "-01";
    }

    public static void fijar(String correlacion, String traceparent) {
        String corrNorm = normalizarCorrelacion(correlacion);
        String tpNorm = normalizarTraceparent(traceparent, corrNorm);
        ACTUAL.set(new Estado(corrNorm, tpNorm));
    }

    public static void restaurar(Estado previo) {
        if (previo == null) {
            ACTUAL.remove();
        } else {
            ACTUAL.set(previo);
        }
    }

    public static Estado capturar() {
        return ACTUAL.get();
    }

    public static String obtenerCorrelacion() {
        Estado estado = ACTUAL.get();
        return estado != null ? estado.correlacion() : null;
    }

    public static String obtenerTraceparent() {
        Estado estado = ACTUAL.get();
        return estado != null ? estado.traceparent() : null;
    }

    public static void limpiar() {
        ACTUAL.remove();
    }

    private static String derivarTraceId(String correlacion) {
        if (correlacion != null && !correlacion.isBlank()) {
            try {
                String hex = UUID.fromString(correlacion.trim()).toString().replace("-", "");
                if (!ZERO_TRACE_ID.equals(hex)) {
                    return hex.toLowerCase(Locale.ROOT);
                }
            } catch (IllegalArgumentException ignored) {
                // Usar UUID aleatorio si el identificador no tiene formato UUID estándar.
            }
        }
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String generarSpanIdHex() {
        long val;
        do {
            val = ThreadLocalRandom.current().nextLong();
        } while (val == 0L);
        return String.format("%016x", val);
    }
}
