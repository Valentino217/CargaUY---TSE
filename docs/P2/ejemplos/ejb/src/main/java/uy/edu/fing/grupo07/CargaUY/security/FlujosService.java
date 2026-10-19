package uy.edu.fing.grupo07.CargaUY.security;
import java.time.*;
import jakarta.ejb.*;
import jakarta.persistence.*;
import uy.edu.fing.grupo07.CargaUY.domain.entity.FlujoIdentidad;
@Stateless @LocalBean
public class FlujosService {
 public record Inicio(String state,String nonce,String url){}
 @PersistenceContext(unitName="CargaUYPersistenceUnit")private EntityManager em;
 public Inicio preparar(String challenge){
  habilitado();
  if(challenge==null||!challenge.matches("[A-Za-z0-9_-]{43}"))throw new ErrorAcceso(400,"PKCE challenge inválido");
  String state=Cripto.aleatorio(),nonce=Cripto.aleatorio();
  em.persist(new FlujoIdentidad(Cripto.hash(state),challenge,nonce,Instant.now().plusSeconds(180)));
  String url=GubOidcClient.autorizacion()+"?response_type=code&scope="+GubOidcClient.url(GubOidcClient.scopes())
   +"&client_id="+GubOidcClient.url(GubOidcClient.clientId())+"&redirect_uri="+GubOidcClient.url(redirect())
   +"&state="+state+"&nonce="+nonce+"&code_challenge="+challenge+"&code_challenge_method=S256";
  return new Inicio(state,nonce,url);
 }
 @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
 public String consumir(String state,String verifier){
  habilitado();
  if(state==null||!state.matches("[A-Za-z0-9_-]{43}")||verifier==null||!verifier.matches("[A-Za-z0-9._~-]{43,128}"))
   throw new ErrorAcceso(401,"Flujo inválido");
  FlujoIdentidad f=em.find(FlujoIdentidad.class,Cripto.hash(state),LockModeType.PESSIMISTIC_WRITE);
  if(f==null||!f.getVence().isAfter(Instant.now())||!Cripto.igual(f.getChallenge(),Cripto.pkce(verifier)))
   throw new ErrorAcceso(401,"Flujo vencido o PKCE inválido");
  String nonce=f.getNonce();em.remove(f);return nonce;
 }
 private static void habilitado(){
  if(GubOidcClient.real() && (!Boolean.parseBoolean(Config.valor("gub.mobile.enabled","false")) || !GubOidcClient.pkce()))
   throw new ErrorAcceso(409,"Móvil requiere callback registrado y soporte PKCE confirmado");
 }
 public static String redirect(){return Config.valor("gub.mobile.redirect","uy.cargauy:/oauth/callback");}
 @Schedule(hour="*",minute="*/15",persistent=false)
 public void limpiar(){em.createQuery("delete from FlujoIdentidad f where f.vence<=:ahora")
   .setParameter("ahora",Instant.now()).executeUpdate();}
}
