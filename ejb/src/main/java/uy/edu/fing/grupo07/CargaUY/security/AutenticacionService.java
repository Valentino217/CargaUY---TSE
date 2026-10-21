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
        // No hay fallback a contraseñas de PostgreSQL si LDAP se cae.
        ldap.autenticar(uid, password);
        return sesiones.emitirLdap(uid);
    }
    
    public SesionesService.Login loginGub(String code, String verifier, String nonce) {
        return sesiones.emitirGub(gub.validar(code, verifier, nonce));
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