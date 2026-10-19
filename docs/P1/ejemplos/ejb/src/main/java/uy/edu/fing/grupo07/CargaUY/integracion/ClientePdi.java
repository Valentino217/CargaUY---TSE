package uy.edu.fing.grupo07.CargaUY.integracion;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;

@Singleton
@LocalBean
@Lock(LockType.WRITE)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ClientePdi {
    private static final Logger LOG = Logger.getLogger(ClientePdi.class.getName());
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String PDI = "http://pdi.dnic.gub.uy/";
    private HttpClient http;
    private URI endpoint;
    private int fallasConsecutivas;
    private long abiertoHasta;
    @PostConstruct
    public void iniciar() {
        String url = System.getProperty("pdi.endpoint");
        if (url == null || url.isBlank()) url = System.getenv("PDI_ENDPOINT");
        if (url == null || url.isBlank()) url = "http://localhost:8081/pdi/ws";
        endpoint = URI.create(url);
        if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme()))
            throw new IllegalStateException("Endpoint PDI debe ser HTTP o HTTPS");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }

    public ResultadoPdi consultar(int ci) {
        if (ci <= 0) throw new IllegalArgumentException("CI debe ser positiva");
        if (System.nanoTime() < abiertoHasta) return ResultadoPdi.indisponible();
        // Una consulta SOAP de lectura admite un reintento limitado.
        for (int intento = 0; intento < 2; intento++) {
            try {
                String xml = "<soap:Envelope xmlns:soap=\"" + SOAP
                    + "\" xmlns:pdi=\"" + PDI + "\"><soap:Body>"
                    + "<pdi:consultarCiudadano><ci>" + ci + "</ci>"
                    + "</pdi:consultarCiudadano></soap:Body></soap:Envelope>";
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(xml)).build();
                HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
                if (response.statusCode() != 200)
                    throw new IOException("Estado HTTP no exitoso");
                ResultadoPdi resultado = interpretar(response.body(), ci);
                fallasConsecutivas = 0;
                abiertoHasta = 0;
                return resultado;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                registrarFalla();
                return ResultadoPdi.indisponible();
            } catch (IOException e) {
                if (intento == 1) registrarFalla();
            } catch (Exception e) {
                // XML inválido o Fault: no fabricar un ciudadano ni reintentar errores de contrato.
                registrarFalla();
                return ResultadoPdi.indisponible();
            }
        }
        return ResultadoPdi.indisponible();
    }

    private void registrarFalla() {
        fallasConsecutivas++;
        if (fallasConsecutivas >= 3)
            abiertoHasta = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        LOG.warning("PDI: consulta no disponible; fallas consecutivas=" + fallasConsecutivas);
    }

    // Visibilidad de paquete para probar el contrato sin hacer llamadas de red.
    ResultadoPdi interpretar(String xml, int solicitada) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element envelope = doc.getDocumentElement();
        if (!SOAP.equals(envelope.getNamespaceURI()) || !"Envelope".equals(envelope.getLocalName()))
            throw new IOException("No es SOAP 1.1");
        NodeList bodies = doc.getElementsByTagNameNS(SOAP, "Body");
        if (bodies.getLength() != 1) throw new IOException("Body inválido");
        Element body = (Element) bodies.item(0);
        if (body.getElementsByTagNameNS(SOAP, "Fault").getLength() > 0)
            throw new IOException("SOAP Fault no tipificado por el contrato actual");
        NodeList respuestas = body.getElementsByTagNameNS(PDI, "consultarCiudadanoResponse");
        if (respuestas.getLength() != 1) throw new IOException("Respuesta inesperada");
        Element respuesta = (Element) respuestas.item(0);
        int ci = Integer.parseInt(campo(respuesta, "ci"));
        if (ci != solicitada) throw new IOException("CI de respuesta no coincide");
        String vigente = campo(respuesta, "vigente");
        if (!vigente.equals("true") && !vigente.equals("false")
                && !vigente.equals("1") && !vigente.equals("0"))
            throw new IOException("Booleano inválido");
        return new ResultadoPdi(ResultadoPdi.Estado.DISPONIBLE, ci,
            campo(respuesta, "nombre"), campo(respuesta, "apellido"),
            LocalDate.parse(campo(respuesta, "fechaNacimiento")),
            vigente.equals("true") || vigente.equals("1"), "Datos obtenidos del mock PDI");
    }
    private String campo(Element padre, String nombre) throws IOException {
        String valor = null;
        for (Node n = padre.getFirstChild(); n != null; n = n.getNextSibling()) {
            // El WSDL actual define elementos locales sin namespace.
            if (n instanceof Element e && nombre.equals(e.getLocalName())
                    && (e.getNamespaceURI() == null || e.getNamespaceURI().isEmpty())) {
                if (valor != null) throw new IOException("Campo repetido");
                valor = e.getTextContent().trim();
            }
        }
        if (valor == null || valor.isEmpty()) throw new IOException("Campo obligatorio ausente");
        return valor;
    }
}
