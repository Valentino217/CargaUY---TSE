<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="es">
<head><meta charset="UTF-8"><title>Pruebas locales P1</title></head>
<body>
<h1>Pruebas locales de perfiles y plataforma</h1>
<p>Elegí una operación y completá solo los campos que necesita.
La respuesta se muestra como texto. Volvé atrás para otra operación.</p>
<form method="post" action="<%= request.getContextPath() %>/p1/pruebas">
<input type="hidden" name="csrf" value="<%= request.getAttribute("csrf") %>">
<label>Operación <select name="accion">
<option value="crearUsuario">crearUsuario</option>
<option value="listarUsuarios">listarUsuarios</option>
<option value="consultarUsuario">consultarUsuario</option>
<option value="modificarUsuario">modificarUsuario</option>
<option value="bajaUsuario">bajaUsuario</option>
<option value="reactivarUsuario">reactivarUsuario</option>
<option value="crearEmpresa">crearEmpresa</option>
<option value="consultarEmpresa">consultarEmpresa</option>
<option value="listarEmpresas">listarEmpresas</option>
<option value="modificarEmpresa">modificarEmpresa</option>
<option value="bajaEmpresa">bajaEmpresa</option>
<option value="reactivarEmpresa">reactivarEmpresa</option>
<option value="asociar">asociar</option>
<option value="desasociar">desasociar</option>
<option value="crearRubro">crearRubro</option>
<option value="listarRubros">listarRubros</option>
<option value="modificarRubro">modificarRubro</option>
<option value="eliminarRubro">eliminarRubro</option>
<option value="crearTipoCarga">crearTipoCarga</option>
<option value="listarTiposCarga">listarTiposCarga</option>
<option value="modificarTipoCarga">modificarTipoCarga</option>
<option value="eliminarTipoCarga">eliminarTipoCarga</option>
<option value="consultarRegla">consultarRegla</option>
<option value="crearRegla">crearRegla</option>
<option value="editarRegla">editarRegla</option>
<option value="consultarPdi">consultarPdi</option>
</select></label>
<fieldset><legend>Usuario</legend>
<label>ID <input name="id" type="number"></label>
<label>Tipo <select name="tipo">
<option value="">Todos (solo listado)</option>
<option>CIUDADANO</option><option>CHOFER</option><option>FUNCIONARIO</option>
<option>AUTORIDAD</option><option>ADMINISTRADOR</option>
</select></label>
<label>Nombre <input name="nombre"></label>
<label>Correo <input name="mail" type="email"></label>
<label>Nacimiento <input name="fechaNac" type="date"></label>
<label>CI sin puntos ni guiones <input name="ci" type="number"></label>
<label>Contraseña, solo alta <input name="password" type="password" autocomplete="new-password"></label>
<label>Número funcionario <input name="nroFuncionario" type="number"></label>
<label>Departamento <input name="departamento"></label>
<label>Cargo autoridad <input name="cargo"></label>
</fieldset>
<fieldset><legend>Empresa y asociación</legend>
<label>Número <input name="numero" type="number"></label>
<label>Razón social <input name="razon"></label>
<label>Nombre público <input name="nombrePublico"></label>
<label>Dirección <input name="direccion"></label>
<p>Asociar/desasociar: usá número de empresa e ID del ciudadano.</p>
</fieldset>
<fieldset><legend>Catálogos</legend>
<label>Código <input name="codigo" type="number"></label>
<label>Nombre rubro <input name="nombreRubro"></label>
<label>Descripción <input name="descripcion"></label>
</fieldset>
<fieldset><legend>Regla</legend>
<label>Tolerancia kg <input name="cargaKg" type="number" min="0"></label>
<label>Desvío km <input name="desvioKm" type="number" min="0" step="0.01"></label>
<label>Versión consultada (editar) <input name="version" type="number" min="0"></label>
</fieldset>
<button type="submit">Ejecutar</button>
</form>
</body></html>