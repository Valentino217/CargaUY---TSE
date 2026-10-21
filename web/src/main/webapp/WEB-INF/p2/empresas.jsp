<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.*,jakarta.servlet.http.HttpServletRequest,uy.edu.fing.grupo07.CargaUY.security.Acceso,uy.edu.fing.grupo07.CargaUY.dto.TipoUsuario,uy.edu.fing.grupo07.CargaUY.dto.EmpresaResumen,uy.edu.fing.grupo07.CargaUY.security.IdentidadesService.VinculoVista,uy.edu.fing.grupo07.CargaUY.integration.ResultadoPdi" %>
<%!
private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
private String valor(HttpServletRequest r,String campo,Object defecto){String v=r.getParameter(campo);return h(v==null?defecto:v);}
private String ocultos(HttpServletRequest r,String seccion,String accion){
    return "<input type=\"hidden\" name=\"csrf\" value=\""+h(r.getAttribute("csrf"))+"\">"
        +"<input type=\"hidden\" name=\"seccion\" value=\""+h(seccion)+"\">"
        +(accion.isEmpty()?"":"<input type=\"hidden\" name=\"accion\" value=\""+h(accion)+"\">");
}
%>
<%
Acceso actor=(Acceso)request.getAttribute("actor");
String seccion=(String)request.getAttribute("seccion"),base=request.getContextPath()+"/app/empresa";
boolean operador=actor.tiene(TipoUsuario.ADMINISTRADOR)||actor.tiene(TipoUsuario.FUNCIONARIO);
boolean editar=operador||actor.tiene(TipoUsuario.CIUDADANO);
Map<String,String> titulos=Map.of("listar","Listado de empresas","registrar","Registrar empresa","consultar","Consultar empresa",
    "editar","Editar empresa","ciudadanos","Vincular ciudadano","choferes","Vincular chofer","vinculos","Consultar vínculos",
    "estado","Activar o dar de baja","pdi","Consultar ciudadano","altaCiudadano","Registrar usuario");
EmpresaResumen empresa=(EmpresaResumen)request.getAttribute("empresa");
List<EmpresaResumen> empresas=(List<EmpresaResumen>)request.getAttribute("empresas");
List<VinculoVista> vinculos=(List<VinculoVista>)request.getAttribute("vinculos");
%>
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>CargaUY - <%=h(titulos.get(seccion))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/plataforma.css">
</head>
<body><main class="container">
<header class="header-bar">
    <div><h1>Gestión de Empresas</h1><p class="subtitle">Empresas transportistas, responsables y choferes</p></div>
    <nav class="nav-links" aria-label="Navegación principal">
        <a href="<%=request.getContextPath()%>/index.xhtml">Inicio</a>
        <a href="<%=request.getContextPath()%>/app/perfil">Mi perfil</a>
        <% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %><a href="<%=request.getContextPath()%>/app/admin">Administración</a><% } %>
        <form method="post" action="<%=request.getContextPath()%>/logout">
            <input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>"><button class="button-secondary">Cerrar sesión</button>
        </form>
    </nav>
</header>
<p class="subtitle">Sesión de <strong><%=h(actor.nombre())%></strong></p>
<nav class="operation-tabs" aria-label="Operaciones de empresas">
<% for(String item:List.of("listar","registrar","consultar","editar","altaCiudadano","ciudadanos","choferes","vinculos","estado","pdi")){
    if(Set.of("registrar","altaCiudadano","ciudadanos","choferes","estado").contains(item)&&!operador||item.equals("editar")&&!editar)continue; %>
    <a href="<%=base%>?seccion=<%=item%>"<%=item.equals(seccion)?" aria-current=\"page\"":""%>><%=h(titulos.get(item))%></a>
<% } %>
</nav>
<% if(request.getAttribute("error")!=null){ %><div class="alert" role="alert"><%=h(request.getAttribute("error"))%></div><% } %>
<% if(request.getAttribute("mensaje")!=null){ %><div class="alert alert-success" role="status"><%=h(request.getAttribute("mensaje"))%></div><% } %>

<% if(seccion.equals("altaCiudadano")){ %>
<section class="card">
    <h2>Registrar usuario</h2>
    <form method="post" action="<%=base%>">
        <%=ocultos(request,seccion,"registrarCiudadano")%>
        <div class="form-grid">
            <label>Nombre completo<input name="nombre" maxlength="100" required autocomplete="name" value="<%=valor(request,"nombre","")%>"></label>
            <label>Correo electrónico<input name="mail" type="email" maxlength="150" required autocomplete="email" value="<%=valor(request,"mail","")%>"></label>
            <label>Fecha de nacimiento<input name="fechaNac" type="date" max="<%=java.time.LocalDate.now()%>" required value="<%=valor(request,"fechaNac","")%>"></label>
            <label>CI sin puntos ni guiones<input name="ci" type="number" min="1" required value="<%=valor(request,"ci","")%>"></label>
        </div>
        <fieldset>
            <legend>Roles del usuario: seleccioná uno o varios</legend>
            <% Set<String> seleccionados=request.getParameterValues("roles")==null?Set.of():new HashSet<>(Arrays.asList(request.getParameterValues("roles"))); %>
            <% for(String rol:List.of("CIUDADANO","CHOFER")){ %>
            <label><input type="checkbox" name="roles" value="<%=rol%>"<%=seleccionados.contains(rol)?" checked":""%>> <%=rol.equals("CIUDADANO")?"Ciudadano / responsable de empresa":"Chofer"%></label>
            <% } %>
            <% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){for(String rol:List.of("FUNCIONARIO","AUTORIDAD","ADMINISTRADOR")){ %>
            <label><input type="checkbox" name="roles" value="<%=rol%>"<%=seleccionados.contains(rol)?" checked":""%>> <%=h(rol)%></label>
            <% }} %>
        </fieldset>
        <% if(actor.tiene(TipoUsuario.ADMINISTRADOR)){ %>
        <fieldset>
            <legend>Datos adicionales según el rol</legend>
            <label>Número de funcionario<input name="nroFuncionario" type="number" min="1" value="<%=valor(request,"nroFuncionario","")%>"></label>
            <label>Departamento del funcionario<input name="departamento" maxlength="100" value="<%=valor(request,"departamento","")%>"></label>
            <label>Cargo de autoridad<input name="cargo" maxlength="100" value="<%=valor(request,"cargo","")%>"></label>
            <p class="help">Funcionario requiere número y departamento. Autoridad requiere cargo.</p>
        </fieldset>
        <% } %>
        <button>Registrar usuario</button>
    </form>
</section>
<% } %>

<% if(seccion.equals("listar")){ %>
<section class="card">
    <h2>Listado de empresas</h2>
    <form method="get" action="<%=base%>" class="form-grid compact-form">
        <input type="hidden" name="seccion" value="listar">
        <label>Página (desde 0)<input name="pagina" type="number" min="0" value="<%=valor(request,"pagina",0)%>"></label>
        <label>Empresas por página<input name="tam" type="number" min="1" max="100" value="<%=valor(request,"tam",20)%>"></label>
        <div class="field-action"><button>Consultar listado</button></div>
    </form>
    <div class="table-scroll"><table><thead><tr><th>Número</th><th>Razón social</th><th>Nombre público</th><th>Estado</th><th>Consulta</th></tr></thead><tbody>
    <% if(empresas!=null)for(EmpresaResumen e:empresas){ %>
    <tr><td><%=e.numero()%></td><td><%=h(e.razonSocial())%></td><td><%=h(e.nombrePublico())%></td>
        <td><span class="status-badge <%=e.activa()?"is-active":"is-inactive"%>"><%=e.activa()?"Activa":"Inactiva"%></span></td>
        <td><a href="<%=base%>?seccion=consultar&amp;numero=<%=e.numero()%>">Ver empresa</a></td></tr>
    <% } %>
    <% if(empresas==null||empresas.isEmpty()){ %><tr><td colspan="5" class="empty-state">No hay empresas para mostrar en esta página.</td></tr><% } %>
    </tbody></table></div>
</section>
<% } %>

<% if(Set.of("consultar","editar","ciudadanos","choferes","vinculos","estado").contains(seccion)){ %>
<section class="card">
    <h2><%=h(titulos.get(seccion))%></h2>
    <form method="get" action="<%=base%>" class="form-grid compact-form">
        <input type="hidden" name="seccion" value="<%=h(seccion)%>">
        <label>Número de empresa<input name="numero" type="number" min="1" required value="<%=valor(request,"numero","")%>"></label>
        <div class="field-action"><button>Buscar empresa</button></div>
    </form>
</section>
<% } %>

<% if(empresa!=null){ %>
<section class="card">
    <h2><%=h(empresa.nombrePublico())%></h2>
    <dl class="company-details">
        <div><dt>Número</dt><dd><%=empresa.numero()%></dd></div>
        <div><dt>Razón social</dt><dd><%=h(empresa.razonSocial())%></dd></div>
        <div><dt>Dirección</dt><dd><%=h(empresa.direccion())%></dd></div>
        <div><dt>Estado</dt><dd><%=empresa.activa()?"Activa":"Inactiva"%></dd></div>
    </dl>
    <% if(seccion.equals("consultar")){ %>
    <div class="nav-links">
        <a href="<%=base%>?seccion=vinculos&amp;numero=<%=empresa.numero()%>">Ver responsables y choferes</a>
        <% if(editar){ %><a href="<%=base%>?seccion=editar&amp;numero=<%=empresa.numero()%>">Editar datos</a><% } %>
    </div><% } %>
</section>
<% } %>

<% if(seccion.equals("registrar")||seccion.equals("editar")&&empresa!=null){ %>
<section class="card">
    <h2><%=seccion.equals("registrar")?"Registrar una nueva empresa":"Editar datos de la empresa"%></h2>
    <form method="post" action="<%=base%>">
        <%=ocultos(request,seccion,seccion.equals("registrar")?"crearEmpresa":"modificarEmpresa")%>
        <% if(empresa!=null){ %>
        <input type="hidden" name="numero" value="<%=empresa.numero()%>">
        <input type="hidden" name="version" value="<%=valor(request,"version",empresa.version())%>">
        <% } %>
        <div class="form-grid">
        <% if(seccion.equals("registrar")){ %><label>Número de empresa<input name="numero" type="number" min="1" required value="<%=valor(request,"numero","")%>"></label><% } %>
        <label>Razón social<input name="razon" maxlength="150" required value="<%=valor(request,"razon",empresa==null?"":empresa.razonSocial())%>"></label>
        <label>Nombre público<input name="nombrePublico" maxlength="150" required value="<%=valor(request,"nombrePublico",empresa==null?"":empresa.nombrePublico())%>"></label>
        <label>Dirección<input name="direccion" maxlength="255" required value="<%=valor(request,"direccion",empresa==null?"":empresa.direccion())%>"></label>
        </div>
        <button><%=seccion.equals("registrar")?"Registrar empresa":"Guardar cambios"%></button>
    </form>
</section>
<% } %>

<% if((seccion.equals("ciudadanos")||seccion.equals("choferes"))&&empresa!=null){boolean ciudadano=seccion.equals("ciudadanos"); %>
<section class="card">
    <h2><%=ciudadano?"Gestionar responsable ciudadano":"Gestionar chofer"%></h2>
    <p><%=ciudadano?"El usuario debe tener rol CIUDADANO. Al vincularlo podrá actuar como responsable de esta empresa.":"El usuario debe tener rol CHOFER. El vínculo lo habilita para ser asignado a guías de esta empresa."%></p>
    <form method="post" action="<%=base%>">
        <%=ocultos(request,seccion,"")%><input type="hidden" name="numero" value="<%=empresa.numero()%>">
        <div class="form-grid"><label>ID del usuario (no su CI)<input name="id" type="number" min="1" required value="<%=valor(request,"id","")%>"></label></div>
        <div class="operation-buttons">
            <button name="accion" value="<%=ciudadano?"vincularCiudadano":"vincularChofer"%>"><%=ciudadano?"Vincular ciudadano":"Vincular chofer"%></button>
            <button class="button-secondary" name="accion" value="<%=ciudadano?"desvincularCiudadano":"desvincularChofer"%>">Desvincular esta función</button>
        </div>
    </form>
</section>
<% } %>

<% if(vinculos!=null){ %>
<section class="card">
    <h2>Responsables y choferes de la empresa</h2>
    <div class="table-scroll"><table><thead><tr><th>ID de usuario</th><th>Nombre</th><th>Responsable</th><th>Chofer</th><th>Estado del vínculo</th></tr></thead><tbody>
    <% for(VinculoVista v:vinculos){ %>
    <tr><td><%=v.usuario()%></td><td><%=h(v.nombre())%></td><td><%=v.responsable()?"Sí":"No"%></td><td><%=v.chofer()?"Sí":"No"%></td><td><%=v.responsable()||v.chofer()?"Con funciones":"Revocado"%></td></tr>
    <% } %>
    <% if(vinculos.isEmpty()){ %><tr><td colspan="5" class="empty-state">Esta empresa no tiene vínculos registrados.</td></tr><% } %>
    </tbody></table></div>
    <% if(vinculos.size()==100){ %><p class="help">Se muestran hasta 100 vínculos.</p><% } %>
</section>
<% } %>

<% if(seccion.equals("estado")&&empresa!=null){ %>
<section class="card"><h2>Estado de la empresa</h2>
    <p>La baja es lógica: conserva los datos y el historial.</p>
    <form method="post" action="<%=base%>">
        <%=ocultos(request,seccion,empresa.activa()?"desactivarEmpresa":"activarEmpresa")%>
        <input type="hidden" name="numero" value="<%=empresa.numero()%>">
        <button><%=empresa.activa()?"Dar de baja la empresa":"Reactivar empresa"%></button>
    </form>
</section>
<% } %>

<% if(seccion.equals("pdi")){ %>
<section class="card"><h2>Consultar datos de un ciudadano</h2>
    <form method="post" action="<%=base%>" class="form-grid compact-form">
        <%=ocultos(request,seccion,"consultarPdi")%>
        <label>CI sin puntos ni guiones<input name="ci" type="number" min="1" required value="<%=valor(request,"ci","")%>"></label>
        <div class="field-action"><button>Consultar ciudadano</button></div>
    </form>
    <% ResultadoPdi pdi=(ResultadoPdi)request.getAttribute("ciudadanoPdi");if(pdi!=null){ %>
    <div class="result card"><h2><%=h(pdi.estado())%></h2><p><%=h(pdi.mensaje())%></p>
    <% if(pdi.ci()!=null){ %><p>CI: <%=pdi.ci()%></p><p>Nombre: <%=h(pdi.nombre())%> <%=h(pdi.apellido())%></p><% } %>
    </div><% } %>
</section>
<% } %>
</main></body></html>

