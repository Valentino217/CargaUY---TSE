package uy.edu.fing.grupo07.CargaUY.integracion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientePdiTest {
    private String respuesta(String ci) {
        return "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<s:Body><p:consultarCiudadanoResponse xmlns:p=\"http://pdi.dnic.gub.uy/\">"
            + "<ci>" + ci + "</ci><nombre>Juan</nombre><apellido>Perez</apellido>"
            + "<fechaNacimiento>1985-05-15</fechaNacimiento><vigente>true</vigente>"
            + "</p:consultarCiudadanoResponse></s:Body></s:Envelope>";
    }
    @Test void interpretaElContratoActual() throws Exception {
        ResultadoPdi r = new ClientePdi().interpretar(respuesta("12345678"), 12345678);
        assertEquals(ResultadoPdi.Estado.DISPONIBLE, r.estado());
        assertEquals("Juan", r.nombre());
    }
    @Test void noAceptaOtraCedula() {
        assertThrows(Exception.class,
            () -> new ClientePdi().interpretar(respuesta("11111111"), 12345678));
    }
    @Test void noAceptaXmlConEntidadesExternas() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///no-leer'>]>"
            + respuesta("12345678");
        assertThrows(Exception.class, () -> new ClientePdi().interpretar(xml, 12345678));
    }
    @Test void fallbackNoInventaDatos() {
        ResultadoPdi r = ResultadoPdi.indisponible();
        assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, r.estado());
        assertNull(r.nombre());
        assertNull(r.ci());
    }
@Test void consultaHttpRealContraUnServicioLocalDePrueba() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pdi/ws", exchange -> {
            String peticion = new String(exchange.getRequestBody().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            byte[] bytes = respuesta(peticion.contains("<ci>12345678</ci>")
                ? "12345678" : "11111111").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        String anterior = System.getProperty("pdi.endpoint");
        try {
            server.start();
            System.setProperty("pdi.endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/pdi/ws");
            ClientePdi cliente = new ClientePdi(); cliente.iniciar();
            ResultadoPdi r = cliente.consultar(12345678);
            assertEquals(ResultadoPdi.Estado.DISPONIBLE, r.estado());
            assertEquals(12345678, r.ci());
        } finally {
            server.stop(0);
            if (anterior == null) System.clearProperty("pdi.endpoint");
            else System.setProperty("pdi.endpoint", anterior);
        }
    }
    @Test void reintentaConLimiteYCortaTrasFallasSostenidas() throws Exception {
        java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pdi/ws", exchange -> {
            llamadas.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        String anterior = System.getProperty("pdi.endpoint");
        try {
            server.start();
            System.setProperty("pdi.endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/pdi/ws");
            ClientePdi cliente = new ClientePdi(); cliente.iniciar();
            for (int i = 0; i < 3; i++)
                assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, cliente.consultar(12345678).estado());
            assertEquals(6, llamadas.get()); // Dos intentos por consulta.
            assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE, cliente.consultar(12345678).estado());
            assertEquals(6, llamadas.get()); // Circuito abierto: ninguna llamada adicional.
        } finally {
            server.stop(0);
            if (anterior == null) System.clearProperty("pdi.endpoint");
            else System.setProperty("pdi.endpoint", anterior);
        }
    }
}
