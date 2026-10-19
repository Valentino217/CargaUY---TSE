# Archivos de la segunda guía

Los destinos son relativos a la raíz del proyecto. Los archivos preparados están bajo ejemplos/ con esa misma estructura. Para ID Uruguay real leer USUARIO-GUB-UY-REAL.md e incluir el WAR de retorno raíz.

| Destino | Finalidad |
|---|---|
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Config.java` | Resuelve propiedades de WildFly o variables de entorno; evita credenciales en el código. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/ErrorAcceso.java` | Representa 401, 403 y conflictos de identidad; pide rollback de la operación. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Cripto.java` | Genera tokens aleatorios, sus hashes y el desafío PKCE; compara valores sensibles. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/Acceso.java` | DTO de identidad y roles actuales; no contiene contraseña ni credenciales de proveedor. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/SesionAcceso.java` | Guarda solo SHA-256 del token, el usuario y el vencimiento en PostgreSQL. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/VinculoEmpresa.java` | Representa funciones de responsable y chofer en una empresa sin crear dos personas con la misma CI. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/LdapClient.java` | Hace bind LDAP con el usuario final, rechaza password vacío y distingue credencial inválida de caída del directorio. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/SesionesService.java` | Emite, valida, revoca y limpia sesiones; consulta el estado y los roles del usuario en cada validación. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/ControlAcceso.java` | Autoriza en negocio por rol y por vínculo con la empresa. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/AutenticacionService.java` | Coordina LDAP y gub.uy sin fallback de contraseñas; expone login, validarToken y logout. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/GubOidcClient.java` | Intercambia el código OIDC, valida firma RS256, issuer, audiencia, nonce y fechas; verifica userinfo. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/BootstrapAdmin.java` | Crea únicamente el perfil local del administrador de pruebas si fue habilitado; no guarda ni cambia su password LDAP. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/IdentidadesService.java` | Permite a administradores vincular proveedores y asignar roles; gestiona vínculos de responsables y choferes. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Usuario.java` | Agrega roles e identidad de proveedor, expone versión y mueve la relación de guías al usuario canónico. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Empresa.java` | Expone la versión usada para detectar formularios desactualizados. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/Chofer.java` | Conserva su subtipo por compatibilidad; hereda la relación de guías desde Usuario. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/GuiaDeViaje.java` | Permite que el chofer sea un Usuario con rol CHOFER; exige validar esa función en negocio. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/UsuarioResumen.java` | Devuelve roles y versión sin devolver password ni UID externo. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/dto/EmpresaResumen.java` | Incluye versión. Su lista ciudadanos conserva la asociación antigua; para el vínculo canónico usar listarVinculos. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesService.java` | Reemplaza las firmas inseguras por operaciones que exigen token; agrega paginación y comprobación de versiones. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaService.java` | Exige permisos para catálogos y reglas; las escrituras requieren administrador. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/FuncionarioEJB.java` | Retira la antigua vía de alta sin identidad. Conserva la interfaz por compatibilidad de compilación. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/WebSeguridad.java` | CSRF, rotación de sesión, límites de transporte y cabeceras de respuesta. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/ConfiguracionWeb.java` | Configura cookies HttpOnly/SameSite y evita IDs de sesión en URLs. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/SesionFilter.java` | Protege las pantallas autenticadas y verifica CSRF en operaciones de escritura. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/LoginServlet.java` | Presenta el login interno y autentica con LDAP. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/GubServlet.java` | Inicia OIDC y valida state; selecciona ID Uruguay o Keycloak y usa PKCE únicamente cuando está configurado. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/LogoutServlet.java` | Revoca el token y destruye la sesión web mediante POST protegido por CSRF. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/AplicacionRest.java` | Publica REST bajo /api. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/AuthFilter.java` | Valida bearer y establece el contexto REST; no acepta una cookie como sustituto del bearer. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/ApiP1.java` | Login LDAP, login GUB móvil, identidad, logout, listado de empresas y health checks. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/ErroresApi.java` | Traduce errores de autorización a respuestas HTTP. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/integration/ClientePdi.java` | Cliente SOAP 1.1 y WS-Addressing, cuatro llamadas concurrentes, reintento acotado y circuito con prueba de recuperación. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ConsultaCiudadanoService.java` | Aplica permisos antes de consultar datos personales mediante PDI. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/ReglasLecturaService.java` | Lee la configuración actual para el motor sin conservar una copia estática desactualizada. |
| `docker/mock-pdi/mock_pdi.py` | Simulador con catálogo, Fault de ciudadano inexistente, validación de Addressing, logs y TLS opcional. |
| `docker/mock-pdi/pdi.wsdl` | Contrato SOAP y cabeceras WS-Addressing; declara respuestas y fallas. |
| `docker/mock-pdi/Dockerfile` | Archivo de referencia de la segunda guía; ver su sección en la guía principal. |
| `docker/ldap/Dockerfile` | Archivo de referencia de la segunda guía; ver su sección en la guía principal. |
| `docker/ldap/iniciar.sh` | Inicializa el directorio LDAP, almacena hashes y arranca slapd; LDAPS opcional con certificados suministrados. |
| `docker-compose.p1.yml` | Levanta solo LDAP, Keycloak y PDI en puertos de loopback, sin reemplazar WildFly/PostgreSQL. |
| `scripts/p1/preparar-local.ps1` | Genera contraseñas y secreto OIDC aleatorios una sola vez en una carpeta ignorada por Git. |
| `scripts/p1/configurar-wildfly.ps1` | Aplica propiedades con la CLI de WildFly; reemplaza el acceso problemático de Eclipse a VM arguments. |
| `scripts/p1/migracion-02.sql` | Extiende el esquema sin borrar usuarios/empresas; cambia la FK de guía al usuario canónico. |
| `.gitignore.fragment` | Una línea para incorporar al .gitignore real antes de generar secretos. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/PanelServlet.java` | Adapta los formularios de administración, empresas y perfil a los servicios protegidos. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/P1PruebasServlet.java` | Devuelve 410 para retirar la pantalla de pruebas anterior. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/FuncionarioServlet.java` | Devuelve 410 para retirar el formulario anterior que creaba funcionarios sin autorización. |
| `web/src/main/webapp/WEB-INF/p2/login.jsp` | Entrada para administradores/autoridades LDAP y usuarios externos con Usuario gub.uy. |
| `web/src/main/webapp/WEB-INF/p2/panel.jsp` | Vistas de administración, empresas y perfil con operaciones condicionadas por rol; los servicios vuelven a autorizar. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/CorrelacionFilter.java` | Propaga X-Correlation-ID y registra duración/estado de solicitudes sin registrar tokens o passwords. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/OperacionesApi.java` | Métricas por instancia, visibles a administradores. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/CiudadanosApi.java` | Consulta PDI autorizada y conserva la correlación entre cliente, central y mock. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/SeguridadP2Test.java` | Tokens, logout, estado del usuario, roles, pertenencia y ausencia de fallback ante caída de LDAP. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPerfilesServiceTest.java` | Versión actualizada de los tests de la guía 1, con seguridad inyectada. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/GestionPlataformaServiceTest.java` | Versión actualizada de los tests de reglas. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/integration/ClientePdiTest.java` | WS-Addressing, inexistencia, XML inseguro, reintentos y saturación. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/OidcP2Test.java` | Rechaza alg=none y comprueba firmas y claims con claves RSA de prueba. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/EvaluadorToleranciasService.java` | Aplica las tolerancias actuales en kg y km en cada evaluación; no crea ni tramita casos. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/service/EstadoService.java` | Comprueba disponibilidad de la base para readiness. |
| `docker-compose.p1.tls.yml` | Overlay TLS con certificados provistos por el equipo; no genera ni desactiva validación de certificados. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/domain/entity/FlujoIdentidad.java` | Conserva state, desafío PKCE y nonce para el login móvil de un solo uso. |
| `ejb/src/main/java/uy/edu/fing/grupo07/CargaUY/security/FlujosService.java` | Prepara y consume de forma transaccional el flujo móvil; expira a los tres minutos. |
| `scripts/p1/probar-api.ps1` | Verifica login, bearer, PDI, ciudadano inexistente y revocación. |
| `scripts/p1/carga-pdi.ps1` | Genera una prueba reproducible del componente PDI y reporta estados y p95. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/FlujosP2Test.java` | PKCE, expiración y consumo de un flujo móvil. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/service/EvaluadorToleranciasServiceTest.java` | Comprueba que una nueva evaluación use las tolerancias actualizadas. |
| `web/src/main/java/uy/edu/fing/grupo07/CargaUY/web/api/NegocioApi.java` | Traduce validaciones de negocio a HTTP 400 sin exponer detalles de infraestructura. |
| `gub-callback-root/src/main/webapp/index.jsp` | Recibe el callback en la URI raíz registrada y lo retransmite a un destino fijo; no crea sesión ni intercambia tokens. |
| `gub-callback-root/src/main/webapp/logout.jsp` | Página de retorno en /logout; no equivale a ejecutar el cierre local ni el cierre SSO. |
| `gub-callback-root/src/main/webapp/WEB-INF/web.xml` | Archivo de referencia de la segunda guía; ver su sección en la guía principal. |
| `gub-callback-root/src/main/webapp/WEB-INF/jboss-web.xml` | Archivo de referencia de la segunda guía; ver su sección en la guía principal. |
| `scripts/p1/empaquetar-callback.ps1` | Empaqueta el receptor raíz como WAR independiente, sin modificar el EAR. |
| `ejb/src/test/java/uy/edu/fing/grupo07/CargaUY/security/IdUruguayP2Test.java` | Prueba el canje HTTP Basic con credenciales ficticias, claims documentales, identidad separada por issuer, nacimiento ausente y bloqueo móvil. |

