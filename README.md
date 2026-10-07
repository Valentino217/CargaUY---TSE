# carga.uy (Grupo 07, TSE 2026)

EAR Jakarta EE 10 (`ejb` + `web` + `ear`) sobre **WildFly 41** + **PostgreSQL** + **JMS (ActiveMQ Artemis)**.

---

## Opción A: Docker (un solo comando)

Requiere Docker Desktop **corriendo**.

```bash
docker compose up --build
```

El EAR se compila dentro de la imagen (Dockerfile multi-stage); no hace falta correr Maven antes.

| Servicio | URL |
|---|---|
| Aplicación | http://localhost:8080/CargaUY-web/ |
| Consola de WildFly | http://localhost:9990 |
| Mock PDI | http://localhost:8081 |
| Mock balanza | http://localhost:8082 |
| PostgreSQL | `localhost:5432`, BD/usuario `cargauy`, contraseña `cargauy123` |

Para empezar con la BD vacía: `docker compose down -v`.

---

## Opción B: WildFly local (Eclipse / Windows)

### 1. Base de datos (una sola vez)

Con `psql` como superusuario (`postgres`):

```sql
CREATE ROLE cargauy WITH LOGIN PASSWORD 'cargauy123';
CREATE DATABASE cargauy OWNER cargauy;
\c cargauy
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

### 2. Driver JDBC

Copiar `postgresql-42.7.x.jar` a `<WILDFLY>/standalone/deployments/` (queda registrado como driver `postgresql-42.7.x.jar`).

### 3. Datasource y colas JMS (una sola vez, con WildFly levantado)

Arrancar WildFly con el perfil **full** (incluye JMS): `standalone.bat -c standalone-full.xml`.
En Eclipse: doble clic en el servidor → *Open launch configuration* → cambiar `--server-config=standalone.xml` por `standalone-full.xml`.

Guardar esto como `setup-local.cli` (ajustando el nombre del driver a la versión que copiaste):

```text
data-source add --name=PostgreSQLDS --jndi-name=java:jboss/datasources/PostgreSQLDS --driver-name=postgresql-42.7.3.jar --connection-url=jdbc:postgresql://localhost:5432/cargauy --user-name=cargauy --password=cargauy123 --min-pool-size=5 --max-pool-size=25

jms-queue add --queue-address=TrackingQueue --entries=[java:/jms/queue/TrackingQueue,java:jboss/exported/jms/queue/TrackingQueue]
jms-queue add --queue-address=FiscalizacionQueue --entries=[java:/jms/queue/FiscalizacionQueue,java:jboss/exported/jms/queue/FiscalizacionQueue]
jms-queue add --queue-address=NotificacionQueue --entries=[java:/jms/queue/NotificacionQueue,java:jboss/exported/jms/queue/NotificacionQueue]
jms-queue add --queue-address=DLQ.TrackingQueue --entries=[java:/jms/queue/DLQ.TrackingQueue]
```

y ejecutar:

```powershell
$env:NOPAUSE="true"
<WILDFLY>\bin\jboss-cli.bat --connect --file=setup-local.cli
<WILDFLY>\bin\jboss-cli.bat --connect --command="/subsystem=datasources/data-source=PostgreSQLDS:test-connection-in-pool"
```

> Si usás `standalone.xml` (sin JMS), las colas no se pueden crear; alcanza mientras nadie use JMS, pero el perfil correcto es `standalone-full.xml`.

### 4. Desplegar

```bash
mvn clean package
```

y publicar desde Eclipse, o copiar `ear/target/CargaUY.ear` a `deployments/`.

---

## Build, tests y cobertura

```bash
mvn clean verify          # compila, corre tests, genera el reporte JaCoCo y aplica jacoco:check
```

- Reporte: `ejb/target/site/jacoco/index.html`
- Umbral mínimo de cobertura: propiedad `jacoco.minimum.coverage` en el `pom.xml` raíz (meta final: `0.80`).
- El CI (`.gitlab-ci.yml`) corre `build` → `test` (`mvn verify`) → `quality` (Sonar) → `package`.

## Auditoría inmutable

`registro_auditoria` es append-only: al desplegar, `AuditoriaTriggerInstaller` (EJB `@Startup`) instala un trigger que rechaza `UPDATE` y `DELETE`. Se reinstala en cada deploy porque `drop-and-create` recrea la tabla.
