# Pantallas Facelets

Las vistas activas de CargaUY usan JSF/Facelets. No queda ningún JSP en los módulos desplegables. Los archivos de `docs/P1/ejemplos` y `docs/P2/ejemplos` son ejemplos históricos: no se compilan ni publican.

## Archivos que se editan

| Pantalla | Archivo bajo `web/src/main/webapp/` |
|---|---|
| Ingreso LDAP y enlace a Usuario gub.uy | `WEB-INF/p2/login.xhtml` |
| Empresas, registro de usuarios con múltiples roles y vínculos | `WEB-INF/p2/empresas.xhtml` |
| Administración y perfil | `WEB-INF/p2/panel.xhtml` |
| Consulta antigua de tracking por servlet | `WEB-INF/viaje-consulta.xhtml` |
| Inicio y demás módulos JSF | Los `.xhtml` existentes en la raíz |
| Estilo de plataforma | `css/plataforma.css` |

`PlataformaVista.java` es un bean CDI `@Named`, de alcance de petición, con los datos de presentación: secciones visibles, roles disponibles, fecha máxima y valores de formularios. Las expresiones `#{...}`, `h:outputText`, `h:dataTable` y etiquetas Facelets sustituyen los scriptlets Java. Los datos mostrados se escapan como texto.

## Formularios y controladores

La migración cambia la tecnología de las vistas. Los formularios HTML conservan sus nombres de parámetros y envían a los servlets existentes. **No se han convertido en formularios `h:form` con acciones de backing beans**. Facelets es quien renderiza las páginas mediante `FacesServlet`; los servlets de entrada continúan coordinando los servicios, las respuestas de error y las redirecciones después de guardar. Esto conserva los controles de sesión, autorización y CSRF que ya utiliza el proyecto.

No basta con renombrar un archivo: también se retiraron los scriptlets, se introdujo el bean de presentación y se actualizaron los `forward`. No agregar Java `<% ... %>` en los XHTML.

Las rutas de uso siguen siendo `/login`, `/app/empresa`, `/app/perfil` y `/app/admin`. Las vistas privadas en `WEB-INF` no se abren directamente en el navegador.

Se retiraron las vistas de pruebas deshabilitadas y el `index.jsp` que únicamente redirigía a `index.xhtml`.

## Retorno de gub.uy

El módulo independiente `gub-callback-root` no presenta formularios: su servlet recibe las URI raíz que ya están registradas en ID Uruguay, valida los parámetros y redirige al callback de CargaUY. Sustituye los dos JSP técnicos anteriores sin introducir un segundo motor de vistas.

Ahora Maven lo compila junto con los demás módulos, pero **continúa desplegándose como WAR separado**, fuera del EAR. El resultado es `gub-callback-root/target/gub-callback-root.war`. `scripts/p1/empaquetar-callback.ps1` también compila ese módulo antes de copiar el WAR a su ubicación anterior.

Esta migración no resuelve el `OIDC_ERROR` que devuelve el proveedor al autorizar el cliente.

## Actualizar Eclipse y publicar

1. Refrescar los proyectos con F5. Si Eclipse avisa que un archivo cambió en disco, cargar la versión del disco.
2. Maven → Update Project. Si se importa todo el repositorio, aparecerá también `gub-callback-root`, que tiene su propio `pom.xml`.
3. Ejecutar `clean verify` desde el proyecto padre.
4. Detener WildFly y hacer Clean/Publish del EAR para retirar los JSP antiguos del despliegue de Eclipse. Reiniciar.
5. Actualizar también el WAR independiente del callback si estaba desplegado.
6. Abrir `http://localhost:8080/CargaUY-web/login`. Probar LDAP, navegación a inicio, registro con varios roles y operaciones de empresas. No se necesita migrar la base de datos por este cambio de vistas.
