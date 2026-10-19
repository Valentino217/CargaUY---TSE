package uy.edu.fing.grupo07.CargaUY.security;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdUruguayP2Test {
 @Test void canjeBasicYDocumentoSinInventarNacimiento() throws Exception {
  String[] claves={"gub.proveedor","gub.issuer","gub.client.id","gub.client.secret","gub.redirect",
   "gub.document.pais","gub.document.tipo","gub.jwks","gub.pkce","p1.entorno.local"};
  Map<String,String> previos=new HashMap<>();for(String k:claves)previos.put(k,System.getProperty(k));
  var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  List<String> errores=Collections.synchronizedList(new ArrayList<>());
  try {
   var gen=java.security.KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var par=gen.generateKeyPair();
   var pub=(java.security.interfaces.RSAPublicKey)par.getPublic();
   java.util.function.Function<byte[],String> b64=b->Base64.getUrlEncoder().withoutPadding().encodeToString(b);
   String issuer="http://127.0.0.1:"+servidor.getAddress().getPort()+"/oidc/v1";
   long ahora=Instant.now().getEpochSecond();
   String payload=jakarta.json.Json.createObjectBuilder().add("sub","persona-verificada")
    .add("iss",issuer).add("aud","890192").add("nonce","nonce-prueba").add("iat",ahora).add("exp",ahora+60).build().toString();
   String partes=b64.apply("{\"alg\":\"RS256\",\"kid\":\"prueba\"}".getBytes(StandardCharsets.UTF_8))
    +"."+b64.apply(payload.getBytes(StandardCharsets.UTF_8));
   var firma=java.security.Signature.getInstance("SHA256withRSA");firma.initSign(par.getPrivate());
   firma.update(partes.getBytes(StandardCharsets.US_ASCII));String jwt=partes+"."+b64.apply(firma.sign());
   String jwks="{\"keys\":[{\"kid\":\"prueba\",\"kty\":\"RSA\",\"n\":\""
    +b64.apply(pub.getModulus().toByteArray())+"\",\"e\":\""+b64.apply(pub.getPublicExponent().toByteArray())+"\"}]}";
   servidor.createContext("/oidc/v1/jwks",e->{byte[] b=jwks.getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   servidor.createContext("/oidc/v1/token",e->{
    String esperado="Basic "+Base64.getEncoder().encodeToString("890192:secreto-ficticio".getBytes(StandardCharsets.UTF_8));
    if(!esperado.equals(e.getRequestHeaders().getFirst("Authorization")))errores.add("Basic");
    String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
    if(body.contains("client_secret")||body.contains("code_verifier"))errores.add("formulario");
    if(!body.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080"))errores.add("redirect");
    byte[] b=("{\"id_token\":\""+jwt+"\",\"access_token\":\"acceso-ficticio\"}").getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   servidor.createContext("/oidc/v1/userinfo",e->{
    if(!"Bearer acceso-ficticio".equals(e.getRequestHeaders().getFirst("Authorization")))errores.add("Bearer");
    byte[] b=("{\"sub\":\"persona-verificada\",\"pais_documento\":\"UY\",\"tipo_documento\":\"CI\","
      +"\"numero_documento\":\"1.234.567-8\",\"name\":\"Persona Prueba\",\"email\":\"persona@example.invalid\","
      +"\"email_verified\":true}").getBytes(StandardCharsets.UTF_8);
    e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
   System.setProperty("gub.proveedor","iduruguay");System.setProperty("gub.issuer",issuer);
   System.setProperty("gub.client.id","890192");System.setProperty("gub.client.secret","secreto-ficticio");
   System.setProperty("gub.redirect","http://localhost:8080");System.setProperty("p1.entorno.local","true");
   System.setProperty("gub.jwks",issuer+"/jwks");System.setProperty("gub.document.pais","UY");
   System.setProperty("gub.document.tipo","CI");System.setProperty("gub.pkce","false");
   servidor.start();
   var identidad=new GubOidcClient().validar("codigo-ficticio",Cripto.aleatorio(),"nonce-prueba");
   assertEquals(12345678,identidad.ci());assertNull(identidad.nacimiento());
   assertEquals(Cripto.hash(issuer+"\npersona-verificada"),identidad.subject());
   assertTrue(errores.isEmpty(),errores.toString());
   assertEquals(issuer+"/authorize",GubOidcClient.autorizacion());
   assertTrue(GubOidcClient.scopes().contains("document"));
   System.setProperty("gub.document.tipo","PASAPORTE");
   assertThrows(ErrorAcceso.class,()->new GubOidcClient().validar("otro-codigo",Cripto.aleatorio(),"nonce-prueba"));
  } finally {
   servidor.stop(0);
   for(String k:claves){if(previos.get(k)==null)System.clearProperty(k);else System.setProperty(k,previos.get(k));}
  }
 }
 @Test void movilRealBloqueadoSinCallbackRegistrado(){
  String antes=System.getProperty("gub.proveedor"), habilitado=System.getProperty("gub.mobile.enabled");
  try {
   System.setProperty("gub.proveedor","iduruguay");System.setProperty("gub.mobile.enabled","false");
   assertThrows(ErrorAcceso.class,()->new FlujosService().preparar(Cripto.pkce(Cripto.aleatorio())));
   assertThrows(ErrorAcceso.class,()->new FlujosService().consumir(Cripto.aleatorio(),Cripto.aleatorio()));
  } finally{
   if(antes==null)System.clearProperty("gub.proveedor");else System.setProperty("gub.proveedor",antes);
   if(habilitado==null)System.clearProperty("gub.mobile.enabled");else System.setProperty("gub.mobile.enabled",habilitado);
  }
 }
}