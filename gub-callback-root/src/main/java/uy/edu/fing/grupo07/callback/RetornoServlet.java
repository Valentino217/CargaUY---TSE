package uy.edu.fing.grupo07.callback;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Endpoint técnico para las URI raíz ya registradas en ID Uruguay. Sin vista ni sesión. */
@WebServlet(urlPatterns={"","/logout"})
public class RetornoServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Pragma","no-cache");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("X-Content-Type-Options","nosniff");
        String contexto=getServletContext().getInitParameter("cargauy.context");
        if(contexto==null||!contexto.matches("/[A-Za-z0-9_-]+")){response.sendError(503);return;}
        String state=request.getParameter("state"),code=request.getParameter("code"),error=request.getParameter("error");
        String destino=contexto+"/login";
        if(!request.getServletPath().equals("/logout")&&(state!=null||code!=null||error!=null)){
            if(state==null||!state.matches("[A-Za-z0-9_-]{43}")||(code==null)==(error==null)
                ||code!=null&&(code.isBlank()||code.length()>4096)
                ||error!=null&&!error.matches("[A-Za-z0-9_]{1,100}")){response.sendError(400);return;}
            destino=contexto+"/gub/callback?state="+URLEncoder.encode(state,StandardCharsets.UTF_8)
                +"&"+(error==null?"code":"error")+"="+URLEncoder.encode(error==null?code:error,StandardCharsets.UTF_8);
        }
        response.setStatus(303);response.setHeader("Location",destino);
    }
}
