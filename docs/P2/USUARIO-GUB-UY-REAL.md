# Usuario gub.uy real · Ajustes a la segunda guía

Actualizado: 15 de octubre de 2026. Este documento reemplaza las instrucciones del simulador cuando se utiliza el cliente de testing entregado por el curso. Los ejemplos siguen estando en docs/P2/ejemplos: copiarlos a sus destinos para incorporarlos al proyecto. No se modificó automáticamente tu aplicación ni tu servidor.

## 1. Qué cambia y qué se conserva

El administrador de pruebas martin.admin sigue entrando mediante LDAP. Ciudadanos, choferes y funcionarios utilizan ID Uruguay. El client_id identifica a CargaUY, no a una persona; su secreto tampoco es la contraseña de un ciudadano. Para completar una prueba se necesita además una cuenta personal válida en el entorno de testing, que no fue proporcionada.

Keycloak queda como alternativa de práctica sin conexión con ID Uruguay. El script preparar-local.ps1 genera únicamente esa alternativa y las credenciales LDAP. Después de ejecutarlo, aplicar las propiedades de la sección 3 para seleccionar ID Uruguay. No introducir las credenciales reales en el realm de Keycloak, en servicios.env, en código Java ni en Docker Compose.

Los archivos modificados respecto de la primera versión de la segunda guía son GubOidcClient.java, GubServlet.java, SesionesService.java, FlujosService.java, login.jsp y preparar-local.ps1. El anexo CODIGO-P2.md incluye sus versiones actualizadas.

## 2. La dirección de retorno: por qué hace falta un WAR adicional

Tenemos que utilizar únicamente las URI ya registradas. Elegimos exactamente:

~~~text
http://localhost:8080
~~~

No sustituirla por http://localhost:8080/CargaUY-web/gub/callback ni por http://localhost:8080/ en la configuración de autorización o del intercambio de código. Tampoco usar 127.0.0.1 como equivalente de localhost. La URI debe coincidir con la registrada.

CargaUY sigue publicado bajo /CargaUY-web. Para recibir la respuesta en la raíz, se incorpora una pequeña aplicación independiente, gub-callback-root.war, con contexto /. No se cambia el contexto del EAR, los enlaces de la aplicación ni las cookies de sesión.

El recorrido es:

~~~text
/CargaUY-web/gub/iniciar
    → ID Uruguay
    → http://localhost:8080?code=...&state=...
    → /CargaUY-web/gub/callback?code=...&state=...
    → perfil de CargaUY
~~~

El WAR de retorno solo retransmite code y state a un destino fijo en el mismo servidor. CargaUY verifica state contra la sesión original y luego canjea el código enviando redirect_uri=http://localhost:8080, que es la dirección original de la autorización. El secreto nunca pasa por el WAR de retorno ni por el navegador.

La sesión original se recupera cuando el navegador vuelve a /CargaUY-web. El WAR raíz tiene session=false para evitar crear otra cookie JSESSIONID. Responde con no-store y Referrer-Policy: no-referrer; no imprime el código ni lo incorpora a una página con recursos externos. No activar logs HTTP que registren query strings en estos endpoints.

No usar openidconnect.net como intermediario para el login de CargaUY: sirve para pruebas independientes del proveedor. No copiar códigos de autorización manualmente desde esa página a nuestra aplicación.

### Archivos nuevos

Copiar desde ejemplos a la raíz del repositorio, conservando estas rutas:

~~~text
gub-callback-root/src/main/webapp/index.jsp
gub-callback-root/src/main/webapp/logout.jsp
gub-callback-root/src/main/webapp/WEB-INF/web.xml
gub-callback-root/src/main/webapp/WEB-INF/jboss-web.xml
scripts/p1/empaquetar-callback.ps1
~~~

En web.xml, cargauy.context debe tener el contexto real de tu aplicación: /CargaUY-web. Si se publica como /cargauy, cambiar solamente ese parámetro. El redirect registrado sigue siendo http://localhost:8080.

Desde PowerShell, ubicado en la raíz del repositorio:

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/p1/empaquetar-callback.ps1
~~~

El resultado es target/p1-callback/gub-callback-root.war. El script no necesita Maven y no despliega nada; empaqueta JSP y descriptores. Si ya existe el WAR, pide quitar o renombrar ese archivo antes de volver a generarlo.

Con WildFly iniciado, desplegarlo desde su consola de administración:

1. Abrir http://localhost:9990 y entrar con el usuario de administración de WildFly.
2. Entrar en Deployments → Add → Upload Deployment.
3. Elegir target/p1-callback/gub-callback-root.war.
4. Terminar el asistente y habilitar el deployment si no quedó habilitado.
5. Abrir http://localhost:8080: debería mostrar el enlace Ingresar a CargaUY.

Este WAR se despliega además del EAR. Si la raíz ya pertenece a otra aplicación, no reemplazarla: hay que integrar allí el receptor fijo o acordar cómo liberar ese contexto. Si aparece la bienvenida de WildFly, revisar que el WAR esté habilitado, su descriptor indique / y que se esté usando el mismo servidor/puerto que CargaUY.

## 3. Configuración privada de WildFly

Primero asegurarse de que .gitignore contenga /.local-p1/. Si ya ejecutaste preparar-local.ps1, no lo vuelvas a ejecutar: conserva las cuentas LDAP. Abrí .local-p1/wildfly.properties y reemplazá las propiedades gub.* del simulador por:

~~~properties
gub.proveedor=iduruguay
gub.issuer=https://auth-testing.iduruguay.gub.uy/oidc/v1
gub.jwks=https://auth-testing.iduruguay.gub.uy/oidc/v2/jwks
gub.client.id=890192
gub.client.secret=REEMPLAZAR_SOLO_EN_ESTE_ARCHIVO_PRIVADO
gub.redirect=http://localhost:8080
gub.pkce=false
gub.mobile.enabled=false
gub.document.pais=CONFIRMAR_VALOR_DEL_PROVEEDOR
gub.document.tipo=CONFIRMAR_VALOR_DEL_PROVEEDOR
~~~

Pegar el secreto recibido del curso únicamente en el valor privado de gub.client.secret, sin comillas. No dejar el marcador de ejemplo. No compartir capturas del archivo ni subirlo al repositorio.

pais y tipo son los valores exactos de pais_documento y tipo_documento para una CI uruguaya en este proveedor. La documentación enumera los campos, pero no fija sus códigos en el material consultado: confirmarlos con el docente/administrador del entorno antes de completar estos dos valores. No dar por hecho UY/CI, 858 o nombres en español. Mantener los marcadores bloquea el alta de documentos en lugar de aceptar uno incorrecto. Si falta el scope document para este cliente, también hay que solicitar su habilitación; escribirlo en la URL no concede el permiso.

Para aplicar el archivo, usar configurar-wildfly.ps1 con el mismo WildFlyHome de la sección 5 de la guía principal, y reiniciar el servidor. No poner el secreto en VM arguments ni en una línea de comandos. No pegarlo en herramientas web para comprobarlo.

Verificar la exclusión antes de versionar:

~~~powershell
git check-ignore .local-p1/wildfly.properties
~~~

Debe devolver la ruta. Si no devuelve nada, corregir .gitignore antes de agregar archivos a Git. Si algún archivo privado ya estaba versionado, ignorarlo no lo elimina del historial: revisar ese caso por separado.

### Endpoints públicos comprobados

Se consultaron las configuraciones v1 y v2 del entorno indicado:

| Función | URL |
|---|---|
| Issuer declarado en ambas configuraciones | https://auth-testing.iduruguay.gub.uy/oidc/v1 |
| Autorizar | https://auth-testing.iduruguay.gub.uy/oidc/v1/authorize |
| Canjear código | https://auth-testing.iduruguay.gub.uy/oidc/v1/token |
| Datos personales | https://auth-testing.iduruguay.gub.uy/oidc/v1/userinfo |
| Claves de firma v2 | https://auth-testing.iduruguay.gub.uy/oidc/v2/jwks |
| Cierre de sesión del proveedor | https://auth-testing.iduruguay.gub.uy/oidc/v1/logout |

Usar las claves v2 no significa inventar issuer=/oidc/v2. La configuración pública v2 consultada sigue declarando issuer=/oidc/v1. Se mantiene la comparación exacta del issuer del ID token; si cambia el contrato, revisar metadata y token con el proveedor, sin desactivar esa validación.

El cliente de ejemplo acepta exclusivamente RS256. Si la inscripción del cliente estuviera configurada para HS256, pedir RS256 o incorporar de manera explícita una implementación revisada para ese algoritmo. No aceptar cualquier algoritmo indicado por el token.

## 4. Qué cambia en el código y por qué

GubOidcClient selecciona las rutas según gub.proveedor. Con iduruguay utiliza /authorize, /token, /userinfo y el JWKS configurado; con keycloak conserva las rutas de ese simulador. El canje de código autentica al cliente mediante Authorization: Basic, por HTTPS. Así coincide con el contrato publicado de ID Uruguay y evita seguir enviando el secreto como parámetro del formulario.

GubServlet sigue generando state y nonce aleatorios, los conserva en la sesión y acepta un único retorno durante tres minutos. Valida state antes de procesar éxito o rechazo. La aplicación valida firma, issuer, audiencia, nonce, tiempos y coincidencia de sub entre ID token y userinfo. El receptor raíz no sustituye ninguna de esas validaciones.

Los scopes solicitados al proveedor real son openid profile personal_info email document. El perfil obtiene nombre y correo; el documento usa numero_documento, pais_documento y tipo_documento. Los campos ci y fechaNacimiento pertenecían al fixture de Keycloak, no al contrato real.

La identidad externa real se guarda como SHA-256 de issuer + salto de línea + sub. Esto separa proveedores aunque emitan el mismo identificador. Es una clave de vinculación, no una contraseña ni un token de acceso. En el panel administrativo, el campo UID externo para GUB recibe esa referencia de 64 caracteres; no el uid de personal_info, el correo, la CI ni el client_id. Las cuentas del simulador conservan su subject anterior.

### Fecha de nacimiento y primer acceso

El proveedor no anuncia fecha de nacimiento en los scopes consultados. El modelo de esta guía la exige para crear un perfil, por lo que el ejemplo no completa ese dato con una fecha inventada ni con el mock PDI.

Para probar ahora:

1. Entrar como administrador LDAP y crear el perfil CIUDADANO con datos completos aportados por la persona, incluida su fecha de nacimiento.
2. Intentar ingresar con la cuenta de ID Uruguay correspondiente.
3. Después de validar la identidad, aparecerá un conflicto con una Referencia de vinculación. Copiar esa referencia; no copiar tokens ni el secreto.
4. Desde la sesión del administrador, vincular ese perfil con origen GUB y la referencia mostrada.
5. Volver a iniciar el flujo desde el botón Usuario gub.uy, obteniendo un código nuevo.
6. La aplicación exige que la CI del perfil coincida con la CI verificada antes de emitir su sesión.

Si no se creó antes el perfil, el mensaje indica que hay que crearlo y vincularlo. Esto permite la prueba con el modelo actual, pero el autoregistro real con pantalla de completar datos sigue pendiente. La vinculación administrativa debe seguir una comprobación de identidad del grupo; no asociar una referencia recibida de un tercero sin verificar su procedencia.

El autoregistro del fixture de Keycloak sigue disponible porque su contrato de prueba sí incluye fechaNacimiento. Esa facilidad no acredita el autoregistro con ID Uruguay.

### PKCE y móvil

La metadata consultada no anuncia code_challenge_methods_supported y el documento suministrado no describe PKCE. Eso no prueba que el servidor lo rechace, pero tampoco permite afirmar que lo implementa. El ejemplo web usa el flujo de cliente confidencial documentado, con secreto exclusivamente en backend, state y nonce; se configura gub.pkce=false de forma explícita para este proveedor.

No declarar PKCE verificado en ID Uruguay por haberlo probado en Keycloak. Si el curso confirma S256, cambiar a gub.pkce=true y probar aceptación de un verificador correcto y rechazo de uno incorrecto.

El callback móvil uy.cargauy:/oauth/callback no está registrado. Por eso los endpoints móviles quedan bloqueados para iduruguay. No incluir el client_secret en una app móvil. Para habilitar ese flujo hace falta un diseño de retorno permitido para el móvil y confirmar PKCE; con la lista actual de URI no se puede prometer que el ejemplo móvil anterior funcione directamente.

## 5. Logout: dos sesiones distintas

El botón Salir del código actual revoca el token local e invalida la sesión de CargaUY. La sesión SSO de ID Uruguay puede seguir abierta: al volver a entrar, el proveedor podría no pedir contraseña.

El endpoint de logout del proveedor exige id_token_hint. El ejemplo actual no conserva el ID token para ese propósito y no implementa todavía el cierre SSO. No redirigir simplemente a /oidc/v1/logout sin ese dato ni afirmar que el cierre global está terminado.

La URI ya autorizada http://localhost:8080/logout se encuentra atendida por el WAR raíz y puede servir de retorno para una futura implementación del cierre SSO. Abrir esa página por sí sola no cierra ninguna sesión. /CargaUY-web/logout sigue siendo el POST protegido con CSRF que ejecuta el cierre local.

Para completar el cierre SSO, conservar el ID token solamente en la sesión del backend durante el login web; ante el POST Salir, revocar primero la sesión local y redirigir al end_session_endpoint con id_token_hint, post_logout_redirect_uri exacto y state de retorno de un solo uso. Añadir la validación de ese state en el receptor del retorno. No persistir ni exponer esos tokens en logs. Esta ampliación queda identificada como pendiente y requiere su prueba real.

## 6. Orden de prueba y errores esperables

1. Probar el administrador LDAP; no depende de gub.uy.
2. Publicar EAR actualizado y WAR raíz. Comprobar que http://localhost:8080 muestre el enlace.
3. Confirmar propiedades privadas, códigos de documento y disponibilidad de una cuenta personal de testing.
4. Abrir http://localhost:8080/CargaUY-web/login y elegir Usuario gub.uy.
5. Verificar que el navegador vaya al dominio auth-testing.iduruguay.gub.uy.
6. Autenticarse y consentir compartir los datos.
7. Confirmar el paso por localhost:8080 y el regreso al callback de CargaUY.
8. Completar la vinculación administrativa si corresponde; repetir el ingreso con un flujo nuevo.
9. Probar rechazo del consentimiento, state alterado, perfil inactivo y logout local.

| Resultado | Qué revisar |
|---|---|
| redirect_uri inválida | Debe enviarse exactamente http://localhost:8080, tanto al autorizar como al canjear. |
| Bienvenida de WildFly al volver | El receptor raíz no está habilitado o pertenece a otra instancia. |
| Error 401 por state | Iniciar desde CargaUY en la misma pestaña; no mezclar localhost con 127.0.0.1 ni reutilizar códigos. |
| Documento incompatible | Confirmar scopes y códigos de país/tipo; no eliminar esa validación. |
| Conflicto con Referencia | Crear/completar el perfil y vincularlo desde administración LDAP. |
| Perfil externo incompleto | Revisar nombre, correo verificado y datos realmente consentidos. |
| Firma/issuer rechazados | Revisar contrato del proveedor, RS256 y JWKS; no omitir la validación. |
| No vuelve a pedir contraseña tras Salir | Se cerró CargaUY; el cierre SSO está pendiente. |

No se utilizó el secreto entregado para realizar un login remoto ni se autenticó una cuenta personal. Consultar metadata pública y compilar el cliente no equivale a una prueba completa con ID Uruguay.

## Fuentes

- [Documentación técnica oficial de ID Uruguay](https://www.gub.uy/agencia-gobierno-electronico-sociedad-informacion-conocimiento/comunicacion/publicaciones/documentacion-tecnica-id-uruguay/documentacion-tecnica-id-uruguay/id-0).
- [Configuración pública v1 entregada por el curso](https://auth-testing.iduruguay.gub.uy/oidc/v1/.well-known/openid-configuration).
- [Configuración pública v2 mencionada por Agesic](https://auth-testing.iduruguay.gub.uy/oidc/v2/.well-known/openid-configuration).
- [Ejemplo Python entregado como referencia](https://github.com/franps/oidc-client). Es material de consulta; el proyecto sigue siendo Java/WildFly.

