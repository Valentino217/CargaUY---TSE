# Gestión de Empresas: una sección por operación

La pantalla `/app/empresa` deja de usar el formulario genérico de administración. Cada opción del menú abre su propio formulario y muestra únicamente sus campos. Se conserva el estilo del resto del proyecto.

## Cómo verla

1. Refrescar los proyectos en Eclipse con F5.
2. Compilar con Maven, Goals: `verify`.
3. Reiniciar WildFly y hacer Publish del EAR: cambió el servlet que atiende `/app/empresa`.
4. Ingresar a CargaUY y abrir Empresas desde Inicio. La dirección sigue siendo `http://localhost:8080/CargaUY-web/app/empresa`.

No hay nuevas migraciones, usuarios ni contraseñas para este cambio. Se necesita la configuración anterior y un usuario autenticado.

## Opciones del menú

| Opción | Qué se completa | Resultado |
|---|---|---|
| Listado de empresas | Página y cantidad por página | Tabla con las empresas permitidas y enlace Ver empresa. |
| Registrar empresa | Número, razón social, nombre público y dirección | Crea la empresa y confirma el resultado. |
| Registrar usuario | Nombre, correo, nacimiento, CI y uno o varios roles | Crea el perfil con los roles seleccionados y muestra su ID. |
| Consultar empresa | Número de empresa | Muestra sus datos y enlaces para editar o consultar vínculos. |
| Editar empresa | Buscar por número; luego modificar sus datos | Guarda con control de versión automático en el formulario. El número no se modifica. |
| Vincular ciudadano | Buscar empresa y escribir el ID del usuario | Agrega o retira la función de responsable ciudadano. |
| Vincular chofer | Buscar empresa y escribir el ID del usuario | Agrega o retira la función de chofer. |
| Consultar vínculos | Número de empresa | Tabla con ID, nombre y funciones de cada persona; incluye vínculos revocados. |
| Activar o dar de baja | Buscar empresa | Cambia su estado sin borrar sus registros. |
| Consultar ciudadano | CI sin puntos ni guiones | Consulta PDI; no crea un usuario ni una asociación. |

Registrar empresas o ciudadanos, vincular/desvincular y cambiar estado están disponibles para administradores y funcionarios. El responsable ciudadano puede consultar y editar sus empresas. El servidor comprueba los permisos sobre cada empresa además de controlar qué opciones muestra el menú.

### Alta con múltiples roles

En Registrar usuario se completan los datos personales y se marcan una o varias casillas de roles. No se asigna CIUDADANO por defecto; debe seleccionarse al menos un rol.

- Un administrador puede asignar CIUDADANO, CHOFER, FUNCIONARIO, AUTORIDAD y ADMINISTRADOR.
- Un funcionario puede dar de alta perfiles con CIUDADANO, CHOFER o ambos; no puede crear otros operadores o administradores.
- Se admiten combinaciones como CIUDADANO + CHOFER o CIUDADANO + FUNCIONARIO.
- No se mezclan roles gub.uy (CIUDADANO, CHOFER, FUNCIONARIO) con roles LDAP (ADMINISTRADOR, AUTORIDAD).
- FUNCIONARIO requiere número y departamento; AUTORIDAD requiere cargo.

El servidor valida estas reglas, aunque alguien modifique el formulario. La pantalla utiliza GestionPerfilesService.registrarConRoles. El método registrarCiudadano anterior queda como compatibilidad para llamadas que requieran exclusivamente CIUDADANO.

El alta muestra el ID del usuario y no crea su cuenta en LDAP/gub.uy ni una contraseña local. La identidad del proveedor y los vínculos de empresa se gestionan por separado.

## Ejemplo: empresa con responsable y chofer

1. Entrar como administrador o funcionario.
2. Registrar la empresa 101, completando sus datos.
3. En administración de usuarios, identificar al ciudadano responsable y anotar su **ID de usuario**. La persona debe tener rol CIUDADANO.
4. Abrir Vincular ciudadano, buscar la empresa 101 e ingresar ese ID. Presionar Vincular ciudadano.
5. Identificar a la persona que conducirá y comprobar que tenga rol CHOFER.
6. Abrir Vincular chofer, buscar la empresa 101, ingresar su ID y presionar Vincular chofer.
7. Abrir Consultar vínculos y buscar la empresa 101. La tabla muestra qué funciones tiene cada persona.

El ID no es la CI. Consultar una persona en PDI tampoco la registra en CargaUY. El usuario debe existir antes de vincularlo y sus roles se administran por separado.

Una misma persona puede ser responsable y chofer. Vincular o desvincular una función **conserva la otra**. Si ambas quedan desactivadas, el vínculo permanece revocado para conservar su trazabilidad.

## Qué archivos cambiaron y por qué

- `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/EmpresasServlet.java`: controla las nuevas secciones, valida los campos, llama a los servicios y prepara los datos de la pantalla. Una sección no acepta acciones de otra. Después de guardar redirige a una consulta GET para que actualizar la página no repita el POST.
- `web/src/main/webapp/WEB-INF/p2/empresas.jsp`: menú, formularios independientes, tabla de empresas, ficha de empresa y tabla de vínculos. Los formularios conservan el token CSRF y escapan los valores mostrados.
- `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/PanelServlet.java`: conserva administración y perfil; deja de atender la ruta de empresas para evitar dos servlets con la misma dirección.
- `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/IdentidadesService.java`: agrega `funcionEmpresa`, que actualiza una sola función dentro de una transacción y conserva la otra. Se mantiene el método anterior para compatibilidad con administración.
- `web/src/main/webapp/css/plataforma.css`: estilos de menú de operaciones, tablas, ficha de empresa y mensajes de confirmación.

Separar los formularios permite que cada botón envíe solo los datos de su operación. La JSP presenta la información; el servlet coordina las solicitudes y los servicios siguen aplicando permisos y reglas de negocio. Ocultar un botón no reemplaza esas comprobaciones.

## Comprobaciones

Se agregaron pruebas para conservar funciones al vincular/desvincular, rechazar un chofer sin el rol necesario, impedir un alta desde el rol ciudadano, rechazar acciones de otra sección y comprobar la redirección después de registrar. Las plantillas JSP se validaron compilando su contenido Java.
