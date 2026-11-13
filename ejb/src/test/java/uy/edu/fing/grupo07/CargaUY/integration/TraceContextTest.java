package uy.edu.fing.grupo07.CargaUY.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas Unitarias - TraceContext (W3C traceparent y X-Correlation-ID)")
class TraceContextTest {

    private static final Pattern TRACEPARENT_REGEX =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    @AfterEach
    void limpiarContexto() {
        TraceContext.limpiar();
    }

    @Test
    @DisplayName("normalizarCorrelacion preserva UUID válido, cae al contexto o genera UUID nuevo")
    void testNormalizarCorrelacion() {
        String id = UUID.randomUUID().toString();
        assertEquals(id, TraceContext.normalizarCorrelacion("  " + id + "  "));

        TraceContext.fijar(id, null);
        assertEquals(id, TraceContext.normalizarCorrelacion("no-es-uuid"));
        assertEquals(id, TraceContext.normalizarCorrelacion(null));

        TraceContext.limpiar();
        String generado = TraceContext.normalizarCorrelacion("invalido");
        assertNotNull(UUID.fromString(generado));
    }

    @Test
    @DisplayName("normalizarTraceparent valida formato W3C, rechaza ceros y deriva trace-id desde correlación")
    void testNormalizarTraceparent() {
        String corr = "123e4567-e89b-12d3-a456-426614174000";
        String traceIdEsperado = corr.replace("-", "");

        String valido = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        assertEquals(valido, TraceContext.normalizarTraceparent(" " + valido.toUpperCase() + " ", corr));

        String desdeCeros = TraceContext.normalizarTraceparent(
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
                corr
        );
        Matcher m = TRACEPARENT_REGEX.matcher(desdeCeros);
        assertTrue(m.matches());
        assertEquals(traceIdEsperado, m.group(1));

        String desdeMalformado = TraceContext.normalizarTraceparent("bad-header", corr);
        Matcher m2 = TRACEPARENT_REGEX.matcher(desdeMalformado);
        assertTrue(m2.matches());
        assertEquals(traceIdEsperado, m2.group(1));
    }

    @Test
    @DisplayName("siguienteTraceparentHijo conserva trace-id y flags del padre pero rota span-id")
    void testSiguienteTraceparentHijoPreservaTraceId() {
        String padre = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String hijo = TraceContext.siguienteTraceparentHijo(padre, null);

        Matcher m = TRACEPARENT_REGEX.matcher(hijo);
        assertTrue(m.matches());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", m.group(1));
        assertNotEquals("00f067aa0ba902b7", m.group(2));
        assertEquals("01", m.group(3));
    }

    @Test
    @DisplayName("fijar, capturar y restaurar preservan aislamiento ThreadLocal")
    void testCapturarYRestaurar() {
        assertNull(TraceContext.capturar());

        String corr1 = UUID.randomUUID().toString();
        String tp1 = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        TraceContext.fijar(corr1, tp1);
        TraceContext.Estado previo = TraceContext.capturar();
        assertNotNull(previo);
        assertEquals(corr1, TraceContext.obtenerCorrelacion());
        assertEquals(tp1, TraceContext.obtenerTraceparent());

        String corr2 = UUID.randomUUID().toString();
        TraceContext.fijar(corr2, null);
        assertEquals(corr2, TraceContext.obtenerCorrelacion());

        TraceContext.restaurar(previo);
        assertEquals(corr1, TraceContext.obtenerCorrelacion());
        assertEquals(tp1, TraceContext.obtenerTraceparent());

        TraceContext.restaurar(null);
        assertNull(TraceContext.obtenerCorrelacion());
        assertNull(TraceContext.obtenerTraceparent());
    }
}
