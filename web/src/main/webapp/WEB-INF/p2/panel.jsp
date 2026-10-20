<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="uy.edu.fing.grupo07.CargaUY.security.Acceso,uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario,java.util.*" %>
<%! private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
<% Acceso actor=(Acceso)request.getAttribute("actor");
String ruta=request.getServletPath();
List<String> acciones=ruta.equals("/app/admin") ? Arrays.asList(
"crearUsuario","listarUsuarios","consultarUsuario","modificarUsuario","bajaUsuario","reactivarUsuario",
"crearEmpresa","consultarEmpresa","listarEmpresas","modificarEmpresa","bajaEmpresa","reactivarEmpresa",
"asociar","desasociar","vincularIdentidad","roles","vinculo","listarVinculos","crearRubro","listarRubros","modificarRubro",
"eliminarRubro","crearTipoCarga","listarTiposCarga","modificarTipoCarga","eliminarTipoCarga","consultarRegla",
"crearRegla","editarRegla","consultarPdi") : ruta.equals("/app/perfil") ?
Arrays.asList("consultarUsuario","modificarUsuario") :
actor.tiene(TipoUsuario.FUNCIONARIO)?Arrays.asList("listarEmpresas","consultarEmpresa","crearEmpresa","modificarEmpresa","asociar","desasociar","vinculo","listarVinculos","consultarPdi"):
actor.tiene(TipoUsuario.CIUDADANO)?Arrays.asList("listarEmpresas","consultarEmpresa","modificarEmpresa","listarVinculos","consultarPdi"):
Arrays.asList("listarEmpresas","consultarEmpresa"); %>
<!doctype html>
<html lang="es">
	<head>
		<meta charset="UTF-8"><title>CargaUY · Gestión</title>
	</head>
	
	<body><p><a href="<%=request.getContextPath()%>/index.xhtml">Viajes, vehículos y fiscalización</a></p>
		<h1><%=ruta.equals("/app/admin")?"Administración":ruta.equals("/app/perfil")?"Mi perfil":"Empresas"%></h1>
		<p>Usuario: <%=h(actor.nombre())%> · ID: <%=actor.id()%> · CI: <%=actor.ci()%></p>
		
		<nav>
			<a href="<%=request.getContextPath()%>/app/perfil">Mi perfil</a>
			<a href="<%=request.getContextPath()%>/app/empresa">Empresas</a>
			<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %><a href="<%=request.getContextPath()%>/app/admin">Administración</a><% } %>
		</nav>
		
		<form method="post" action="<%=request.getContextPath()%>/logout">
			<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>"><button>Cerrar sesión</button>
		</form>
		
		<p><%=h(request.getAttribute("error"))%></p>
			<pre><%=h(request.getAttribute("resultado"))%></pre>
			
		<p>Elegí una operación y completá solo los campos que necesita.
		El resultado aparece en esta página. Para editar, consultá primero la versión actual.</p>
		
		<form method="post" action="<%= request.getContextPath() + request.getServletPath() %>">
			<input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
			<label>Operación 
				<select name="accion">
					<% for(String a:acciones){ %><option value="<%=h(a)%>"><%=h(a.replaceAll("([a-z])([A-Z])","$1 $2"))%></option><% } %>
				</select>
			</label>
			
			<label>Página (desde 0) <input name="pagina" type="number" min="0" value="0"></label>
			<label>Filas por página <input name="tam" type="number" min="1" max="100" value="20"></label>
			<label>Versión consultada del registro a editar <input name="version" type="number" min="0" value="0"></label>
			
			<% if(ruta.equals("/app/admin")) { %><fieldset>
				<legend>Identidad y permisos</legend>
				
			<label>Origen 
				<select name="origen">
					<option>LDAP</option>
					<option>GUB</option>
				</select>
			</label>
			
			<label>UID LDAP o subject GUB <input name="uid"></label>
			<label>Roles separados por coma <input name="roles" placeholder="CIUDADANO,CHOFER"></label>
			</fieldset><% } %>
			
			<fieldset>
				<legend>Usuario</legend>
				<label>ID <input name="id" type="number" value="<%=actor.id()%>"></label>
				<label>Tipo 
					<select name="tipo">
						<option value="">Todos (solo listado)</option>
						<option>CIUDADANO</option><option>CHOFER</option><option>FUNCIONARIO</option>
						<option>AUTORIDAD</option><option>ADMINISTRADOR</option>
					</select>
				</label>
				<label>Nombre <input name="nombre"></label>
				<label>Correo <input name="mail" type="email"></label>
				<label>Nacimiento <input name="fechaNac" type="date"></label>
				<label>CI sin puntos ni guiones <input name="ci" type="number"></label>
				<label>Contraseña, solo alta <input name="password" type="password" autocomplete="new-password"></label>
				<label>Número funcionario <input name="nroFuncionario" type="number"></label>
				<label>Departamento <input name="departamento"></label>
				<label>Cargo autoridad <input name="cargo"></label>
			</fieldset>
			
			<fieldset>
				<legend>Empresa y asociación</legend>
				<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)||actor.tiene(TipoUsuario.FUNCIONARIO)){ %>
				<label><input name="responsable" type="checkbox">Responsable de empresa</label>
				<label><input name="chofer" type="checkbox">Chofer de empresa</label>
				<% } %>
				
				<label>Número <input name="numero" type="number"></label>
				<label>Razón social <input name="razon"></label>
				<label>Nombre público <input name="nombrePublico"></label>
				<label>Dirección <input name="direccion"></label>
				<p>Asociar/desasociar: usá número de empresa e ID del ciudadano.</p>
			</fieldset>
			
			<% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %>
			<fieldset>
				<legend>Catálogos</legend>
				<label>Código <input name="codigo" type="number"></label>
				<label>Nombre rubro <input name="nombreRubro"></label>
				<label>Descripción <input name="descripcion"></label>
			</fieldset>
			
			<fieldset>
				<legend>Regla</legend>
				<label>Tolerancia kg <input name="cargaKg" type="number" min="0"></label>
				<label>Desvío km <input name="desvioKm" type="number" min="0" step="0.01"></label>
			</fieldset><% } %>
			
			<button type="submit">Ejecutar</button>
		</form>
	</body>
</html>