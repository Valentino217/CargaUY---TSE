<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%@ page import="java.net.URLEncoder,java.nio.charset.StandardCharsets" %>
<%
response.setHeader("Cache-Control","no-store");
response.setHeader("Pragma","no-cache");
response.setHeader("Referrer-Policy","no-referrer");
response.setHeader("X-Content-Type-Options","nosniff");
String state=request.getParameter("state"), code=request.getParameter("code"), error=request.getParameter("error");
String contexto=application.getInitParameter("cargauy.context");
if(contexto==null || !contexto.matches("/[A-Za-z0-9_-]+")) { response.sendError(503); return; }
if(code!=null || error!=null || state!=null) {
    if(state==null || !state.matches("[A-Za-z0-9_-]{43}") || (code==null)==(error==null)
        || code!=null && (code.isBlank() || code.length()>4096)
        || error!=null && !error.matches("[A-Za-z0-9_]{1,100}")) {
        response.sendError(400); return;
    }
    String campo=error!=null?"error":"code", valor=error!=null?error:code;
    // Destino fijo del servidor: nunca se acepta una URL de retorno proporcionada por el visitante.
    response.setStatus(303);
    response.setHeader("Location",contexto+"/gub/callback?state="+URLEncoder.encode(state,StandardCharsets.UTF_8)
        +"&"+campo+"="+URLEncoder.encode(valor,StandardCharsets.UTF_8));
    return;
}
%>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>CargaUY</title></head>
<body><p><a href="<%=contexto%>/login">Ingresar a CargaUY</a></p></body></html>

