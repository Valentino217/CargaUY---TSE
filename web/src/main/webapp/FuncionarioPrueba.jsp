<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Alta de Funcionario</title>
</head>
<body>
    <form action="FuncionarioServlet" method="post">

    <table>
        <tr>
            <td><label for="nombre">Nombre:</label></td>
            <td><input type="text" id="nombre" name="nombre"></td>
        </tr>

        <tr>
            <td><label for="mail">Mail:</label></td>
            <td><input type="email" id="mail" name="mail"></td>
        </tr>

        <tr>
            <td><label for="fechaNac">Fecha de nacimiento:</label></td>
            <td><input type="date" id="fechaNac" name="fechaNac"></td>
        </tr>

        <tr>
            <td><label for="ci">CI:</label></td>
            <td><input type="number" id="ci" name="ci"></td>
        </tr>

        <tr>
            <td><label for="contraseña">Contraseña:</label></td>
            <td><input type="password" id="password" name="password"></td>
        </tr>

        <tr>
            <td><label for="nroFuncionario">Número de funcionario:</label></td>
            <td><input type="number" id="nroFuncionario" name="nroFuncionario"></td>
        </tr>

        <tr>
            <td><label for="departamento">Departamento:</label></td>
            <td><input type="text" id="departamento" name="departamento"></td>
        </tr>

        <tr>
            <td></td>
            <td>
                <button type="submit">Guardar funcionario</button>
            </td>
        </tr>
    </table>
</form>
</body>
</html>