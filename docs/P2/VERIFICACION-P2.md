# Verificación de la guía P1, parte 2
Fecha: 15 de octubre de 2026.

## Actualización para ID Uruguay real
Se consultaron las configuraciones públicas v1 y v2. Se corrigieron endpoints, HTTP Basic y claims documentales, y se agregó un WAR independiente para recibir el retorno en http://localhost:8080.

Los ejemplos Java actualizados compilaron con --release 17. Pasaron cuatro métodos JUnit OIDC: los dos existentes de firma/claims y dos nuevos con proveedor HTTP temporal y credenciales ficticias. Se verificaron HTTP Basic, redirect original en el canje, documento normalizado, correo, ausencia de nacimiento, separación por issuer y bloqueo del móvil sin habilitación.

La lógica Java extraída de index.jsp compiló y pasó comprobaciones de destino fijo, codificación de parámetros, state mal formado, éxito/rechazo y cabeceras. Los descriptores XML y la sintaxis PowerShell del empaquetador también se comprobaron. Esto no sustituye desplegar/renderizar el WAR en WildFly.

No se usó el secreto suministrado para autenticar, no se hizo login con una cuenta personal de testing y no se desplegó el receptor raíz en tu servidor. Quedan por confirmar los códigos de país/tipo de documento, datos consentidos para este cliente y soporte PKCE. El alta real se realiza mediante perfil completo y vinculación administrativa; el autoregistro con pantalla de completar datos y el cierre SSO siguen pendientes. El móvil real está bloqueado por defecto.

## Resultado inicial anterior a esta actualización
• Los ejemplos Java compilan con javac --release 17.
• Se ejecutaron 47 métodos de prueba con aserciones JUnit y Mockito; todos pasaron. El ejecutor de verificación respetó BeforeEach/AfterEach de los tests existentes. No fue un clean package del proyecto activo.
• Se volvieron a ejecutar los 11 tests de servicios después de ajustar compatibilidad con proxies JPA y tratamiento HTTP de errores; pasaron.
• Los fragmentos Java de los dos JSP se extrajeron y compilaron para detectar errores de sintaxis. Esto no es renderizado real con Jasper/Undertow.
• Sintaxis PowerShell/Python y XML del WSDL comprobados. Compose local y overlay TLS pasan config --quiet.
• Imagen LDAP construida con Debian bookworm-slim/OpenLDAP. En un contenedor temporal se verificaron bind correcto, contraseña incorrecta y directorio inaccesible con el cliente Java de la guía.
• Keycloak 26.4.7 temporal: import del realm generado, login del fixture, código con PKCE, firma RS256, issuer, audiencia, nonce y userinfo verificados por Java.
• Nuevo mock Python y cliente Java interoperan por HTTP: DISPONIBLE, NO_ENCONTRADO por SOAP Fault y cabeceras WS-Addressing. La correlación coincidió en logs de ambos componentes.
• Las pruebas unitarias también verificaron rechazo de XML con DTD, relación de otra solicitud, límites de reintentos/circuito y admisión concurrente acotada.
• El flujo móvil tiene pruebas de desafío PKCE, expiración, state desconocido y consumo transaccional simulado.

## Alcance
No se aplicaron migraciones a cargauy ni se modificó el código activo de ejb/web o la configuración real de WildFly.
No se probaron en ese servidor los mapeos JPA de la nueva versión, bloqueos, transacciones de base, importación del EAR o renderizado de las nuevas pantallas.
No se verificó TLS extremo a extremo, escalado, p95 de toda la plataforma, cobertura objetivo, móvil real ni el motor completo de fiscalización.
El overlay TLS está preparado para certificados del equipo; no los genera y no contiene claves privadas.
Los contenedores temporales se eliminaron en finally. Las imágenes LDAP de verificación y Keycloak descargadas permanecen en la caché de Docker.
Las credenciales temporales de verificación se eliminan con la carpeta de trabajo de pruebas.

## Cómo reproducir en el proyecto
Seguir GUIA-P1-PARTE-2-LDAP.md: copiar código y tests a sus destinos, migrar la base con respaldo, configurar proveedores y propiedades y ejecutar clean package desde Eclipse.
No confundir los resultados de los ejemplos con una validación automática de la instalación final.
