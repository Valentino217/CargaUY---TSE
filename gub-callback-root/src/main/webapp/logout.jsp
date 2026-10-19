<%@ page contentType="text/html;charset=UTF-8" session="false" %>
<%
response.setHeader("Cache-Control","no-store");
response.setHeader("Referrer-Policy","no-referrer");
%>
<!doctype html><html lang="es"><head><meta charset="UTF-8"><title>Retorno de identidad</title>
</head>
	<body>
		<p>Retorno del proveedor de identidad.</p><p>Para cerrar tu sesión en CargaUY, usá su botón Salir.</p>
		<a href="<%=application.getInitParameter("cargauy.context")%>/login">Volver a CargaUY</a>
	</body>
</html>