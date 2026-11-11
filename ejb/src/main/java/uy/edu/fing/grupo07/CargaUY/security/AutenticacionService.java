package uy.edu.fing.grupo07.CargaUY.security;

import jakarta.ejb.*;

@Stateless @LocalBean
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AutenticacionService {
    @EJB private LdapClient ldap;
    @EJB private GubOidcClient gub;
    @EJB private SesionesService sesiones;
    @EJB private FlujosService flujos;

    public SesionesService.Login login(String uid, String password) {
        boolean local = Boolean.parseBoolean(Config.valor("p1.login.local", "false"));
        try {
            ldap.autenticar(uid, password);
            return sesiones.emitirLdap(uid);
        } catch (ErrorAcceso e) {
            if (local) {
                return sesiones.emitirLocal(uid, password);
            }
            throw e;
        } catch (Exception e) {
            if (local) {
                return sesiones.emitirLocal(uid, password);
            }
            throw new ErrorAcceso(401, "Credenciales inválidas");
        }
    }
    
    public SesionesService.Login loginGub(String code, String verifier, String nonce) {
        return sesiones.emitirGub(gub.validar(code, verifier, nonce));
    }

    public record LoginGubWeb(SesionesService.Login login, String idToken) {
        @Override public String toString() { return "LoginGubWeb"; }
    }

    public LoginGubWeb loginGubWeb(String code, String verifier, String nonce) {
        var identidad = gub.validarConToken(code, verifier, nonce, false);
        return new LoginGubWeb(sesiones.emitirGub(identidad.identidad()), identidad.idToken());
    }
    
    public FlujosService.Inicio prepararMovil(String challenge){
    	return flujos.preparar(challenge);
    }
    
    public SesionesService.Login loginMovil(String code,String state,String verifier){
        String nonce=flujos.consumir(state,verifier);
        return sesiones.emitirGub(gub.validar(code,verifier,nonce,true));
    }
    
    public SesionesService.Login loginDevChofer(int ci) {
        return sesiones.emitirDevChofer(ci);
    }
    
    public Acceso validarToken(String token) { 
    	return sesiones.validarToken(token); 
    }
    
    public void logout(String token) { 
    	sesiones.logout(token); 
    }
}
