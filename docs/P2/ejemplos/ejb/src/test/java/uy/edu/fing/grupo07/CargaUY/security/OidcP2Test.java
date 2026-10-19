package uy.edu.fing.grupo07.CargaUY.security;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OidcP2Test {
    @Test void rechazaAlgoritmoNoneSinConsultarClaves()throws Exception{
        String encabezado=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"alg\":\"none\",\"kid\":\"x\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Method m=GubOidcClient.class.getDeclaredMethod("validarIdToken",String.class,String.class);m.setAccessible(true);
        var fallo=assertThrows(InvocationTargetException.class,()->m.invoke(new GubOidcClient(),encabezado+".e30.firma","nonce"));
        assertInstanceOf(ErrorAcceso.class,fallo.getCause());
    }
@Test void verificaFirmaIssuerAudienciaNonceYVencimiento()throws Exception{
        var generador=java.security.KeyPairGenerator.getInstance("RSA");generador.initialize(2048);
        var par=generador.generateKeyPair();var publica=(java.security.interfaces.RSAPublicKey)par.getPublic();
        var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        java.util.function.Function<byte[],String> b64=bytes->java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String jwks="{\"keys\":[{\"kid\":\"prueba\",\"kty\":\"RSA\",\"n\":\""
            +b64.apply(publica.getModulus().toByteArray())+"\",\"e\":\""
            +b64.apply(publica.getPublicExponent().toByteArray())+"\"}]}";
        servidor.createContext("/realms/prueba/protocol/openid-connect/certs",ex->{
            byte[] datos=jwks.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,datos.length);ex.getResponseBody().write(datos);ex.close();
        });
        String anterior=System.getProperty("gub.issuer"),local=System.getProperty("p1.entorno.local");
        try{
            servidor.start();String issuer="http://127.0.0.1:"+servidor.getAddress().getPort()+"/realms/prueba";
            System.setProperty("gub.issuer",issuer);System.setProperty("p1.entorno.local","true");
            Method m=GubOidcClient.class.getDeclaredMethod("validarIdToken",String.class,String.class);m.setAccessible(true);
            for(int caso=0;caso<5;caso++){
                long ahora=java.time.Instant.now().getEpochSecond();
                var payload=jakarta.json.Json.createObjectBuilder().add("sub","ciudadano")
                    .add("iss",caso==1?"https://otro.example":issuer).add("aud",caso==2?"otro":"cargauy")
                    .add("nonce",caso==3?"otro":"nonce").add("iat",ahora).add("exp",caso==4?ahora-5:ahora+60).build();
                String h=b64.apply("{\"alg\":\"RS256\",\"kid\":\"prueba\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String cuerpo=h+"."+b64.apply(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var firma=java.security.Signature.getInstance("SHA256withRSA");firma.initSign(par.getPrivate());
                firma.update(cuerpo.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String jwt=cuerpo+"."+b64.apply(firma.sign());
                if(caso==0)assertNotNull(m.invoke(new GubOidcClient(),jwt,"nonce"));
                else assertThrows(InvocationTargetException.class,()->m.invoke(new GubOidcClient(),jwt,"nonce"));
            }
        }finally{
            servidor.stop(0);
            if(anterior==null)System.clearProperty("gub.issuer");else System.setProperty("gub.issuer",anterior);
            if(local==null)System.clearProperty("p1.entorno.local");else System.setProperty("p1.entorno.local",local);
        }
    }
}
