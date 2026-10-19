package uy.edu.fing.grupo07.CargaUY.security;

import java.io.StringReader;
import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.time.*;
import java.util.*;
import jakarta.ejb.*;
import jakarta.json.*;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class GubOidcClient {
    public record Identidad(String subject, int ci, String nombre, String mail, LocalDate nacimiento) {
    }
    
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public static String issuer() {
        return Config.valor("gub.issuer", "http://localhost:8083/realms/cargauy-test");
    }
    
    public static String clientId() { 
    	return Config.valor("gub.client.id", "cargauy"); 
    }
    
    public static String redirect() { 
    	return Config.requerido("gub.redirect"); 
    }
    
    public static String url(String s) { 
    	return URLEncoder.encode(s, StandardCharsets.UTF_8); 
    }
    
    public static boolean real() { 
    	return "iduruguay".equals(Config.valor("gub.proveedor","keycloak")); 
    }
    
    public static boolean pkce() { 
    	return Boolean.parseBoolean(Config.valor("gub.pkce",real()?"false":"true")); 
    }
    
    public static String autorizacion() {
        return real() ? issuer()+"/authorize" : issuer()+"/protocol/openid-connect/auth";
    }
    
    public static String scopes() { 
    	return real()?"openid profile personal_info email document":"openid profile email"; 
    }
    
    private URI endpoint(String sufijo) {
        String ruta = real() ? (sufijo.equals("certs")?"/jwks":"/"+sufijo) : "/protocol/openid-connect/"+sufijo;
        URI u = URI.create(real() && sufijo.equals("certs")
            ? Config.valor("gub.jwks",issuer()+ruta) : issuer()+ruta);
        boolean local = Boolean.parseBoolean(Config.valor("p1.entorno.local","false"))
            && ("localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost()));
        if (!"https".equals(u.getScheme()) && !("http".equals(u.getScheme()) && local))
            throw new ErrorAcceso(503,"El proveedor de identidad requiere HTTPS");
        return u;
    }
    
    public Identidad validar(String code,String verifier,String nonce){
    	return validar(code,verifier,nonce,false);
    }
    
    public Identidad validar(String code, String verifier, String nonce, boolean movil) {
        try {
            if (code == null || code.length() > 4096 || verifier == null || nonce == null)
                throw new ErrorAcceso(401,"Respuesta de identidad inválida");
            String datos = "grant_type=authorization_code"
                + "&redirect_uri=" + url(movil?FlujosService.redirect():redirect()) + "&code=" + url(code)
                + (pkce() ? "&code_verifier=" + url(verifier) : "");
            String basic = Base64.getEncoder().encodeToString((url(clientId())+":"
                +url(Config.requerido("gub.client.secret"))).getBytes(StandardCharsets.UTF_8));
            JsonObject token = pedir(HttpRequest.newBuilder(endpoint("token"))
                .header("Authorization","Basic "+basic)
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(datos)));
            JsonObject claims = validarIdToken(token.getString("id_token"), nonce);
            String acceso = token.getString("access_token");
            JsonObject info = pedir(HttpRequest.newBuilder(endpoint("userinfo"))
                .header("Authorization","Bearer " + acceso).GET());
            if (!claims.getString("sub").equals(info.getString("sub")))
                throw new ErrorAcceso(401,"Identidad inconsistente");
            if (real()) {
                // No se trata un pasaporte extranjero como CI.
                String pais = info.getString("pais_documento",""), tipo = info.getString("tipo_documento","");
                if (!pais.equalsIgnoreCase(Config.requerido("gub.document.pais"))
                        || !tipo.equalsIgnoreCase(Config.requerido("gub.document.tipo")))
                    throw new ErrorAcceso(409,"Se requiere documento de tipo CI uruguaya configurado");
            }
            String documento = info.getString(real()?"numero_documento":"ci","").replaceAll("[. \\-]","");
            if (!documento.matches("[0-9]{7,8}")) throw new ErrorAcceso(409,"Documento ausente o incompatible");
            int ci = Integer.parseInt(documento);
            // ID Uruguay no anuncia fecha de nacimiento; no se inventa ni se consulta un mock para completarla.
            LocalDate nacimiento = real()?null:LocalDate.parse(info.getString("fechaNacimiento"));
            String mail = info.getString("email",""), nombre = info.getString("name",
                info.getString("nombre_completo",""));
            if (ci <= 0 || nombre.isBlank() || nombre.length()>100 || mail.length()>150
                    || !mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    || nacimiento!=null && nacimiento.isAfter(LocalDate.now())
                    || !info.getBoolean("email_verified",false))
                throw new ErrorAcceso(409,"Perfil externo incompleto o correo sin verificar");
            // Separa los identificadores de proveedores aunque dos emitan el mismo sub.
            String subject = real()?Cripto.hash(issuer()+"\n"+claims.getString("sub")):claims.getString("sub");
            return new Identidad(subject,ci,nombre,mail,nacimiento);
        } catch (ErrorAcceso e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ErrorAcceso(503,"Proveedor interrumpido");
        } catch (Exception e) { throw new ErrorAcceso(503,"No se pudo verificar el proveedor de identidad"); }
    }
    
    private JsonObject validarIdToken(String jwt, String nonce) 
		throws Exception {
	        String[] p = jwt.split("\\.");
	        if (p.length != 3) throw new ErrorAcceso(401,"ID token inválido");
	        Base64.Decoder d = Base64.getUrlDecoder();
	        JsonObject h = json(new String(d.decode(p[0]),StandardCharsets.UTF_8));
	        if (!"RS256".equals(h.getString("alg"))) throw new ErrorAcceso(401,"Algoritmo no admitido");
	        JsonArray keys = pedir(HttpRequest.newBuilder(endpoint("certs")).GET()).getJsonArray("keys");
	        JsonObject key = keys.stream().map(v -> (JsonObject)v)
	            .filter(k -> h.getString("kid").equals(k.getString("kid"))
	                && "RSA".equals(k.getString("kty"))).findFirst()
	            .orElseThrow(() -> new ErrorAcceso(401,"Clave desconocida"));
	        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
	            new BigInteger(1,d.decode(key.getString("n"))),
	            new BigInteger(1,d.decode(key.getString("e")))));
	        Signature firma = Signature.getInstance("SHA256withRSA"); firma.initVerify(pub);
	        firma.update((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII));
	        if (!firma.verify(d.decode(p[2]))) throw new ErrorAcceso(401,"Firma de identidad inválida");
	        JsonObject c = json(new String(d.decode(p[1]),StandardCharsets.UTF_8));
	        JsonValue aud = c.get("aud");
	        boolean audiencia = aud instanceof JsonString s ? clientId().equals(s.getString())
	            : aud instanceof JsonArray a && a.stream().anyMatch(v -> v instanceof JsonString s
	                && clientId().equals(s.getString()));
	        long ahora = Instant.now().getEpochSecond();
	        if (!issuer().equals(c.getString("iss")) || !audiencia
	                || !Cripto.igual(nonce,c.getString("nonce",""))
	                || c.getJsonNumber("exp").longValue() <= ahora
	                || c.getJsonNumber("iat").longValue() > ahora+30
	                || c.containsKey("nbf") && c.getJsonNumber("nbf").longValue()>ahora+30
	                || c.containsKey("azp") && !clientId().equals(c.getString("azp"))
	                || aud instanceof JsonArray a && a.size()>1 && !c.containsKey("azp"))
	            throw new ErrorAcceso(401,"Claims de identidad inválidos");
	        return c;
	    }
    
    private JsonObject pedir(HttpRequest.Builder b) 
		throws Exception {
	        HttpResponse<String> r = http.send(b.timeout(Duration.ofSeconds(3)).build(),
	            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	        if (r.statusCode()!=200) throw new ErrorAcceso(r.statusCode()>=500?503:401,
	            "Proveedor rechazó la operación");
	        if (r.body().length()>131072) throw new ErrorAcceso(503,"Respuesta demasiado grande");
	        return json(r.body());
	    }
    
    private JsonObject json(String s) {
        try (JsonReader reader = Json.createReader(new StringReader(s))) { return reader.readObject(); }
    }
}