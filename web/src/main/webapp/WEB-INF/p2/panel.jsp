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
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CargaUY - <%=ruta.equals("/app/admin")?"Administración":ruta.equals("/app/perfil")?"Mi perfil":"Empresas"%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/plataforma.css">
</head>
<body>
<main class="container">
    <header class="header-bar">
        <div>
            <h1><%=ruta.equals("/app/admin")?"Administración":ruta.equals("/app/perfil")?"Mi perfil":"Gestión de Empresas"%></h1>
            <p class="subtitle"><%=ruta.equals("/app/empresa")?"Administración de empresas, responsables y choferes":ruta.equals("/app/perfil")?"Consulta y actualización de tus datos personales":"Usuarios, permisos y configuración de la plataforma"%></p>
        </div>
        <nav class="nav-links" aria-label="Navegación principal">
            <a href="<%=request.getContextPath()%>/index.xhtml">Inicio</a>
            <a href="<%=request.getContextPath()%>/app/perfil"<%=ruta.equals("/app/perfil")?" aria-current=\"page\"":""%>>Mi perfil</a>
            <a href="<%=request.getContextPath()%>/app/empresa"<%=ruta.equals("/app/empresa")?" aria-current=\"page\"":""%>>Empresas</a>
            <% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %>
            <a href="<%=request.getContextPath()%>/app/admin"<%=ruta.equals("/app/admin")?" aria-current=\"page\"":""%>>Administración</a>
            <% } %>
            <form method="post" action="<%=request.getContextPath()%>/logout">
                <input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
                <button type="submit" class="button-secondary">Cerrar sesión</button>
            </form>
        </nav>
    </header>
    <p class="subtitle">Sesión de <strong><%=h(actor.nombre())%></strong> · CI: <%=actor.ci()%> · ID: <%=actor.id()%></p>
    <% if(request.getAttribute("error")!=null){ %>
    <div class="alert" role="alert"><%=h(request.getAttribute("error"))%></div>
    <% } %>
    <% if(request.getAttribute("resultado")!=null){ %>
    <section class="card result" aria-label="Resultado de la operación" role="status">
        <h2>Resultado</h2>
        <pre><%=h(request.getAttribute("resultado"))%></pre>
    </section>
    <% } %>

    <form method="post" action="<%=request.getContextPath()+request.getServletPath()%>">
        <input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
        <section class="card">
            <h2>Operación</h2>
            <p class="subtitle">Elegí qué necesitás hacer y completá los datos correspondientes. Para editar, consultá primero el registro y su versión.</p>
            <div class="form-grid">
                <label>Operación
                    <select name="accion">
                        <% for(String a:acciones){ %>
                        <option value="<%=h(a)%>"<%=a.equals(request.getParameter("accion"))?" selected":""%>><%=h(a.replaceAll("([a-z])([A-Z])","$1 $2"))%></option>
                        <% } %>
                    </select>
                </label>
                <label>Página (desde 0)<input name="pagina" type="number" min="0" value="0"></label>
                <label>Filas por página<input name="tam" type="number" min="1" max="100" value="20"></label>
                <label>Versión del registro a editar<input name="version" type="number" min="0" value="0"></label>
            </div>
        </section>

        <% if(ruta.equals("/app/admin")){ %>
        <fieldset>
            <legend>Identidad y permisos</legend>
            <label>Origen<select name="origen"><option>LDAP</option><option>GUB</option></select></label>
            <label>UID LDAP o referencia GUB<input name="uid"></label>
            <label>Roles separados por coma<input name="roles" placeholder="CIUDADANO,CHOFER"></label>
        </fieldset>
        <% } %>

        <% if(!ruta.equals("/app/empresa")){ %>
        <fieldset>
            <legend>Datos del usuario</legend>
            <label>ID<input name="id" type="number" value="<%=actor.id()%>"></label>
            <label>Tipo<select name="tipo">
                <option value="">Todos (solo listado)</option>
                <option>CIUDADANO</option><option>CHOFER</option><option>FUNCIONARIO</option>
                <option>AUTORIDAD</option><option>ADMINISTRADOR</option>
            </select></label>
            <label>Nombre<input name="nombre" autocomplete="name"></label>
            <label>Correo<input name="mail" type="email" autocomplete="email"></label>
            <label>Fecha de nacimiento<input name="fechaNac" type="date"></label>
            <label>CI sin puntos ni guiones<input name="ci" type="number"></label>
            <label>Contraseña, solo para el alta<input name="password" type="password" autocomplete="new-password"></label>
            <label>Número de funcionario<input name="nroFuncionario" type="number"></label>
            <label>Departamento<input name="departamento"></label>
            <label>Cargo de autoridad<input name="cargo"></label>
        </fieldset>
        <% } %>

        <% if(!ruta.equals("/app/perfil")){ %>
        <fieldset>
            <legend>Datos de la empresa</legend>
            <label>Número de empresa<input name="numero" type="number"></label>
            <label>Razón social<input name="razon"></label>
            <label>Nombre público<input name="nombrePublico"></label>
            <label>Dirección<input name="direccion"></label>
        </fieldset>
        <fieldset>
            <legend>Asociaciones y consulta de ciudadano</legend>
            <% if(ruta.equals("/app/empresa")){ %>
            <label>ID del usuario<input name="id" type="number" value="<%=actor.id()%>"></label>
            <label>CI para consultar ciudadano<input name="ci" type="number"></label>
            <% } %>
            <% if(actor.tiene(TipoUsuario.ADMINISTRADOR)||actor.tiene(TipoUsuario.FUNCIONARIO)){ %>
            <label><input name="responsable" type="checkbox"> Responsable de empresa</label>
            <label><input name="chofer" type="checkbox"> Chofer de empresa</label>
            <% } %>
            <p class="help">Para asociar o desvincular una persona, indicá el número de empresa y su ID de usuario.</p>
        </fieldset>
        <% } %>

        <% if(ruta.equals("/app/admin")){ %>
        <fieldset>
            <legend>Catálogos</legend>
            <label>Código<input name="codigo" type="number"></label>
            <label>Nombre del rubro<input name="nombreRubro"></label>
            <label>Descripción<input name="descripcion"></label>
        </fieldset>
        <fieldset>
            <legend>Reglas de fiscalización</legend>
            <label>Tolerancia de carga (kg)<input name="cargaKg" type="number" min="0"></label>
            <label>Tolerancia de desvío (km)<input name="desvioKm" type="number" min="0" step="0.01"></label>
        </fieldset>
        <% } %>
        <div class="form-actions"><button type="submit">Ejecutar operación</button></div>
    </form>
</main>
</body>
</html>
