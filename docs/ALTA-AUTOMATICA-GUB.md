# Ingreso y alta automática con Usuario gub.uy

## Comportamiento

El flujo normal pasa a ser login → proveedor gub.uy → validación de identidad → sesión de CargaUY. En el primer ingreso de una identidad y CI nuevas, `SesionesService.emitirGub` crea un Ciudadano activo con rol CIUDADANO, origen GUB y la referencia externa calculada por `GubOidcClient`. No es necesario cargar esos campos a mano.

Los siguientes ingresos reutilizan esa identidad. No se otorgan automáticamente roles de chofer, funcionario, autoridad o administrador, ni vínculos con empresas. Los datos ya editados en el perfil no se sobrescriben con cada ingreso.

ID Uruguay no entrega fecha de nacimiento en el contrato utilizado. El alta inicial la deja pendiente (`null`). Antes de acceder a las páginas de CargaUY, se muestra una ventana obligatoria para completar ese dato. Al guardar, se continúa a Inicio; los siguientes ingresos no muestran la ventana porque se consulta el dato persistido. No se guarda una fecha ficticia ni se usa el mock PDI como fuente de datos reales.

La ventana está en `web/src/main/webapp/completar-perfil.xhtml`. `CompletarPerfilBean` coordina el guardado y `SesionesService.completarNacimiento` toma el usuario del token, valida que la fecha no sea futura y evita sobrescribir una fecha ya guardada desde otra pestaña. `NacimientoPendienteFilter`, registrado en `web.xml`, impide omitir el paso por acceso directo a otras páginas y contempla redirecciones de solicitudes AJAX JSF. Se puede cerrar sesión sin completar el dato.

Este paso obligatorio corresponde a la navegación web; no cambia el contrato de la API móvil. No requiere una migración nueva además de la migración 04.

Las comprobaciones del token, correo y documento de `GubOidcClient` permanecen como estaban. La excepción local de país/tipo que se agregó para el testing sigue siendo una suposición local, no una verificación documental del proveedor.

## Actualizar la base y publicar

Antes de publicar el nuevo EAR:

1. Detener WildFly y conservar un respaldo de la base.
2. En pgAdmin, abrir Query Tool en `cargauy`.
3. Abrir y ejecutar completo `scripts/p1/migracion-04-alta-gub.sql`.
4. Comprobar COMMIT. El script no elimina ni modifica perfiles existentes: permite nacimiento pendiente únicamente para el subtipo CIUDADANO de origen GUB y mantiene una restricción de base para las demás cuentas.
5. Compilar con `mvn clean verify` y publicar el EAR en Eclipse. Refrescar con F5 para no volver a guardar clases antiguas desde el editor.
6. Reiniciar y empezar un ingreso nuevo desde el login; no recargar ni reutilizar el código del callback anterior.
7. Si no hay fecha, comprobar que aparezca la ventana, que no permita guardar vacía/futura y que después de guardar lleve a Inicio. Cerrar sesión y volver a entrar: no debe pedirla nuevamente. Un usuario con fecha ya registrada entra directamente.

El código ya admite el alta; sin la migración, una base con `fecha_nac NOT NULL` rechazará el primer registro sin nacimiento.

## Cuenta preexistente con la misma CI

Si el perfil ya está vinculado a la misma identidad, ingresa normalmente. Si solo coincide la CI pero la identidad todavía no está vinculada, el servicio conserva el conflicto 409. Esto es una excepción de datos previos, no un paso obligatorio del flujo normal: no se toma una cuenta existente y sus roles/vínculos solo porque una identidad nueva afirma el mismo número de documento.

La revisión de una colisión preexistente debe ser administrativa. No borrar el perfil ni crear una CI falsa para superar el conflicto. Esta excepción es especialmente importante en testing, donde país y tipo pueden estar siendo asumidos localmente.

## Pruebas incluidas

Se cubren el primer ingreso sin nacimiento, la creación y vinculación automática con un único rol CIUDADANO, la emisión de sesión, la reutilización del perfil, el rechazo de CI preexistente sin vínculo, cuenta inactiva, rol interno y cambio de CI. También se comprueba que un perfil federado pendiente se pueda editar y completar sin permitir borrar posteriormente la fecha ya completada.

El login real requiere un ensayo del usuario en el navegador después de la migración y publicación; las pruebas unitarias no sustituyen esa comprobación.
