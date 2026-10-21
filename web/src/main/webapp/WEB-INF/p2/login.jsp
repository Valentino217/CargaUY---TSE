<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%! private String h(Object o){return o==null?"":o.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
<!doctype html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CargaUY - Ingresar</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/plataforma.css">
</head>
<body>
<main class="login-container">
    <header class="header-bar">
        <div>
            <p class="eyebrow">Plataforma de gestión y fiscalización</p>
            <h1>Ingresar a CargaUY</h1>
        </div>
    </header>
    <% if(request.getAttribute("error")!=null){ %>
    <div class="alert" role="alert"><%=h(request.getAttribute("error"))%></div>
    <% } %>
    <div class="login-grid">
        <section class="card" aria-labelledby="ingreso-interno">
            <h2 id="ingreso-interno">Administradores y autoridades</h2>
            <form class="login-form" method="post" action="<%=request.getContextPath()%>/login">
                <input type="hidden" name="csrf" value="<%=h(request.getAttribute("csrf"))%>">
                <label for="uid">Usuario del directorio
                    <input id="uid" name="uid" required autocomplete="username" placeholder="Ingresá tu usuario">
                </label>
                <label for="password">Contraseña
                    <input id="password" name="password" type="password" required autocomplete="current-password" placeholder="Ingresá tu contraseña">
                </label>
                <button type="submit" class="button-full">Ingresar</button>
            </form>
        </section>
        <section class="card external-login" aria-labelledby="ingreso-ciudadano">
            <h2 id="ingreso-ciudadano">Ciudadanos, choferes y funcionarios</h2>
            <p>El ingreso se realiza a través de Usuario gub.uy. Al finalizar, volverás a esta plataforma.</p>
            <a class="button button-secondary button-full" href="<%=request.getContextPath()%>/gub/iniciar">Ingresar con Usuario gub.uy</a>
        </section>
    </div>
    <p class="login-footer">CargaUY · Gestión de transporte de carga</p>
</main>
</body>
</html>
