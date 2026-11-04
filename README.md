# carga.uy (Grupo 07 - TSE 2026)

Plataforma de gestión, fiscalización y seguimiento de transporte de carga terrestre para el Ministerio de Transporte y Obras Públicas (MTOP - Uruguay).

Arquitectura empresarial **Jakarta EE 10** (`ejb` + `web` + `ear`) sobre **WildFly 41**, **PostgreSQL**, mensajería asíncrona **JMS (ActiveMQ Artemis)**, **MongoDB** (tracking NoSQL), **OpenLDAP** y **Keycloak** (simulador Usuario gub.uy con OIDC/PKCE).

---

## 🚀 Guía de Inicio Rápido (Paso a Paso)

### ⚠️ Requisitos previos y advertencias importantes

Antes de levantar el proyecto, verificá estos 3 puntos para evitar conflictos de puertos o bloqueos:

1. **Docker Desktop corriendo:** Debe estar abierto y en verde (*Engine running*).
2. **PostgreSQL local detenido:** Si tenés PostgreSQL instalado como servicio de Windows en tu máquina, **debés detenerlo** (desde `services.msc` → botón derecho en el servicio de PostgreSQL → *Detener*). De lo contrario, ocupará el puerto `5432` y Docker no podrá iniciarse.
3. **No duplicar WildFly:** Si vas a correr WildFly localmente en Eclipse o consola, no levantes el perfil `app` de Docker para que no choquen en el puerto `8080`.

---

### Paso 1: Levantar toda la plataforma con Docker (Todo en uno)

En la raíz del proyecto, ejecutá:

```bash
docker compose up -d
```
*(Si modificaste código Java y querés que Docker recompile la imagen de WildFly, usá `docker compose up --build -d`).*

Este comando levanta automáticamente toda la plataforma con sus puertos mapeados:

| Servicio | URL / Puerto | Descripción |
|---|---|---|
| **WildFly (CargaUY)** | `http://localhost:8080/CargaUY-web/` | Servidor de aplicaciones Jakarta EE con el EAR desplegado. |
| **PostgreSQL** | `localhost:5432` | Base de datos relacional principal (`cargauy` / `cargauy123`). |
| **OpenLDAP** | `localhost:1389` | Directorio de identidades para funcionarios y administradores. |
| **Keycloak (gub.uy)** | `http://localhost:8083` | Proveedor OIDC simulador de Usuario gub.uy (Realm `cargauy-test`). |
| **MongoDB** | `localhost:27017` | Almacén de series temporales para posiciones de tracking. |
| **Mock PDI (SOAP)** | `http://localhost:8081` | Web Service SOAP simulador de DNIC / Dirección Nacional de Identificación Civil. |
| **Mock Balanza (REST)** | `http://localhost:8082` | API REST simuladora de balanzas y pesajes en ruta. |

> 💡 **¿Preferís programar y debuggear en Eclipse / Windows con tu WildFly local?**
> Si querés usar tu propio WildFly en Windows (para debuggear con breakpoints o deploy instantáneo desde el IDE), simplemente apagá el contenedor de WildFly con:
> ```bash
> docker compose stop wildfly
> ```
> y levantás tu WildFly en Windows (`standalone.bat -c standalone-full.xml`) sin que choquen en el puerto 8080.

---

### Paso 2: Cargar datos de prueba (Seed Data)

Una vez que la aplicación arrancó y creó las tablas en PostgreSQL, cargá los datos iniciales de prueba (empresas transportistas, vehículos con habilitaciones ITV/PNC, tipos de carga, guías de viaje e incidentes de fiscalización). Podés hacerlo con el script helper o con el comando directo:

* **Opción fácil (Script en PowerShell):**
  ```powershell
  ./scripts/cargar-datos-prueba.ps1
  ```
* **Comando directo (PowerShell):**
  ```powershell
  Get-Content scripts/seed-datos-prueba.sql -Raw -Encoding UTF8 | docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy
  ```
* **Comando directo (Bash / Linux / Mac / CMD):**
  ```bash
  docker exec -i cargauy-p1-postgres-1 psql -U cargauy -d cargauy < scripts/seed-datos-prueba.sql
  ```

---

### Paso 3: Ingresar a la plataforma

Accedé en tu navegador a:
👉 **`http://localhost:8080/CargaUY-web/`**

#### Usuarios de prueba listos para usar:

| Rol / Tipo | Usuario | Contraseña | Vía de Ingreso |
|---|---|---|---|
| **Administrador Central** | `admin.central` | `admin123` | Formulario interno (LDAP) |
| **Administrador de pruebas** | `martin.admin` | `admin123` | Formulario interno (LDAP) |
| **Autoridad MTOP** | `alberto.autoridad` | `autoridad123` | Formulario interno (LDAP) |
| **Ciudadano / Chofer** | `ciudadano.prueba` | Consultar `.local-p1/cuentas-locales.txt` | Botón *Ingresar con Usuario gub.uy* |
| **Consola Keycloak** | `gestor-idp` | Consultar `.local-p1/cuentas-locales.txt` | Consola http://localhost:8083 |

---

## 🛠️ Comandos de utilidad

* **Ver el estado de los contenedores:**
  ```bash
  docker compose ps
  ```
* **Ver los logs de un servicio (ej. postgres o wildfly):**
  ```bash
  docker compose logs -f postgres
  docker compose logs -f wildfly
  ```
* **Detener los servicios sin perder datos:**
  ```bash
  docker compose stop
  ```
* **Reiniciar todo desde cero (Base de datos limpia):**
  ```bash
  docker compose down -v
  ```

---

## 🧪 Pruebas y cobertura de código (JaCoCo)

Para compilar, ejecutar todos los tests unitarios y de integración, y generar el reporte de cobertura:

```bash
mvn clean verify
```

* **Reporte HTML de cobertura:** Abrir `ejb/target/site/jacoco/index.html` en el navegador.
* El pipeline de integración continua (`.gitlab-ci.yml`) verifica automáticamente las fases de `build` → `test` → `quality` (Sonar) → `package`.

---

## 🔒 Auditoría inmutable

La tabla `registro_auditoria` es inmutable (*append-only*): al desplegar el backend, el EJB `@Startup` `AuditoriaTriggerInstaller` instala a nivel de PostgreSQL un trigger que bloquea y rechaza cualquier operación de `UPDATE` o `DELETE`. Se reinstala automáticamente en cada inicialización para garantizar trazabilidad legal inalterable.
