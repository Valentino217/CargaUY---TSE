<%@ page contentType="text/html;charset=UTF-8" %>
<%! private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>Ingresar a CargaUY</title></head>
<body><h1>Ingresar a CargaUY</h1>
<p><%=h(request.getAttribute("error"))%></p>
<h2>Administradores y autoridades</h2>
<form method="post" action="<%=request.getContextPath()%>/login">
<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
<label>Usuario del directorio <input name="uid" required autocomplete="username"></label>
<label>Contraseña <input type="password" name="password" required autocomplete="current-password"></label>
<button>Ingresar</button></form>
<h2>Ciudadanos, choferes y funcionarios</h2>
<a href="<%=request.getContextPath()%>/gub/iniciar">Ingresar con Usuario gub.uy</a>
</body></html>
