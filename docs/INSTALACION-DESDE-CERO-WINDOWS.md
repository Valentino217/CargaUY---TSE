# Ejecutar CargaUY desde cero en Windows

Fecha: 22 de octubre de 2026.

Repositorio: https://github.com/Valentino217/CargaUY---TSE

Rama utilizada: **Martin**.

Esta guía utiliza WildFly y PostgreSQL instalados en la PC, y Docker para OpenLDAP y el mock PDI. Eclipse es opcional. Los comandos se ejecutan en PowerShell, salvo las instrucciones de pgAdmin.

## 1. Instalar las herramientas

- Git.
- JDK compatible con WildFly. El proyecto compila para Java 17; las verificaciones anteriores utilizaron WildFly 41 y JDK 25.
- Maven.
- PostgreSQL y pgAdmin.
- WildFly con configuración que incluya mensajería.
- Docker Desktop, iniciado.
- Eclipse Enterprise Java, si se desea desarrollar o publicar desde el IDE.

Comprobar:

```powershell
git --version
java -version
mvn -version
docker version
```

`mvn -version` muestra el JDK que utiliza Maven. Si `java` o `mvn` no se reconocen, configurar JAVA_HOME y PATH, y abrir una nueva ventana de PowerShell.

Ejemplo para establecer el JDK en la ventana actual, adaptando la ruta a la instalación propia:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 2. Descargar directamente la rama Martin

La primera descarga se hace con `clone`; `pull` actualiza una copia existente.

Elegir una carpeta, por ejemplo `C:\Proyectos`:

```powershell
New-Item -ItemType Directory -Path C:\Proyectos -Force
cd C:\Proyectos
git clone --branch Martin https://github.com/Valentino217/CargaUY---TSE.git
cd CargaUY---TSE
git branch --show-current
```

El último comando debe mostrar `Martin`.

Si el repositorio ya está descargado:

```powershell
cd C:\Proyectos\CargaUY---TSE
git status
git fetch origin
git switch Martin
git pull --ff-only origin Martin
```

Si la rama todavía no existe localmente, reemplazar `git switch Martin` por:

```powershell
git switch --track origin/Martin
```

Si hay modificaciones locales o Git rechaza la actualización, resolver ese estado antes de continuar. No usar `reset --hard` ni `push --force` como parte de esta instalación.

Desde este punto, ejecutar los comandos del proyecto en la carpeta donde está el `pom.xml` principal.

## 3. Crear la base PostgreSQL

En pgAdmin:

1. Conectarse al servidor PostgreSQL local.
2. Clic derecho en Databases → Create → Database.
3. Nombre: `cargauy`.
4. Elegir como propietario el usuario PostgreSQL que utilizará WildFly.
5. Guardar y anotar usuario, contraseña y puerto; normalmente es `5432`.

No confundir esta cuenta con el administrador de WildFly ni con el usuario LDAP de CargaUY.

## 4. Preparar el esquema: requisito previo que no debe omitirse

Los scripts disponibles incluyen:

```text
scripts/p1/migracion-02.sql
scripts/p1/migracion-03-integracion.sql
```

**Estos scripts no inicializan por sí solos una base completamente vacía.** La migración 02 comienza modificando `usuarios`; la 03 referencia tablas anteriores. Antes hace falta el esquema inicial compatible, obtenido del procedimiento de inicialización del equipo o de un respaldo de desarrollo preparado para compartir sin datos sensibles.

Si la PC parte de una base vacía y no dispone de ese esquema, este paso queda pendiente: pedir al equipo el respaldo/esquema inicial o completar su automatización. La guía no garantiza un arranque totalmente automático mientras falte ese recurso.

Una vez disponible el esquema previo:

1. Detener WildFly y respaldar la base si ya contiene información.
2. En pgAdmin, abrir Query Tool sobre `cargauy`.
3. Abrir y ejecutar `migracion-02.sql`.
4. Abrir y ejecutar `migracion-03-integracion.sql` completa.
5. Comprobar que termine en COMMIT.

Ante un error, no ejecutar fragmentos sueltos ni continuar con el despliegue. Si quedó una transacción fallida, ejecutar ROLLBACK antes de corregir y repetir. No habilitar un modo destructivo de JPA para resolver tablas ausentes.

Consultar también `docs/INTEGRACION-MARTIN-VALENTINO.md`.

## 5. Preparar WildFly y su datasource

Definir una ruta propia para WildFly. Los ejemplos siguientes usan:

```text
C:\Herramientas\wildfly-41.0.0.Final
```

Usar una configuración con el subsistema de mensajería, por ejemplo `standalone-full.xml`. Para arrancar fuera de Eclipse:

```powershell
cd C:\Herramientas\wildfly-41.0.0.Final\bin
.\standalone.bat -c standalone-full.xml
```

Mantener esa ventana abierta. Para otros comandos, abrir otra PowerShell y volver a la raíz del repositorio.

En WildFly deben quedar instalados el driver JDBC de PostgreSQL y un datasource con:

| Campo | Valor |
|---|---|
| JNDI | `java:jboss/datasources/PostgreSQLDS` |
| URL local habitual | `jdbc:postgresql://localhost:5432/cargauy` |
| Usuario | El usuario PostgreSQL de esa PC |
| Contraseña | La contraseña de ese usuario |

La consola de administración suele estar en `http://localhost:9990`. Sus credenciales son las de administración de WildFly. Si todavía no existe un administrador, crearlo con `bin\add-user.bat`, seleccionando Management User.

Registrar el driver JDBC y configurar el datasource desde la consola o con el procedimiento del equipo. La ubicación exacta de las opciones depende de la versión de WildFly. Ejecutar **Test Connection** y obtener éxito antes de publicar CargaUY.

Configurar el datasource en el mismo perfil que se va a ejecutar: modificar `standalone.xml` y luego arrancar `standalone-full.xml` no aplica esa configuración al segundo archivo.

## 6. Generar la configuración privada local

En la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\p1\preparar-local.ps1
```

Genera archivos en `.local-p1`:

| Archivo | Uso |
|---|---|
| `cuentas-locales.txt` | Consultar usuarios y contraseñas generados |
| `servicios.env` | Configuración de los contenedores |
| `wildfly.properties` | Propiedades para la aplicación |

Ejecutarlo una sola vez por entorno. Si informa que ya existe configuración local, conservarla y continuar; no borrarla para regenerar contraseñas.

Verificar que Git ignore la configuración privada:

```powershell
git check-ignore .local-p1/wildfly.properties
```

Debe devolver la ruta. No subir `.local-p1` ni compartir capturas de sus secretos.

El preparador genera configuración del simulador de identidad. Para empezar por LDAP no hace falta completar el ingreso real de gub.uy. No reemplazar propiedades del proveedor sin seguir `docs/P2/USUARIO-GUB-UY-REAL.md`.

## 7. Levantar OpenLDAP y el mock PDI

Con Docker Desktop iniciado:

```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml up -d --build ldap mock-pdi
```

Comprobar:

```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml ps
```

Ambos servicios deben aparecer iniciados. Direcciones locales esperadas:

| Servicio | Dirección |
|---|---|
| LDAP | `ldap://127.0.0.1:1389` |
| PDI | `http://localhost:8081/pdi/ws` |
| WSDL PDI | `http://localhost:8081/pdi/ws?wsdl` |

Si un contenedor se detiene:

```powershell
docker compose -p cargauy-p1 --env-file .local-p1/servicios.env -f docker-compose.p1.yml logs --tail=80 ldap mock-pdi
```

No usar `down -v` para corregir un arranque: elimina los volúmenes del entorno, incluidos los datos persistidos del directorio.

## 8. Aplicar propiedades a WildFly

Con el servidor iniciado y su administración disponible, desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\p1\configurar-wildfly.ps1 -WildFlyHome 'C:\Herramientas\wildfly-41.0.0.Final'
```

Reemplazar la ruta por la instalación propia. Para la instalación utilizada por Martin:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\p1\configurar-wildfly.ps1 -WildFlyHome 'C:\Users\Martin\OneDrive\Desktop\Facultad\TSE\wildfly-41.0.0.Final'
```

Si finaliza correctamente, reiniciar WildFly utilizando el mismo perfil. Este script no crea la base ni reemplaza la instalación del driver o la configuración del datasource.

## 9. Compilar

Desde el directorio del `pom.xml` principal:

```powershell
mvn clean verify
```

Debe terminar con `BUILD SUCCESS`. Ante un error de compilación o pruebas, resolverlo antes de publicar. Si falla el agente de Mockito, revisar la configuración del JDK/agente documentada por el equipo; omitir las pruebas no corrige la causa.

El artefacto principal es:

```text
ear\target\CargaUY.ear
```

## 10. Publicar: elegir Eclipse o copia manual

### Opción A: Eclipse

1. File → Import → Maven → Existing Maven Projects.
2. Seleccionar la raíz del repositorio e importar los módulos.
3. En Servers, configurar WildFly con el JDK y el perfil de mensajería utilizado anteriormente.
4. Add and Remove → agregar `CargaUY-ear`.
5. Iniciar y ejecutar Publish.

Después de cambiar de rama: F5 → Maven → Update Project. Si Eclipse avisa que un archivo cambió en disco, cargar la versión que se acaba de descargar antes de guardar un editor antiguo.

### Opción B: sin Eclipse

Con WildFly iniciado, copiar `ear\target\CargaUY.ear` a `standalone\deployments` de la instalación propia.

Ejemplo, desde la raíz del repositorio:

```powershell
Copy-Item .\ear\target\CargaUY.ear 'C:\Herramientas\wildfly-41.0.0.Final\standalone\deployments\CargaUY.ear'
```

Esperar el archivo `CargaUY.ear.deployed`. Si aparece `CargaUY.ear.failed`, revisar su contenido y `standalone\log\server.log`.

No administrar simultáneamente el mismo despliegue con Eclipse y con copias manuales.

## 11. Abrir CargaUY

```text
http://localhost:8080/CargaUY-web/login
```

En Administradores y autoridades, utilizar la cuenta LDAP generada. El nombre predeterminado es `martin.admin`; la contraseña se consulta en `.local-p1\cuentas-locales.txt`.

Después del login debe aparecer Inicio.

OpenLDAP comprueba la contraseña y PostgreSQL contiene el perfil y sus roles. Si la base se importó desde otra PC, comprobar que el UID LDAP coincida con la identidad del perfil. El bootstrap local no reactiva ni cambia los roles de una cuenta existente.

## 12. Comprobaciones básicas

1. Ingresar con LDAP.
2. Abrir Empresas y vínculos.
3. Consultar PDI con CI `12345678`: el mock debe devolver datos ficticios.
4. Abrir Vehículos, Guías e Infracciones y comprobar que no haya errores de tablas, columnas o servicios.
5. Cerrar sesión y volver a ingresar.

No se necesita una migración adicional de datos por la conversión de vistas JSP a Facelets.

## 13. Gub.uy: configuración independiente

El arranque básico se comprueba con LDAP. El proveedor real necesita su configuración privada y el WAR independiente del callback; no se incluyen secretos en este documento.

Si la rama descargada contiene el módulo Maven `gub-callback-root`, su WAR se genera en:

```text
gub-callback-root\target\gub-callback-root.war
```

Se publica aparte del EAR, en el mismo WildFly. Consultar `docs/JSF-FACELETS.md` y `docs/P2/USUARIO-GUB-UY-REAL.md`.

En las pruebas de la conversación, el cliente real de ID Uruguay todavía devolvía `OIDC_ERROR` antes de volver a CargaUY. Reinstalar el proyecto no acredita ni resuelve ese problema. El simulador de identidad y el proveedor real son configuraciones distintas.

## 14. Próximos arranques y actualizaciones

Para arrancar sin cambios nuevos:

1. Iniciar PostgreSQL.
2. Iniciar Docker Desktop y los servicios LDAP/PDI con el comando de la sección 7.
3. Iniciar WildFly con el mismo perfil.
4. Abrir `/CargaUY-web/login`.

Para descargar cambios nuevos:

```powershell
git status
git switch Martin
git pull --ff-only origin Martin
mvn clean verify
```

Revisar las instrucciones de la actualización: aplicar únicamente las nuevas migraciones requeridas y volver a publicar. No regenerar las credenciales ni repetir la preparación inicial en cada arranque.

## 15. Problemas frecuentes

| Problema | Qué comprobar |
|---|---|
| `git` / `mvn` no se reconoce | Instalación, PATH y nueva ventana de PowerShell |
| Test Connection falla | PostgreSQL, base, puerto, usuario, contraseña y driver |
| Tabla `usuarios` u otra tabla no existe | Falta el esquema inicial; las migraciones 02/03 no lo crean desde cero |
| Directorio temporalmente no disponible | Docker Desktop, contenedor LDAP y puerto 1389 |
| Credenciales inválidas | Cuenta generada, contraseña y perfil PostgreSQL vinculado al UID |
| Falta `TrackingQueue` o mensajería | Perfil WildFly con subsistema JMS y despliegue del EAR |
| 404 en `/CargaUY-web/login` | Contexto, despliegue exitoso y puerto de WildFly |
| PDI no disponible | Contenedor, endpoint configurado y puerto 8081 |
| Puerto ocupado | Otro WildFly, PostgreSQL o servicio usando ese puerto |

Para dejar esta instalación totalmente reproducible falta completar y versionar el esquema inicial/procedimiento de base vacía y automatizar la instalación del driver y datasource. Los pasos anteriores explicitan esos requisitos en lugar de asumir que ya existen en otra PC.
