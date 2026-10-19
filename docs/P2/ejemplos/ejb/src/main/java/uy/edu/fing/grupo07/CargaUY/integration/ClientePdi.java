package uy.edu.fing.grupo07.CargaUY.integration;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import uy.edu.fing.grupo07.CargaUY.security.Config;

@Singleton @LocalBean @Lock(LockType.READ)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ClientePdi {
    private static final String S="http://schemas.xmlsoap.org/soap/envelope/";
    private static final String A="http://www.w3.org/2005/08/addressing";
    private static final String P="http://pdi.dnic.gub.uy/";
    private static final String ACTION="urn:cargauy:pdi:consultarCiudadano";
    private static final Logger LOG=Logger.getLogger(ClientePdi.class.getName());
    private final Semaphore capacidad=new Semaphore(4);
    private final AtomicLong llamadas=new AtomicLong(), fallas=new AtomicLong(), rechazadas=new AtomicLong();
    private HttpClient http; private URI endpoint;
    private int consecutivas; private long abiertoHasta; private boolean prueba;
    @PostConstruct public void iniciar(){
        endpoint=URI.create(Config.valor("pdi.endpoint","http://localhost:8081/pdi/ws"));
        boolean local=Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && ("localhost".equals(endpoint.getHost())||"127.0.0.1".equals(endpoint.getHost()));
        if(!"https".equals(endpoint.getScheme())&&!("http".equals(endpoint.getScheme())&&local))
            throw new IllegalStateException("PDI requiere HTTPS fuera del entorno local");
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }
    public ResultadoPdi consultar(int ci){return consultar(ci,UUID.randomUUID().toString());}
    public ResultadoPdi consultar(int ci,String correlacion){
        if(ci<=0) throw new IllegalArgumentException("CI inválida");
        try{correlacion=UUID.fromString(correlacion).toString();}
        catch(Exception e){correlacion=UUID.randomUUID().toString();}
        if(!capacidad.tryAcquire()){rechazadas.incrementAndGet();return ResultadoPdi.indisponible();}
        boolean admitida=false, exito=false;
        long inicio=System.nanoTime();
        try{
            admitida=admitir();
            if(!admitida){rechazadas.incrementAndGet();return ResultadoPdi.indisponible();}
            for(int intento=0;intento<2;intento++){
                String id="urn:uuid:"+UUID.randomUUID();
                try{
                    String xml="<s:Envelope xmlns:s=\""+S+"\" xmlns:w=\""+A+"\" xmlns:p=\""+P+"\">"
                        +"<s:Header><w:Action s:mustUnderstand=\"1\">"+ACTION+"</w:Action>"
                        +"<w:To s:mustUnderstand=\"1\">"+esc(endpoint.toString())+"</w:To>"
                        +"<w:MessageID>"+id+"</w:MessageID><w:ReplyTo><w:Address>"+A+"/anonymous"
                        +"</w:Address></w:ReplyTo></s:Header><s:Body>"
                        +"<p:consultarCiudadano><ci>"+ci+"</ci></p:consultarCiudadano></s:Body></s:Envelope>";
                    llamadas.incrementAndGet();
                    HttpResponse<String> r=http.send(HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(2)).header("Content-Type","text/xml;charset=UTF-8")
                        .header("SOAPAction","\""+ACTION+"\"").header("X-Correlation-ID",correlacion)
                        .POST(HttpRequest.BodyPublishers.ofString(xml)).build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if(r.statusCode()!=200&&r.statusCode()!=500) throw new IOException("HTTP");
                    ResultadoPdi resultado=interpretar(r.body(),ci,id);
                    if(r.statusCode()==500&&resultado.estado()!=ResultadoPdi.Estado.NO_ENCONTRADO)
                        throw new IOException("Estado HTTP inconsistente");
                    exito=true;return resultado;
                }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                catch(Exception e){if(intento==0) continue;}
            }
            fallas.incrementAndGet();return ResultadoPdi.indisponible();
        }finally{
            if(admitida) terminar(exito);
            capacidad.release();
            LOG.info("{\"evento\":\"pdi.consulta\",\"correlacion\":\""+escJson(correlacion)
                +"\",\"ok\":"+exito+",\"duracionMs\":"+((System.nanoTime()-inicio)/1000000)+"}");
        }
    }
    private synchronized boolean admitir(){
        if(abiertoHasta==0)return true;
        if(System.nanoTime()<abiertoHasta||prueba)return false;
        prueba=true;return true; // Una sola consulta prueba recuperación.
    }
    private synchronized void terminar(boolean exito){
        if(prueba){
            prueba=false;
            if(exito){consecutivas=0;abiertoHasta=0;}
            else abiertoHasta=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        }else if(abiertoHasta==0){
            if(exito)consecutivas=0;
            else if(++consecutivas>=3)abiertoHasta=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        }
    }
    public long[] metricas(){return new long[]{llamadas.get(),fallas.get(),rechazadas.get()};}
    ResultadoPdi interpretar(String xml,int ci,String id)throws Exception{
        if(xml.length()>65536)throw new IOException("Respuesta grande");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        Document d=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element envelope=d.getDocumentElement();
        if(!S.equals(envelope.getNamespaceURI())||!"Envelope".equals(envelope.getLocalName()))
            throw new IOException("Envelope inválido");
        Element header=unico(envelope,S,"Header"), body=unico(envelope,S,"Body");
        if(!id.equals(unico(header,A,"RelatesTo").getTextContent()))throw new IOException("Correlación SOAP inválida");
        String action=unico(header,A,"Action").getTextContent();
        NodeList fault=body.getElementsByTagNameNS(S,"Fault");
        if(fault.getLength()>0){
            if(!(A+"/fault").equals(action))throw new IOException("Action de Fault inválida");
            Element detalle=unico((Element)fault.item(0),null,"detail");
            Element ausente=unico(detalle,P,"CiudadanoNoEncontrado");
            if(Integer.parseInt(texto(ausente,"ci"))!=ci)throw new IOException("CI inválida");
            return new ResultadoPdi(ResultadoPdi.Estado.NO_ENCONTRADO,ci,null,null,null,null,
                "No existen datos para esa CI");
        }
        if(!(ACTION+"Response").equals(action))throw new IOException("Action inválida");
        Element r=unico(body,P,"consultarCiudadanoResponse");
        if(Integer.parseInt(texto(r,"ci"))!=ci)throw new IOException("CI inválida");
        String vigente=texto(r,"vigente");
        if(!Set.of("true","false","1","0").contains(vigente))throw new IOException("Booleano inválido");
        return new ResultadoPdi(ResultadoPdi.Estado.DISPONIBLE,ci,texto(r,"nombre"),texto(r,"apellido"),
            LocalDate.parse(texto(r,"fechaNacimiento")),vigente.equals("true")||vigente.equals("1"),"PDI consultada");
    }
    private Element unico(Element padre,String ns,String nombre)throws IOException{
        Element resultado=null;
        for(Node n=padre.getFirstChild();n!=null;n=n.getNextSibling())
            if(n instanceof Element e&&nombre.equals(e.getLocalName())
                    && Objects.equals(ns,e.getNamespaceURI())){
                if(resultado!=null)throw new IOException("Elemento duplicado");resultado=e;
            }
        if(resultado==null)throw new IOException("Elemento ausente: "+nombre);
        return resultado;
    }
    private String texto(Element p,String campo)throws IOException{
        String v=unico(p,null,campo).getTextContent().trim();
        if(v.isBlank())throw new IOException("Campo vacío");return v;
    }
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");}
    private static String escJson(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","").replace("\r","");}
}
