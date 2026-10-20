package uy.edu.fing.grupo07.CargaUY.web;
import java.io.IOException;
import java.time.Instant;
import jakarta.ejb.EJB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import uy.edu.fing.grupo07.CargaUY.security.*;

@WebServlet(urlPatterns={"/gub/iniciar","/gub/callback"})
public class GubServlet extends HttpServlet {
    @EJB private AutenticacionService auth;
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse s) throws IOException {
        if(!WebSeguridad.transporte(r)) { s.sendError(403); return; }
        WebSeguridad.preparar(r,s);
        if(r.getServletPath().equals("/gub/iniciar")) {
            String state=Cripto.aleatorio(), nonce=Cripto.aleatorio(), verifier=Cripto.aleatorio();
            HttpSession ses=r.getSession(); ses.setAttribute("oidcState",state);
            ses.setAttribute("oidcNonce",nonce); ses.setAttribute("oidcVerifier",verifier);
            ses.setAttribute("oidcInicio",Instant.now());
            s.sendRedirect(GubOidcClient.autorizacion()+"?response_type=code"
                +"&scope="+GubOidcClient.url(GubOidcClient.scopes())+"&client_id="+GubOidcClient.url(GubOidcClient.clientId())
                +"&redirect_uri="+GubOidcClient.url(GubOidcClient.redirect())
                +"&state="+state+"&nonce="+nonce+(GubOidcClient.pkce()?"&code_challenge="+Cripto.pkce(verifier)
                +"&code_challenge_method=S256":""));
            return;
        }
        HttpSession ses=r.getSession();
        String state=(String)ses.getAttribute("oidcState");
        String nonce=(String)ses.getAttribute("oidcNonce");
        String verifier=(String)ses.getAttribute("oidcVerifier");
        Instant inicio=(Instant)ses.getAttribute("oidcInicio");
        ses.removeAttribute("oidcState"); ses.removeAttribute("oidcNonce");
        ses.removeAttribute("oidcVerifier"); ses.removeAttribute("oidcInicio");
        if(!Cripto.igual(state,r.getParameter("state"))||inicio==null
                ||inicio.plusSeconds(180).isBefore(Instant.now())) {
            WebSeguridad.error(s,401,"Inicio de sesión vencido o inválido"); return;
        }
        if(r.getParameter("error")!=null) {
            WebSeguridad.error(s,401,"El proveedor no autorizó el ingreso"); return;
        }
        try {
            WebSeguridad.iniciar(r,auth.loginGub(r.getParameter("code"),verifier,nonce));
            s.sendRedirect(r.getContextPath()+"/index.xhtml");
        } catch(ErrorAcceso e) { WebSeguridad.error(s,e.getEstado(),e.getMessage()); }
    }
}
