# Guía de Compilación, Pruebas y Despliegue (`BUILD.md`)

Instrucciones completas para compilar, probar y ejecutar todos los componentes de **carga.uy**.

---

## 1. Prerrequisitos

- **Java JDK 17** (compila con `--release 17` y corre sobre JDK 21 en la imagen oficial de WildFly 41).
- **Apache Maven 3.9+**.
- **Docker Desktop** (para levantar la infraestructura completa en contenedores).
- **Python 3** (opcional, para ejecutar `scripts/generador_carga_tracking.py` y el handler FaaS en desarrollo).
- **Android Studio / JDK 17 + Gradle Wrapper** (para compilar `CargaUYAndroid`).

---

## 2. Compilación y Pruebas del Backend (Maven)

El proyecto sigue una estructura multimódulo Maven (`pom.xml` raíz) con empaquetado canónico Jakarta EE:
- `ejb`: Lógica de negocio (`CargaUY-ejb.jar`), entidades JPA, motor de fiscalización, persistencia MongoDB, clientes REST/SOAP/FaaS, productores/consumidores JMS y seguridad (OIDC + LDAP + PKCS#12).
- `web`: Frontoffice y Backoffice JSF 4.0 / PrimeFaces 15 (`CargaUY-web.war`) + API REST Jakarta (`AplicacionRest` en `/api/*`).
- `ear`: Empaquetado final `ear/target/CargaUY.ear`.
- `gub-callback-root`: WAR auxiliar en contexto raíz `/` (`gub-callback-root.war`) para callbacks OIDC locales de Usuario gub.uy.

### Compilar y ejecutar la suite de pruebas y gate de cobertura (JaCoCo ≥ 80%)
```bash
mvn -B --errors --fail-at-end clean verify
```
El objetivo `verify` ejecuta todos los tests unitarios (`CargaUY-ejb` y `CargaUY-web`), genera los reportes HTML de cobertura en `ejb/target/site/jacoco/index.html` y `web/target/site/jacoco/index.html` y valida automáticamente la regla `jacoco:check` en el módulo `ejb` (`jacoco.minimum.coverage = 0.80` en `pom.xml` raíz).

### Compilar y empaquetar rápido (tests sin validación de gate / sin tests)
```bash
# Suite de tests + empaquetado
mvn -B test package

# Solo empaquetado rápido de artefactos
mvn -B package -DskipTests
```

---

## 3. Despliegue con Docker Compose

`docker-compose.yml` orquesta los servicios `postgres` (`postgres:16-alpine`), `mongodb` (`mongo:7`), `ldap` (OpenLDAP), `mock-pdi` (PDI SOAP), `wildfly` (`CargaUY.ear` + `gub-callback-root.war`), el inicializador `seed` y los perfiles opcionales `mock-gub` (Keycloak) y `reset` (`reset-mongo` + `reset`).

### 3.1 Configuración previa de secretos locales
El archivo `docker-compose.yml` referencia (como opcionales con `required: false`):
1. `.env` *(no versionado en Git; copiar desde `.env.example`)*: variables locales de LDAP (`LDAP_ROOT_PASSWORD`, `LDAP_APP_UID`, `LDAP_APP_PASSWORD`), FaaS (`FAAS_TRACKING_URL`, `FAAS_SHARED_SECRET`), Keycloak y endpoints/timeouts de nodos periféricos (`BALANZA_ENDPOINT`, `BALANZA_TIMEOUT_SECONDS`, `TRACKING_ENDPOINT`, `TRACKING_TIMEOUT_SECONDS`).
   ```bash
   cp .env.example .env
   ```
2. `.local-p1/gub-real.env` *(no versionado en Git)*: contiene el secreto OIDC real para Usuario gub.uy:
   ```env
   GUB_CLIENT_SECRET=<secreto-oidc>
   ```
   *(Si se utiliza únicamente el simulador local Keycloak con `--profile mock-gub`, este archivo puede omitirse o mantener un valor de prueba).*

### 3.2 Levantar todo el entorno
```bash
# Construir e iniciar todos los servicios
docker compose up -d --build --remove-orphans
```

Para incluir el contenedor local de **Keycloak** (simulador OIDC en `http://localhost:8083`):
```bash
docker compose --profile mock-gub up -d --build
```

### 3.3 Verificar estado y logs
```bash
docker compose ps
docker compose logs -f wildfly
```
WildFly está operativo cuando responde `HTTP 200 {"estado":"UP"}` en `http://localhost:8080/CargaUY-web/api/public/ready` (usado por el `healthcheck` de Docker) e imprime `Deployed "CargaUY.ear"` en logs.

### 3.4 Redeploy rápido del EAR sin reconstruir la imagen Docker
Si compilaste localmente con `mvn -B package -DskipTests`:
```bash
docker cp ear/target/CargaUY.ear cargauy-p1-wildfly-1:/opt/jboss/wildfly/standalone/deployments/CargaUY.ear
```

---

## 4. Carga y Reseteo de Datos de Prueba

Una vez que WildFly inicializó el esquema en PostgreSQL (`EsquemaMigracionStartupBean` + `AuditoriaTriggerInstaller`):

- **Ejecutar nuevamente el seed inicial** (se ejecuta automáticamente al hacer `docker compose up -d --build`, y es idempotente):
  ```bash
  docker compose run --rm seed
  ```
- **Resetear y recargar datos limpios** (vacía MongoDB Time-Series y PostgreSQL reinstalando los triggers `append-only` de auditoría, y vuelve a ejecutar el seed sin reiniciar Docker):
  ```bash
  docker compose --profile reset run --rm reset
  ```
- **Sincronizar usuarios de prueba en OpenLDAP** (si el volumen `ldap_p1` quedó desfasado):
  ```powershell
  ./scripts/reparar-ldap.ps1
  ```
  *(En Linux/macOS: `./scripts/reparar-ldap.sh`)*

---

## 5. Prueba de Performance y Saturación (Tracking)

Con el stack levantado y los datos de prueba cargados (chofer CI `11223344` y guía `EN_CURSO` `1001`), el script autónomo de Python 3 (sin dependencias externas) valida 5 escenarios: carga normal sostenida, ráfaga pico `3x`, idempotencia por UUID (AC012), ordenamiento ante eventos desordenados (*out-of-order*) y consultas concurrentes bajo meta `p95 < 500 ms`:

```bash
python scripts/generador_carga_tracking.py --url http://localhost:8080/CargaUY-web
```
Parámetros opcionales: `--camiones 15 --lote 5 --guia 1001 --ci 11223344 --admin-token <JWT_ADMIN>` (si se incluye `--admin-token`, consulta además `/api/ops/health` además de `/api/public/ready`).

---

## 6. Otros Componentes

### 6.1 Aplicación Móvil Android (`CargaUYAndroid`)
```bash
cd CargaUYAndroid
./gradlew assembleDebug
```
*(En Windows PowerShell/CMD: `.\gradlew.bat assembleDebug`)*. Configurar la URL del backend según el entorno (emulador Android: `10.0.2.2:8080`, o IP local de la máquina en red LAN).

### 6.2 Función FaaS de Desvío de Ruta (`faas/tracking-desvio-lambda`)
Contiene `lambda_function.py` con el cálculo geoespacial de desvío sobre polilíneas de Rutas Nacionales de Uruguay (MTOP). Provee handler para **AWS Lambda** (`lambda_function.lambda_handler`) y para **Google Cloud Run Functions** (`evaluar_tracking_http`), configurable en WildFly mediante `FAAS_TRACKING_URL` y `FAAS_SHARED_SECRET`.

---

## 7. Pipeline CI/CD (`.gitlab-ci.yml`)

El pipeline de GitLab ejecuta automáticamente en 4 etapas (`maven:3.9-eclipse-temurin-17`):
1. `build-job`: Compilación limpia de todos los módulos (`mvn clean compile`).
2. `unit-tests-job`: Ejecución de tests unitarios, reporte JaCoCo en `ejb` y `web` y verificación `jacoco:check >= 80%` en `ejb` (`mvn verify`).
3. `sonarqube-job`: Análisis estático y Quality Gate en SonarQube (ramas `main` y `develop`).
4. `package-job`: Empaquetado y publicación de `ear/target/CargaUY.ear` y `gub-callback-root/target/gub-callback-root.war`.
