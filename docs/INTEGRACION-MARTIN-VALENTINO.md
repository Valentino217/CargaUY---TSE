# Rama Martin-integracion

Integra `Martin` (3ee3bb3) con `origin/Valentino` (571558c), consultado el 20/10/2026. Las ramas originales se conservan. Esta rama contiene el merge y sus ajustes de compatibilidad.

## Cambios de integración

- Se conserva `GuiaDeViaje.chofer` como `Usuario`, permitiendo roles múltiples de P1. Las pantallas de guías consultan los vínculos canónicos de `VinculoEmpresa`, sin exigir que la persona sea una instancia de la subclase `Chofer`.
- Se conserva la relación anterior `empresa_choferes` para compatibilidad y se migran sus asignaciones al vínculo canónico.
- Se mantiene un único registro JAX-RS en `/api`. El EAR usa `/CargaUY-web`, igual que Eclipse, el receptor gub.uy y el cliente móvil.
- Las pantallas JSF comparten el login de P1 y se protegen mediante sesión y rol. Vehículos y guías usan servicios con token, permisos de empresa y validación de chofer asignable. No hay un ciudadano fijo con ID 1 ni un fallback que muestre todas las empresas al ciudadano sin vínculos.
- La resolución de infracciones toma al operador autenticado y permite un `Usuario` con rol FUNCIONARIO o ADMINISTRADOR; no atribuye resoluciones a un funcionario fijo.
- La API de choferes exige bearer, rol y pertenencia de las guías; consultar una CI ajena devuelve 403. Balanza exige permisos de operador. Los errores del filtro previo a REST tienen tipo JSON para devolver 401/403 correctamente en WildFly.
- Las colas `TrackingQueue` y `FiscalizacionQueue` se declaran en el EAR. El servidor necesita el subsistema de mensajería de WildFly, presente en tu configuración actual. Si otro entorno ya define externamente estos mismos nombres, hay que unificar quién administra las colas para evitar registros duplicados.
- El inicializador de datos de P3 no borra la base. Está desactivado por defecto, y aunque se habilite mediante `p3.datos.prueba=true`, no reemplaza usuarios existentes. No se ejecuta TRUNCATE.
- Se conserva JaCoCo 0.8.15 de Valentino y su configuración de cobertura; aprobar las pruebas no significa acreditar el 80 % exigido por el curso.
- Se fija LF para scripts `.sh` para evitar nuevamente el error `set: Illegal option` de LDAP.

## Publicar en tu WildFly habitual

La comprobación se hizo en un servidor y un esquema temporales. **No se migró tu esquema habitual ni se publicó el EAR en el servidor de Eclipse.**

1. En Eclipse, refrescar los proyectos con F5 y ejecutar Maven → Update Project después de cambiar de rama.
2. Hacer un respaldo de la base `cargauy` y detener WildFly desde Servers.
3. La base debe tener aplicada `scripts/p1/migracion-02.sql`, que ya utilizaste al preparar P1.
4. En pgAdmin → base `cargauy` → Query Tool, abrir **`scripts/p1/migracion-03-integracion.sql`** y ejecutar el archivo completo. Debe terminar en COMMIT. Esta migración conserva los registros, adapta matrículas y relaciones, habilita historial de ITV y agrega evidencia y unicidad de infracciones.
5. Si la migración informa casos duplicados por guía/tipo, revisar esos casos; no se eliminan automáticamente. Ante un error no continuar con el despliegue hasta resolverlo. Si Query Tool queda dentro de una transacción fallida, ejecutar ROLLBACK antes de corregir y repetir.
6. Desde la raíz, con JAVA_HOME apuntando al JDK del proyecto, ejecutar `mvn clean verify`. En Eclipse se puede usar Run As → Maven build → Goals: `clean verify`.
7. Iniciar Docker Desktop y levantar LDAP y PDI con:

   ```powershell
   docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml up -d --build ldap mock-pdi
   ```

8. Conservar `.local-p1` y sus credenciales; no volver a generarlas. `p3.datos.prueba` no necesita habilitarse. El administrador sigue siendo el de LDAP, normalmente `martin.admin`.
9. Iniciar WildFly y hacer Publish del EAR. Abrir `http://localhost:8080/CargaUY-web/login`.
10. Desde el panel de P1, el enlace **Viajes, vehículos y fiscalización** lleva a `index.xhtml`. Las pantallas de empresa utilizan los vínculos registrados en P1.

El EAR compilado se encuentra en `ear/target/CargaUY.ear`. No desplegar archivos del entorno temporal de verificación.

## Preparar un caso compartido

1. Como administrador, crear una empresa y un ciudadano/chofer en el panel P1.
2. Asignar los roles que correspondan y registrar el vínculo con esa empresa: responsable y/o chofer. Crear una persona en SQL no la registra automáticamente en LDAP o ID Uruguay.
3. En Vehículos, crear un vehículo para esa empresa y registrar su ITV/PNC.
4. En Guías, elegir la empresa, el vehículo y un usuario activo con rol CHOFER y vínculo de chofer. La selección admite un Ciudadano que también tenga rol CHOFER.
5. Usar la API de choferes con una sesión válida de ese usuario para consultar y enviar eventos. No basta con indicar una CI: el token determina quién actúa.
6. Un evento INICIO procesado por TrackingQueue inicia el viaje; los consumidores y el motor de P3 continúan el flujo de fiscalización.

Los métodos antiguos de EJB sin token se conservan para compatibilidad interna y pruebas del módulo heredado; las pantallas de vehículos/guías integradas utilizan las variantes con autorización. No presentar esta integración como una auditoría completa de todos los permisos de cada operación heredada de P3.

## Android y Usuario gub.uy

Se incorporó el proyecto Android de Valentino. La URL de desarrollo apunta al WildFly del emulador (`10.0.2.2:8080`) y las peticiones admiten un bearer en memoria desde el campo **Token de integración**. No se incluye un token ni un secreto fijo en el código. Un 401/403 no provoca reintentos hacia otros servidores.

Ese campo es una herramienta temporal de QA; **no sustituye el login OIDC del móvil**. El callback móvil no está registrado en el cliente entregado por el curso y PKCE sigue pendiente de confirmación. Consultar `docs/P2/USUARIO-GUB-UY-REAL.md`. El teléfono físico necesita una URL accesible y transporte HTTPS; no desactivar los filtros para enviar credenciales por HTTP remoto.

El ingreso web real con gub.uy conserva sus pendientes anteriores: códigos de país/tipo de documento, cuenta personal de testing, vinculación inicial y cierre SSO. El merge no acredita esas pruebas ni las reemplaza con credenciales ficticias.

## Verificación realizada

Consultar el resultado final en `VERIFICACION-INTEGRACION.md`. Se validaron Maven, despliegue JPA/REST/JMS y pantallas en WildFly 41 con un esquema temporal separado del esquema habitual. La autenticación del proveedor no se sustituyó en el proyecto: para las pruebas HTTP se utilizaron sesiones ficticias exclusivamente en el entorno temporal, que se retiró al terminar.
