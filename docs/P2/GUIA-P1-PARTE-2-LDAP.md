# Guía P1 · Parte 2: LDAP, identidad, permisos e integración
Fecha: 15 de octubre de 2026. Continuación de GUIA-P1-SIN-LOGIN.md.
Entorno acordado: WildFly 41 y PostgreSQL locales; LDAP y mock PDI en Docker; Usuario gub.uy en el entorno de testing del curso. Keycloak queda como alternativa opcional.

**Actualización Usuario gub.uy:** leer [USUARIO-GUB-UY-REAL.md](USUARIO-GUB-UY-REAL.md) antes de configurar identidad. Incluye el cliente 890192, retorno por la URI registrada http://localhost:8080 y el WAR raíz adicional. El secreto se completa únicamente en configuración privada.

## 0. Cómo usar esta guía
Esta es una implementación de referencia para incorporar y comprender, igual que en la primera guía. Los archivos están preparados bajo docs/P2/ejemplos y NO fueron aplicados al código activo ni a tu PostgreSQL.
El código completo está en CODIGO-P2.md y también como archivos individuales dentro de ejemplos. Cada ruta del anexo es el destino desde la raíz del repositorio, no desde la carpeta docs.
No copiar un archivo de aplicación a src/test/java: ahí solo van los que aparecen explícitamente bajo esa ruta.
Las decisiones nuevas —Keycloak como simulador opcional, WS-Addressing, roles múltiples y relación canónica con Usuario— son propuestas para revisar con el grupo. Son parte del diseño, no hechos ya aprobados por el equipo docente.

Orden recomendado: leer secciones 1–4; preparar configuración; aplicar migración y archivos; compilar; probar LDAP; luego gub.uy, permisos, PDI y REST.
No mezclar archivos antiguos con archivos nuevos del mismo servicio: las firmas de los métodos cambiaron para exigir un token. Los ejemplos incluyen los tests y los servlets que llaman a esas firmas.

## 1. Qué vamos a implementar
1. Administrador local de pruebas cuya contraseña se valida en LDAP.
2. Login LDAP para administradores/autoridades, sin contraseña de respaldo en PostgreSQL.
3. Login externo OIDC para ciudadanos/choferes/funcionarios con ID Uruguay de testing; alternativa Keycloak para práctica. El alta real requiere completar el perfil y vincularlo administrativamente.
4. Sesiones web, bearer REST, validarToken, expiración y logout revocable.
5. Filtro web, AuthFilter REST, protección CSRF y autorización en los servicios.
6. Roles múltiples y funciones de responsable/chofer por empresa.
7. PDI con ciudadano inexistente, fallas diferenciadas y WS-Addressing.
8. Concurrencia acotada, circuito y prueba de carga del cliente PDI.
9. Pantallas separadas de ingreso, administración, empresas y perfil.
10. Reglas actuales consumibles por el motor, paginación y conflictos de edición.
11. Logs correlacionados, métricas, health checks y preparación de TLS.

Esta guía completa piezas de P1, pero no desarrolla el móvil, el motor completo de fiscalización, dashboards de otros integrantes o el despliegue productivo del grupo. Esos módulos deben utilizar los contratos indicados. No declarar pruebas de integración con módulos que todavía no existen.

## 2. La separación que evita problemas con el administrador
Hay tres cuentas distintas:
• Administrador de CargaUY: por defecto martin.admin; ingresa a /login y se valida contra LDAP.
• Administrador del directorio LDAP: DN cn=manager,dc=cargauy,dc=local; sirve para administrar el directorio, no para entrar a CargaUY.
• Administrador de Keycloak: gestor-idp; administra el simulador, no es administrador de CargaUY.
El usuario de la consola WildFly y el usuario PostgreSQL también siguen siendo independientes.

La contraseña de martin.admin se genera una sola vez y queda en .local-p1/cuentas-locales.txt, fuera del código. LDAP guarda su hash. PostgreSQL guarda el perfil, los roles y la referencia LDAP, pero no una copia de esa contraseña.
Si LDAP está caído, los nuevos logins internos fallan de forma controlada. No se habilita una contraseña local alternativa.
BootstrapAdmin crea el perfil local si se habilita p1.bootstrap. No crea la entrada LDAP ni reactiva un usuario dado de baja. Después de probar el primer ingreso se deshabilita el bootstrap.
Así obtenemos un administrador reproducible de pruebas sin inventar una puerta trasera.

## 3. Decisiones de diseño y contratos
### 3.1 Autenticación y autorización
Autenticación responde quién sos; LDAP u OIDC validan eso.
Autorización responde qué podés hacer; ControlAcceso consulta roles y pertenencia en PostgreSQL.
Cada método público de perfiles/plataforma recibe un token. No basta con pasar el filtro web o esconder un botón.
No usamos @RolesAllowed como única protección: el contexto REST personalizado no configura por sí solo el principal EJB de WildFly. Aquí la comprobación es explícita en negocio y funciona igual desde JSP y REST.

### 3.2 Tokens opacos
Se generan 32 bytes aleatorios y se codifican en Base64 URL-safe. El token tiene 43 caracteres y no contiene roles, contraseña o CI.
En sesiones_acceso se guarda SHA-256 del token, no el token utilizable. SHA-256 es adecuado para este secreto aleatorio de alta entropía; para contraseñas humanas sigue correspondiendo BCrypt o el almacenamiento propio del proveedor.
Duración absoluta: 30 minutos. La sesión web además tiene 15 minutos de inactividad. No se implementa renovación silenciosa infinita.
El logout elimina la sesión de base. Dar de baja un usuario lo bloquea también en la validación. Cambiar identidad/roles revoca sesiones.
Los tokens pueden validarse en distintas instancias porque residen en PostgreSQL. La HttpSession web necesita almacenamiento distribuido o configuración de sesión compartida si despliegan varias instancias; una sola instancia local no demuestra escalado horizontal.

### 3.3 Roles y subtipos
Los subtipos existentes se mantienen por compatibilidad, pero el conjunto Usuario.roles es la fuente de autorización.
Una persona tiene un único Usuario/CI; no se crean dos usuarios con la misma CI para darle dos funciones.
Ejemplo: un Ciudadano puede tener roles CIUDADANO y CHOFER y estar vinculado a la empresa como responsable y chofer.
No se mezclan roles internos ADMINISTRADOR/AUTORIDAD con externos CIUDADANO/CHOFER/FUNCIONARIO en una misma cuenta de proveedor: una cuenta interna usa LDAP, una externa gub.uy. Una persona con funciones de ambos mundos necesita una decisión de identidad del grupo antes de ampliar esa regla.
TYPE(u) en el listado sigue filtrando el subtipo primario, no el conjunto de roles. El DTO muestra también roles para distinguirlos.

### 3.4 Chofer y empresa
VinculoEmpresa relaciona Usuario y Empresa con funciones responsable y chofer. Ese vínculo y el rol del usuario deben habilitar la operación.
GuiaDeViaje.chofer pasa a referenciar Usuario; Chofer hereda la colección de guías desde Usuario.
Esto afecta el modelo compartido. Revisarlo con quien implemente viajes. Si su código espera Chofer c = guia.getChofer(), debe adaptarse a Usuario y comprobar rol/asignación.
La colección antigua Empresa.ciudadanos se conserva como compatibilidad. No es la fuente de permisos para los vínculos nuevos. Para consultar miembros y funciones usar listarVinculos.
ControlAcceso.choferAsignable debe ejecutarse en el servicio de viajes ANTES de asignar el chofer.

### 3.5 BCrypt y los proveedores
GestionPerfilesService conserva el alta con BCrypt del modelo anterior. Sirve como registro compatible, no como login interno alternativo.
Al vincular una identidad LDAP/GUB, IdentidadesService reemplaza la credencial local por un marcador no utilizable. Los logins solo pasan por el proveedor.
En el simulador, el primer login GUB crea un Ciudadano con los datos del fixture. Con ID Uruguay real, la fecha de nacimiento no está anunciada: primero se crea el perfil completo y se vincula la referencia verificada según el complemento. Una coincidencia de CI nunca vincula identidades automáticamente.

## 4. Qué archivos copiar y cuáles reemplazar
Todos se encuentran bajo docs/P2/ejemplos. El anexo CODIGO-P2.md contiene cada archivo completo y su destino.

### Reemplazos en ejb/src/main/java
• domain/entity: Usuario.java, Empresa.java, Chofer.java, GuiaDeViaje.java.
• dto: UsuarioResumen.java, EmpresaResumen.java.
• service: GestionPerfilesService.java, GestionPlataformaService.java, FuncionarioEJB.java.
• integration: ClientePdi.java.
No reemplazar ErrorNegocio, Validaciones, TipoUsuario, DatosUsuario, ResultadoPdi o ReglaFiscalizacion: se reutilizan desde la primera guía.
Tu paquete real es integration, en inglés; no crear otro integracion por mezclar versiones de las guías.

### Archivos nuevos en ejb/src/main/java
• domain/entity: SesionAcceso, FlujoIdentidad y VinculoEmpresa.
• security: todos los archivos de esa carpeta en los ejemplos.
• service: ConsultaCiudadanoService, ReglasLecturaService, EvaluadorToleranciasService y EstadoService.

### Web
• Reemplazar P1PruebasServlet y FuncionarioServlet: devolverán 410 para retirar las entradas antiguas.
• Agregar las clases nuevas del paquete web y web.api.
• Agregar WEB-INF/p2/login.jsp y WEB-INF/p2/panel.jsp.
El panel anterior ya no se usa; podés retirarlo después de verificar el nuevo. No dejar una ruta antigua que siga llamando servicios sin autorización.

### Tests
Reemplazar los tres tests de servicios/PDI de la guía 1 por sus versiones de esta guía.
Agregar SeguridadP2Test, OidcP2Test, IdUruguayP2Test, FlujosP2Test y EvaluadorToleranciasServiceTest.
Conservar los tests de dominio que ya existían.

### Infraestructura
Copiar docker/ldap, reemplazar los archivos indicados de docker/mock-pdi y agregar pdi.wsdl.
Agregar docker-compose.p1.yml y docker-compose.p1.tls.yml en la raíz.
Agregar scripts/p1 en la raíz.
Agregar gub-callback-root y desplegar su WAR independiente según USUARIO-GUB-UY-REAL.md. Es necesario para utilizar la URI raíz ya registrada.
.gitignore.fragment es una instrucción: su línea debe ir dentro del .gitignore real.

## 5. Preparar secretos y servicios sin cambiar tu base
### 5.1 Respaldo
Guardar todos los archivos de Eclipse. Revisar git status y hacer un commit del avance actual si corresponde.
Respaldar cargauy en pgAdmin antes de la migración. No usar drop-and-create: mantener schema-generation.database.action=none.

### 5.2 Ignorar secretos ANTES de generarlos
Abrir .gitignore de la raíz y agregar:
```text
/.local-p1/
```
Esto evita subir claves, contraseñas, datos del simulador y certificados privados. No copiar .local-p1 a docs ni a una carpeta de código.
Copiar scripts/p1/preparar-local.ps1 y ejecutar desde la raíz del proyecto:
```powershell
.\scripts\p1\preparar-local.ps1
```
Si PowerShell bloquea scripts, podés ejecutar esa vez:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\p1\preparar-local.ps1
```
No se cambia la política global. Leer el script antes de ejecutarlo.
Opcionalmente elegir el nombre y CI de prueba:
```powershell
.\scripts\p1\preparar-local.ps1 -Uid martin.admin -Ci 90000001
```
90000001 es un dato ficticio de prueba. El script genera:
• servicios.env: configuración privada de contenedores.
• gub-realm.json: realm de prueba con un ciudadano y secreto del cliente OIDC.
• wildfly.properties: propiedades del central.
• cuentas-locales.txt: las credenciales generadas.
Abrir cuentas-locales.txt para consultar la contraseña; no pegarla en el código ni en el chat.
No ejecutar de nuevo para cambiar passwords: los volúmenes existentes conservan el estado. El script se niega a sobrescribir la configuración para evitar desalinearlo.

### 5.3 Levantar Docker
Si el mock PDI de la primera guía está ocupando 8081, detener solo ese servicio:
```powershell
docker compose stop mock-pdi
```
Para ID Uruguay real, iniciar solo LDAP y PDI:
```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml up -d --build ldap mock-pdi
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml ps
```
No se inicia ni reemplaza PostgreSQL o WildFly.
Puertos:
• LDAP: 127.0.0.1:1389.
• Simulador gub.uy: http://localhost:8083.
• Mock PDI: http://localhost:8081/pdi/ws?wsdl.
Solo si se elige el simulador opcional, iniciar además el servicio gub con Compose y esperar la importación del realm. Comprobar:
```text
http://localhost:8083/realms/cargauy-test/.well-known/openid-configuration
```
Debe devolver JSON. Que un contenedor diga Started no prueba que el servidor ya esté listo.
Keycloak start-dev es únicamente para el simulador local, no para el despliegue productivo.

Antes de aplicar WildFly, reemplazar las propiedades gub.* generadas por las de [Usuario gub.uy real, sección 3](USUARIO-GUB-UY-REAL.md#3-configuración-privada-de-wildfly). El script de preparación genera el simulador; no genera ni conoce el secreto real del curso.

## 6. Configurar WildFly sin la ventana de Eclipse que se quedaba cargando
WildFly debe estar iniciado para usar su CLI.
Con los scripts copiados, ejecutar:
```powershell
.\scripts\p1\configurar-wildfly.ps1 -WildFlyHome 'C:\Users\Martin\OneDrive\Desktop\Facultad\TSE\wildfly-41.0.0.Final'
```
La CLI local puede autenticar por el mecanismo local de WildFly. Si solicita credenciales, son las de administración de WildFly, no martin.admin ni PostgreSQL.
Este script lee .local-p1/wildfly.properties y crea/actualiza system properties; no imprime las contraseñas de cuentas.
Alternativa: Configuration → System Properties en localhost:9990, agregando cada propiedad del archivo (sin -D).

Propiedades importantes:
• p1.entorno.local=true: permite HTTP/LDAP claro únicamente en loopback durante desarrollo.
• p1.bootstrap=true: habilita una vez la creación del perfil local del administrador.
• bootstrap.admin.*: identidad ficticia, no contraseña.
• ldap.url y ldap.people.dn: ubicación del directorio.
• gub.proveedor=iduruguay: selecciona las rutas de ID Uruguay.
• gub.issuer: https://auth-testing.iduruguay.gub.uy/oidc/v1 para el curso; realm de Keycloak solo en la alternativa.
• gub.client.secret: secreto del cliente web; no es una contraseña de usuario.
• gub.redirect: http://localhost:8080 para el cliente real registrado; requiere el receptor raíz descrito en el complemento.
• gub.mobile.enabled=false: el retorno móvil anterior no está registrado en el cliente del curso.
• pdi.endpoint: mock local.
• p1.pruebas=false: el panel temporal queda retirado.

Con ID Uruguay, si cambia el contexto de CargaUY, actualizar cargauy.context en el WAR raíz; mantener gub.redirect=http://localhost:8080. Solo para Keycloak opcional se actualizan gub.redirect y las redirectUris de su cliente.
No regenerar el realm para cambiar ese dato si ya se importó: Keycloak omite el import de un realm existente. Usar su consola administrativa para editar el cliente.
La configuración de esta guía usa /CargaUY-web porque es el contexto real que mostró tu último despliegue de Eclipse.

## 7. Migración y código: aplicar de forma coordinada
1. Detener WildFly desde Servers → Stop.
2. Abrir pgAdmin → cargauy → Query Tool.
3. Abrir scripts/p1/migracion-02.sql y revisar sus instrucciones.
4. Ejecutar el script completo. Debe terminar con COMMIT.
5. Copiar TODOS los Java/JSP indicados en la sección 4, reemplazando los archivos existentes.
6. Guardar todos los archivos en Eclipse.
7. Maven → Update Project.
8. Run As → Maven build… → Goals: clean package.
9. Revisar BUILD SUCCESS. No escribir Goals: ni mvn dentro de ese campo.
10. Iniciar WildFly y Publish del EAR.

La migración no borra usuarios o empresas. Agrega tablas/columnas y cambia una FK de guía para referenciar Usuario. Esa última modificación es estructural: revisar con el equipo y mantener el respaldo.
El código de la guía 1 sin actualizar dejará errores como método crearUsuario(DatosUsuario) no definido: ahora se exige crearUsuario(token, datos). Por eso se reemplazan también los llamadores y tests.
Los importes Jakarta JSON-P ya están en los pom del proyecto actual; no se agregó una librería JWT extra. LDAP usa javax.naming porque pertenece a Java SE, no porque sea Java EE antiguo.

## 8. Primer ingreso del administrador
Abrir:
```text
http://localhost:8080/CargaUY-web/login
```
En Administradores y autoridades:
• Usuario: martin.admin (o el UID elegido).
• Contraseña: la que figura para Administrador de CargaUY en cuentas-locales.txt.
El login debe llevar a /app/admin.

No usar el usuario admin de la consola WildFly ni cn=manager del directorio.
Si falla:
• 401: usuario/password inválido, perfil LDAP no vinculado, cuenta inactiva o UID con distinta escritura.
• 503: LDAP no responde; revisar contenedor/URL.
• Error de columna: falta migración o se publicó código nuevo con esquema viejo.
• Bootstrap CI ocupada: la CI ficticia ya existe; elegir otra y actualizar la configuración de bootstrap antes de reintentar.
• Si el directorio acepta la contraseña pero no hay perfil local, revisar p1.bootstrap y uidExterno/origenIdentidad de usuarios.

Después del primer ingreso correcto:
1. Cambiar p1.bootstrap=false en wildfly.properties.
2. Aplicar de nuevo configurar-wildfly.ps1.
3. Reiniciar WildFly.
4. Ingresar otra vez.
Esto prueba que el administrador funciona por su perfil persistido y LDAP, no por recrearse en cada arranque.
No se reactiva ni se cambian roles automáticamente en cada inicio.

## 9. Login Usuario gub.uy real
Seguir [USUARIO-GUB-UY-REAL.md](USUARIO-GUB-UY-REAL.md): configurar el cliente real, desplegar el WAR raíz adicional y probar con una cuenta personal de testing. No se recibió una cuenta personal: client_id y client_secret identifican a la aplicación. El alta inicial requiere perfil completo y vinculación administrativa porque gub.uy no anuncia fecha de nacimiento. El cierre de sesión SSO y el móvil real quedan identificados como pendientes.

### Alternativa opcional: Keycloak
Los pasos siguientes son exclusivamente del simulador; requieren gub.proveedor=keycloak, gub.pkce=true, issuer del realm y sus credenciales generadas. En /login, hacer clic en Ingresar con Usuario gub.uy.
El navegador va a Keycloak. Usar:
• Usuario: ciudadano.prueba.
• Contraseña: la de Ciudadano del simulador en cuentas-locales.txt.
Al regresar se valida state, code/PKCE, firma RS256, issuer, audiencia, nonce y vencimiento; userinfo debe corresponder al mismo subject.
En el primer acceso se crea un Ciudadano con CI 80000001, correo y nacimiento de prueba. La pantalla permite consultar el perfil.
No se guarda ni se solicita desde CargaUY la contraseña del ciudadano del proveedor.

Si 80000001 ya existe:
• No borrarlo para pasar la prueba.
• Ingresar como administrador y revisar a quién pertenece.
• Si corresponde, vincular ese perfil a origen GUB y subject 11111111-1111-4111-8111-111111111111 del fixture.
La vinculación es explícita y revoca sesiones.

Esta alternativa permite practicar sin el proveedor real. Las credenciales del curso se usan únicamente con la configuración ID Uruguay del complemento, nunca con Keycloak.
Ni la existencia de un botón ni decodificar un JWT sin verificar su firma acreditan autenticación externa.

## 10. Roles, empresas y choferes
### Como administrador
1. Crear o consultar una empresa.
2. Consultar/listar usuarios y anotar ID, roles y versión.
3. En roles, dar al ciudadano de prueba CIUDADANO,CHOFER.
4. En vinculo, indicar número de empresa, ID y marcar responsable/chofer.
5. listarVinculos muestra las funciones reales del vínculo.
6. Si un usuario solo será funcionario, crear/vincular su perfil externo y asignar FUNCIONARIO; no darle contraseña LDAP porque la letra exige gub.uy para ese actor.

Las cuentas nuevas creadas desde el alta compatible quedan LOCAL hasta que se vinculen a su proveedor. No hay login LOCAL expuesto. Para una cuenta interna:
• Crear su entrada en LDAP con herramientas del directorio.
• Crear el perfil local.
• Vincularlo a origen LDAP y el UID.
Para una cuenta externa real, seguir el procedimiento de perfil completo y referencia de vinculación del complemento Usuario gub.uy. El primer acceso automático que crea CIUDADANO con fecha de nacimiento corresponde únicamente al fixture opcional de Keycloak.
No es correcto crear un perfil SQL y asumir que eso crea también la cuenta del proveedor.

### Como responsable de empresa
Ver y editar únicamente empresas donde el vínculo responsable esté activo y tenga rol CIUDADANO.
No puede cambiar roles, crear administradores, gestionar nodos ni editar reglas.
### Como chofer
La función CHOFER y el vínculo chofer permiten asignarlo al viaje; no otorgan administración.
### Como autoridad
Puede consultar empresas globalmente; sus reportes pertenecen al módulo de autoridades.
### Como funcionario
Puede gestionar empresas y vínculos. No administra roles ni identidades.

### Comprobación importante
Intentar acceder a otra empresa cambiando el número en el formulario o URL debe dar 403 desde negocio, aunque el usuario consiga enviar el formulario.
El rol no se toma de un campo oculto, de una cabecera X-Rol o de un parámetro enviado por el navegador.

## 11. Paginación y conflictos de edición
Los listados de usuarios/empresas reciben pagina desde 0 y tam entre 1 y 100. Ejemplo: pagina=0,tam=20.
Primero consultar el usuario/empresa/regla y anotar version. En la edición enviar esa versión.
Si otra operación ya modificó el registro, guardar debe fallar y pedir recargar; no sobrescribir a ciegas.
El DTO de usuario ahora incluye roles y version. El de empresa también incluye version.
Los listados de catálogos conservan el comportamiento anterior; si crecen deben paginarse de la misma manera.
El listado de vínculos está acotado a 100 en esta referencia. Para empresas con más miembros hay que extender su contrato con página/tamaño antes de usarlo como listado completo.

## 12. PDI: contrato completo y WS-Addressing
Reemplazar cliente y mock juntos. El cliente nuevo exige Addressing y el mock nuevo valida esas cabeceras; no mezclar versiones.
El WSDL nuevo está en docker/mock-pdi/pdi.wsdl y se publica con el endpoint de entorno.
La consulta envía:
• Action: operación a ejecutar.
• To: destino configurado.
• MessageID: UUID de la solicitud.
• ReplyTo: respuesta anónima por HTTP.
La respuesta lleva Action y RelatesTo, que debe coincidir con MessageID. SOAPAction HTTP coincide con Action.
Esto implementa WS-Addressing como propuesta del opcional 9; NO implementa WS-Security. HTTPS se encarga del transporte, no sustituye las cabeceras WS-*.

Desde administración:
• consultarPdi con 12345678 o 80000001 → DISPONIBLE.
• consultarPdi con 11111111 → NO_ENCONTRADO.
• Detener mock → NO_DISPONIBLE.
El desconocido se representa con un SOAP Fault tipificado CiudadanoNoEncontrado. No se interpreta vigente=false como inexistencia.
La consulta PDI está autorizada: el ciudadano consulta su propia CI; administrador/funcionario pueden consultar otras.
El resultado no sobrescribe automáticamente perfiles con datos de prueba.

Comandos de caída y recuperación:
```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml stop mock-pdi
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml start mock-pdi
```
Esperar diez segundos para la prueba de recuperación del circuito después de fallas sostenidas.

## 13. Concurrencia y resiliencia del cliente PDI
La primera guía serializaba todas las consultas con @Lock WRITE. Ahora:
• @Lock READ permite llamadas concurrentes.
• Un Semaphore admite hasta cuatro por instancia.
• Si no hay cupo, se devuelve fallback enseguida, sin cola ilimitada.
• Cada consulta tiene como máximo dos intentos, con timeout de conexión de un segundo y de petición de dos.
• Tres consultas fallidas abren el circuito diez segundos.
• Luego una sola consulta comprueba recuperación; las demás reciben fallback mientras esa prueba está en curso.
El estado del circuito es por instancia. La configuración debe medirse para el volumen real; cuatro no es un valor universal.
No reintentar escrituras indiscriminadamente: esta operación es consulta de lectura.
La cota de timeout aplica por intento; no confundirla con p95 de todas las consultas de la plataforma.

Prueba reproducible (PowerShell 7, mock encendido):
```powershell
.\scripts\p1\carga-pdi.ps1 -Cantidad 100 -Concurrencia 12
```
Solicita la contraseña LDAP de manera interactiva, crea una sesión temporal, cuenta estados, calcula p95 y guarda .local-p1/pdi-carga.csv.
Esta es evidencia del componente PDI bajo esa configuración, no de la ingesta de tracking ni de toda la plataforma. No da por cumplido el SLO de la letra.

## 14. API REST, logout y móvil
Base: http://localhost:8080/CargaUY-web/api.
Rutas:
• POST /auth/login: uid/password LDAP para cuentas internas.
• GET /auth/me: identidad y roles del bearer.
• POST /auth/logout: revoca el bearer.
• GET /empresas?pagina=0&tam=20: lista autorizada.
• GET /ciudadanos/{ci}/pdi: consulta autorizada y correlacionada.
• GET /public/health: proceso web activo.
• GET /public/ready: base disponible; devuelve 503 si no está lista.
• GET /ops/metrics: métricas para administrador.
• POST /auth/gub/iniciar y /auth/gub/terminar: flujo móvil externo.

Todas las rutas salvo las de login/flujo inicial y health exigen Authorization: Bearer TOKEN. Una cookie web no sustituye el bearer.
No mostrar ni guardar tokens en logs. El script de prueba no los imprime:
```powershell
.\scripts\p1\probar-api.ps1
```
Debe comprobar LDAP, bearer, PDI, inexistencia y revocación. Después del logout, reutilizar ese token debe dar 401.

### Contrato para quien implemente el móvil
**ID Uruguay real:** este flujo está bloqueado por defecto: la URI móvil no está registrada y PKCE no se verificó con el proveedor. Los pasos siguientes describen el contrato propuesto y probado con el simulador; no acreditan su funcionamiento con el cliente 890192.
1. Generar verifier PKCE aleatorio en el dispositivo; conservarlo hasta terminar.
2. Calcular challenge S256 y enviarlo a POST /auth/gub/iniciar:
```json
{"challenge":"BASE64URL_SHA256_DEL_VERIFIER"}
```
3. Abrir la url devuelta en el navegador del sistema. Conservar state y validar que el callback devuelva el mismo.
4. La app debe registrar uy.cargauy:/oauth/callback (o la URI acordada) como callback.
5. Enviar code, state y verifier a POST /auth/gub/terminar:
```json
{"code":"CODIGO_DEL_PROVEEDOR","state":"STATE_RECIBIDO","verifier":"VERIFIER_ORIGINAL"}
```
6. Recibir el token de CargaUY y almacenarlo en el mecanismo seguro del dispositivo.
7. Usarlo como bearer. Ante 401, volver a autenticar sin borrar eventos offline.
El secreto del cliente OIDC queda en el central, nunca en el móvil. El servidor conserva nonce/desafío con expiración y consume el flujo una sola vez.
El backend de referencia está incluido; configurar Android/iOS, probar deep links e integrar el código del móvil es tarea conjunta con su responsable.
Los flujos de web y móvil tienen redirect URIs diferentes y no deben intercambiarse.

## 15. Consumo de reglas y asignación: integración con otros integrantes
El motor debe inyectar ReglasLecturaService o EvaluadorToleranciasService. La lectura evita caché de segundo nivel y refresca la entidad.
Ejemplo dentro de un EJB del motor:
```java
@EJB
private EvaluadorToleranciasService tolerancias;

// En la evaluación de una pesada:
boolean exceso = tolerancias.excesoCarga(
    pesoMedidoKg, pesoPropioVehiculoKg, capacidadVehiculoKg);

// La distancia ya debe haber sido calculada correctamente en kilómetros:
boolean desvio = tolerancias.superaDesvio(distanciaAlRecorridoKm);
```
El helper no genera casos, no decide el criterio de varios puntos y no calcula una ruta. Esas responsabilidades siguen en fiscalización.
No leer el umbral una sola vez al iniciar y conservarlo en static si se exige editar sin redesplegar.

Antes de asignar chofer:
```java
@EJB
private ControlAcceso control;

// Dentro del método transaccional de alta/asignación del viaje:
control.choferAsignable(token, numeroEmpresa, idUsuarioChofer);
```
El servicio de viajes también debe comprobar que vehículo y guía pertenezcan a esa empresa, sus estados/versiones y los permisos específicos. La FK ahora comprueba identidad referenciada, no rol CHOFER por sí sola.
Los tests nuevos prueban que evaluaciones sucesivas observan las tolerancias recibidas; el escenario completo debe verificarse con el motor real.

## 16. Observabilidad y trazabilidad
CorrelacionFilter valida/genera X-Correlation-ID, lo devuelve al cliente y lo pasa al servicio PDI.
Los logs de central y mock contienen el mismo identificador y duración. No registran tokens, passwords ni el contenido de perfiles.
El API /ops/metrics incluye solicitudes, errores, duración total, intentos/fallas/rechazos PDI. Son contadores por instancia, no un agregador de todo el cluster ni una medición automática de p95.

Para un ensayo:
• El cliente REST/móvil manda un UUID en X-Correlation-ID.
• Consulta /api/ciudadanos/{ci}/pdi con bearer.
• Se conserva el identificador en la respuesta y en logs de central y mock.
Esto permite seguir ese flujo entre componentes. Para acreditar el trazado distribuido exigido por la letra debe demostrarse con el móvil real y los componentes elegidos, no solo con este script; si el grupo adopta OpenTelemetry, instrumentar este mismo flujo y exportarlo al colector.
No presentar un identificador en logs como si ya fueran spans OpenTelemetry exportados.
Configurar logs JSON del servidor o un recolector si necesitan que cada línea completa sea JSON: aquí el mensaje de aplicación contiene JSON, mientras WildFly puede seguir añadiendo su prefijo habitual.

## 17. HTTPS y LDAPS: pasar del entorno local a transporte cifrado
Las URI entregadas para el cliente 890192 permiten HTTP local. El paso a HTTPS del callback requiere nuevas URI registradas; no cambiar gub.redirect a https://localhost:8443 sin esa habilitación. El overlay siguiente prepara servicios locales y el simulador opcional, no cambia el registro de ID Uruguay.
El entorno inicial permite HTTP/LDAP SOLO porque es loopback y p1.entorno.local=true. Eso no acredita HTTPS final.
Se incluye docker-compose.p1.tls.yml como overlay para pruebas con certificados del equipo. No contiene claves privadas ni desactiva validación.
Preparar bajo .local-p1/tls:
• ca.crt.
• ldap.crt/ldap.key para LDAPS.
• pdi.crt/pdi.key para SOAP HTTPS.
• gub.crt/gub.key únicamente para Keycloak HTTPS opcional. El proveedor real ya usa HTTPS y no utiliza estos certificados.
Los certificados deben tener SAN localhost para estas pruebas y encadenar a una CA de desarrollo que el navegador y la JVM confíen. No usar certificados autofirmados sin verificar identidad ni deshabilitar la verificación del cliente.

Con certificados válidos, levantar el overlay:
```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml -f docker-compose.p1.tls.yml up -d --build
```
El overlay cambia:
• LDAP: ldaps://localhost:1636.
• PDI: https://localhost:8081/pdi/ws.
• Keycloak: https://localhost:8084/realms/cargauy-test.
• WildFly: usar su listener HTTPS, por ejemplo https://localhost:8443/CargaUY-web.

Actualizar las propiedades, la redirect URI de Keycloak y la URL base del cliente. Poner p1.bootstrap=false antes de quitar p1.entorno.local.
Después poner p1.entorno.local=false para exigir HTTPS/LDAPS y cookies Secure.
La JVM necesita confiar en la CA. Crear un truststore privado con keytool, revisar su huella y configurarlo para WildFly. No incluir la clave privada de los servidores en el truststore del cliente.
La emisión/instalación de certificados y la configuración final de ANTEL corresponden al despliegue del grupo. El overlay no es una receta de producción: sigue usando start-dev y una base de desarrollo para el simulador Keycloak.
Detrás de proxy, configurar los encabezados reenviados únicamente desde proxies confiables; no aceptar X-Forwarded-Proto arbitrario como prueba de HTTPS.

## 18. Pruebas de aceptación, sin saltear las negativas
A. LDAP: login correcto; contraseña incorrecta → 401; directorio caído → 503; ninguna autenticación de respaldo.
B. Admin: apagar bootstrap y volver a entrar; no duplicar usuarios; no permitir quitarte tu propio rol admin o desactivarte.
C. GUB: primer acceso; CI ocupada exige vinculación; callback con state alterado falla; token con firma/issuer/audiencia/nonce incorrectos falla.
D. Sesiones: logout invalida bearer; usuario inactivo no entra; tokens vencidos dan 401; cerrar sesión web destruye HttpSession.
E. Roles: ciudadano no accede a /app/admin ni modifica catálogos; funcionario no cambia identidades; autoridad no modifica empresa.
F. Empresa: responsable propio puede editar; empresa ajena da 403; chofer requiere rol y vínculo.
G. Edición: dos formularios con la misma versión; guardar uno y rechazar el segundo.
H. PDI: disponible, inexistente, caída, recuperación, cabeceras inválidas y respuesta de otra solicitud.
I. Concurrencia: sin cola ilimitada; los rechazos se cuentan y no consumen una conexión por cada solicitud entrante.
J. Reglas: editar y evaluar otra vez sin redesplegar; comprobar kg/km y versión.
K. Transporte: con modo local deshabilitado, HTTP externo no autentica; verificar certificados y cookies.
L. Trazas: mismo identificador visible en móvil/central/mock cuando esté integrado.

Las pruebas unitarias están en los ejemplos. Mockito comprueba decisiones de negocio; no valida mapeos JPA, transacciones reales, el incremento de @Version o la importación del EAR. Esos escenarios requieren las pruebas de despliegue.
No declarar cobertura 80 % o p95 menor a 500 ms sin medirlos.

## 19. Qué se verificó al preparar esta guía
Actualización ID Uruguay: se consultó metadata pública, se recompiló Java y pasaron cuatro pruebas OIDC contra un proveedor temporal con datos ficticios. También se comprobó la lógica del receptor raíz. No se realizó login remoto con una persona; ver el alcance actualizado en VERIFICACION-P2.md.
• Compilación de los ejemplos Java con --release 17.
• Ejecución de los tests de referencia y del dominio existente (ver VERIFICACION-P2.md).
• Imagen LDAP construida y cliente probado con bind real, credencial incorrecta y caída del directorio.
• Keycloak temporal 26.4.7: login real del fixture, código con PKCE y validación Java de firma/claims/userinfo.
• Sintaxis de scripts PowerShell y Python; XML del WSDL; configuración Compose.
No se aplicó la migración a tu base ni se desplegó esta segunda versión en tu WildFly. No se probó el TLS completo, el móvil real ni el motor completo de fiscalización.
Los contenedores usados para verificar se eliminaron. Las imágenes descargadas/construidas quedan disponibles para reutilizar.

## 20. Explicaciones para el profesor
**¿Por qué LDAP si ya había BCrypt?**
LDAP es el mecanismo interno elegido para el opcional 8. BCrypt protegía credenciales locales, pero no sustituye el directorio. La contraseña interna se verifica por bind y no se duplica como fallback SQL.

**¿Por qué los roles están en SQL y no en LDAP?**
La autenticación se delega al directorio. Los permisos de la aplicación y relaciones con empresas se administran transaccionalmente en su modelo. Es una decisión explícita; el opcional exige autenticación interna LDAP, no necesariamente almacenar allí todos los permisos.

**¿Por qué hay un bootstrap?**
Resuelve la primera cuenta sin una ruta pública que permita crear administradores. Se habilita expresamente para desarrollo, genera solo el perfil y no revive cuentas dadas de baja. Su contraseña vive en LDAP y se genera fuera del código.

**¿Por qué no usar JWT propio?**
El token opaco con registro compartido simplifica revocación inmediata y cambios de roles sin mantener listas de JWT revocados. Requiere una consulta de base; ese costo se mide. OIDC sí usa JWT del proveedor y su firma se verifica.

**¿Por qué validar firma, issuer, audiencia y nonce?**
La firma sola no demuestra que el token fue emitido para esta aplicación o este intento de login. Los otros campos vinculan emisor, destinatario y solicitud, y exp/iat/nbf limitan su validez temporal.

**¿Qué hace PKCE?**
Vincula el código de autorización con el verificador conservado por quien inició el flujo. Interceptar solo el código no alcanza para canjearlo.

**¿Por qué cambiar la sesión al ingresar?**
Para que una sesión anónima previa no mantenga el mismo identificador después de autenticar. El token CSRF también se renueva.

**¿Por qué filtros y controles en servicios?**
Los filtros adaptan el canal. Los servicios hacen cumplir permisos aunque otro canal llame directamente; pasar por una pantalla no otorga autorización.

**¿Por qué javax.naming en un proyecto Jakarta?**
JNDI es parte de Java SE y conserva ese paquete. No corresponde reemplazarlo por un inexistente jakarta.naming.

**¿Por qué una tabla de vínculos en vez de otro Ciudadano para cada función?**
Una persona tiene una identidad; las funciones son relaciones/roles, no personas duplicadas. La guía adapta la referencia de Guía para que esto sea consistente.

**¿Qué agrega WS-Addressing si ya es SOAP?**
Expresa destino, operación y relación solicitud/respuesta mediante cabeceras estándar. SOAP por sí solo no demuestra usar WS-*.

**¿Qué limita la concurrencia?**
El semáforo pone una cota por instancia y devuelve degradación si no hay cupo. El circuito evita insistir ante fallas sostenidas; los timeouts acotan cada intento. Son mecanismos distintos.

**¿La guía termina toda la arquitectura del grupo?**
No. Entrega una referencia de P1 con contratos para otros módulos. La integración y sus resultados deben demostrarse con el sistema real.

## 21. Documentación primaria
• LDAP/JNDI y timeouts: https://docs.oracle.com/javase/8/docs/technotes/guides/jndi/jndi-ldap.html
• Creación de conexiones LDAP: https://docs.oracle.com/javase/jndi/tutorial/ldap/connect/create.html
• Keycloak en contenedores: https://www.keycloak.org/server/containers
• Importación de realms: https://www.keycloak.org/server/importExport
• OIDC, código/PKCE y userinfo: https://www.keycloak.org/docs/23.0.7/securing_apps/index.html
• WS-Addressing SOAP binding: https://www.w3.org/TR/2006/REC-ws-addr-soap-20060509/
• Jakarta Persistence: https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html
• Sesiones y fijación de sesión: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

Verificar versiones y adaptar el SAD con las decisiones que el grupo apruebe. Declarar el uso de IA y comprender todo el código antes de incorporarlo. No usar esta guía como fuente técnica primaria del artículo: usar documentación y mediciones.

