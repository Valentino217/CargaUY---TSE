package uy.edu.fing.grupo07.CargaUY.integration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientePdiTest {
    private static final String ID="urn:uuid:11111111-1111-4111-8111-111111111111";
    private String respuesta(String id,boolean ausente){
        String s="http://schemas.xmlsoap.org/soap/envelope/",a="http://www.w3.org/2005/08/addressing";
        String cuerpo=ausente?
            "<s:Fault><faultcode>s:Client</faultcode><faultstring>No encontrado</faultstring><detail>"
            +"<p:CiudadanoNoEncontrado><ci>12345678</ci></p:CiudadanoNoEncontrado></detail></s:Fault>":
            "<p:consultarCiudadanoResponse><ci>12345678</ci><nombre>Juan</nombre><apellido>Perez</apellido>"
            +"<fechaNacimiento>1985-05-15</fechaNacimiento><vigente>true</vigente></p:consultarCiudadanoResponse>";
        return "<s:Envelope xmlns:s=\""+s+"\" xmlns:w=\""+a+"\" xmlns:p=\"http://pdi.dnic.gub.uy/\">"
            +"<s:Header><w:Action>"+(ausente?a+"/fault":"urn:cargauy:pdi:consultarCiudadanoResponse")
            +"</w:Action><w:RelatesTo>"+id+"</w:RelatesTo></s:Header><s:Body>"+cuerpo+"</s:Body></s:Envelope>";
    }
    @Test void respuestaExitosaConAddressing()throws Exception{
        assertEquals(ResultadoPdi.Estado.DISPONIBLE,new ClientePdi().interpretar(respuesta(ID,false),12345678,ID).estado());
    }
    @Test void ciudadanoInexistenteNoEsFallaDeRed()throws Exception{
        assertEquals(ResultadoPdi.Estado.NO_ENCONTRADO,new ClientePdi().interpretar(respuesta(ID,true),12345678,ID).estado());
    }
    @Test void rechazaRespuestaDeOtraSolicitud(){
        assertThrows(Exception.class,()->new ClientePdi().interpretar(respuesta(ID,false),12345678,"otro"));
    }
    @Test void rechazaDTD(){
        assertThrows(Exception.class,()->new ClientePdi().interpretar(
            "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///no-leer'>]>"+respuesta(ID,false),12345678,ID));
    }
    @Test void limitaReintentosYAbreCircuito()throws Exception{
        var llamadas=new java.util.concurrent.atomic.AtomicInteger();
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        servidor.createContext("/pdi/ws",ex->{llamadas.incrementAndGet();ex.sendResponseHeaders(503,-1);ex.close();});
        String anterior=System.getProperty("pdi.endpoint"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();System.setProperty("pdi.endpoint","http://127.0.0.1:"+servidor.getAddress().getPort()+"/pdi/ws");
            System.setProperty("p1.entorno.local","true");
            ClientePdi c=new ClientePdi();c.iniciar();
            for(int i=0;i<4;i++)assertEquals(ResultadoPdi.Estado.NO_DISPONIBLE,c.consultar(12345678).estado());
            assertEquals(6,llamadas.get());
        }finally{
            servidor.stop(0);restaurar("pdi.endpoint",anterior);restaurar("p1.entorno.local",local);
        }
    }
    @Test void saturacionRechazaSinCrearColaIlimitada()throws Exception{
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var ejecutorServidor=java.util.concurrent.Executors.newFixedThreadPool(4);servidor.setExecutor(ejecutorServidor);
        var ejecutor=java.util.concurrent.Executors.newFixedThreadPool(12);
        servidor.createContext("/pdi/ws",ex->{
            String req=new String(ex.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var m=java.util.regex.Pattern.compile("<w:MessageID>([^<]+)</w:MessageID>").matcher(req);
            String id=m.find()?m.group(1):"";
            try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] data=respuesta(id,false).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
        });
        String anterior=System.getProperty("pdi.endpoint"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();System.setProperty("pdi.endpoint","http://127.0.0.1:"+servidor.getAddress().getPort()+"/pdi/ws");
            System.setProperty("p1.entorno.local","true");
            ClientePdi c=new ClientePdi();c.iniciar();var salida=new java.util.ArrayList<java.util.concurrent.Future<ResultadoPdi>>();
            var inicio=new java.util.concurrent.CyclicBarrier(12);
            for(int i=0;i<12;i++)salida.add(ejecutor.submit(()->{inicio.await();return c.consultar(12345678);}));
            int disponibles=0;
            for(var futuro:salida)if(futuro.get(8,java.util.concurrent.TimeUnit.SECONDS).estado()==ResultadoPdi.Estado.DISPONIBLE)disponibles++;
            assertTrue(disponibles<=4);assertTrue(c.metricas()[2]>=8);
        }finally{
            servidor.stop(0);ejecutor.shutdownNow();ejecutorServidor.shutdownNow();
            restaurar("pdi.endpoint",anterior);restaurar("p1.entorno.local",local);
        }
    }
    private void restaurar(String clave,String v){if(v==null)System.clearProperty(clave);else System.setProperty(clave,v);}
}
