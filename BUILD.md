# Guía de Compilación, Pruebas y Despliegue (`BUILD.md`)

Instrucciones completas para compilar, probar y ejecutar todos los componentes de **carga.uy**.

---

## 1. Prerrequisitos

- **Java JDK 17** (el bytecode Java 17 compila localmente y corre sobre JDK 21 en la imagen de WildFly 41).
- **Apache Maven 3.9+**.
- **Docker Desktop** (para levantar la infraestructura completa).
- **Python 3** (opcional, para ejecutar `scripts/generador_carga_tracking.py` y tests locales de mocks).
- **Android Studio / JDK 17 + Gradle Wrapper** (para compilar `CargaUYAndroid`).

---

## 2. Compilación y Pruebas del Backend (Maven)

El proyecto sigue una estructura multimódulo Maven (`pom.xml` raíz):
- `ejb`: Lógica de negocio, entidades JPA, clientes REST/SOAP, productores/consumidores JMS y seguridad.
- `web`: JSF/PrimeFaces 15 (Frontoffice y Backoffice) + API REST Jakarta (`AplicacionRest`).
- `ear`: Empaquetado final `ear/target/CargaUY.ear`.
- `gub-callback-root`: WAR auxiliar en contexto raíz `/` para callbacks OIDC locales.

### Compilar y ejecutar toda la suite de tests
```bash
mvn -B test package
```

### Generar reporte de cobertura JaCoCo
```bash
mvn clean verify
```
El reporte HTML queda disponible en `ejb/target/site/jacoco/index.html` y `web/target/site/jacoco/index.html`.

### Empaquetado rápido sin tests (solo build de artefactos)
```bash
mvn -B package -DskipTests
```

---

## 3. Despliegue con Docker Compose

### 3.1 Configuración previa de secretos locales
El archivo `docker-compose.yml` referencia:
1. `.env` *(no versionado en Git; copiar desde `.env.example`)*: variables de entorno local y URLs de nodos externos (`BALANZA_ENDPOINT`, `TRACKING_ENDPOINT`, timeouts, claves locales).
   ```bash
   cp .env.example .env
   ```
2. `.local-p1/gub-real.env` *(no versionado en Git)*: debe contener el secreto OIDC real para Usuario gub.uy:
   ```env
   GUB_CLIENT_SECRET=<secreto-oidc>
   ```
   *(Si se utiliza únicamente el simulador local Keycloak con `--profile mock-gub`, este archivo puede quedar vacío o con un valor placeholder).*

### 3.2 Levantar todo el entorno
```bash
# Construir e iniciar todos los servicios
docker compose up -d --build --remove-orphans
```

Para incluir el contenedor local de **Keycloak** (simulador OIDC en `http://localhost:8083`):
```bash
docker compose --profile mock-gub up -d --build
```

Para usar el **compose completo con mocks locales de balanza y tracking** (`mock-balanza` en `:8082` y `mock-tracking` en `:8085`):
```bash
docker compose -f docker-compose.p1.yml up -d --build
```

### 3.3 Verificar estado y logs
```bash
docker compose ps
docker compose logs -f wildfly
```
WildFly está listo cuando imprime `WFLYSRV0025: WildFly 41.0.0.Final started` y `Deployed "CargaUY.ear"`.

### 3.4 Redeploy rápido del EAR sin reconstruir la imagen Docker
Si ya compilaste localmente con `mvn -B package -DskipTests`:
```bash
docker cp ear/target/CargaUY.ear cargauy-p1-wildfly-1:/opt/jboss/wildfly/standalone/deployments/CargaUY.ear
```

---

## 4. Carga y Reseteo de Datos de Prueba

Una vez que WildFly creó el esquema en PostgreSQL:

- **Cargar seed inicial** (rubros, tipos de carga, usuarios LDAP/ciudadanos, empresas, vehículos, guías y casos de fiscalización):
  ```bash
  docker compose --profile seed run --rm seed
  ```
- **Resetear y recargar datos limpios** (vacía PostgreSQL y MongoDB y vuelve a cargar el seed, sin reiniciar Docker):
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

Con el stack levantado y los datos de prueba cargados (incluye chofer CI `11223344` y guía en curso `1001`):
```bash
python scripts/generador_carga_tracking.py --url http://localhost:8080/CargaUY-web
```
Parámetros opcionales: `--camiones 15 --lote 5 --guia 1001 --ci 11223344`.

---

## 6. Otros Componentes

### 6.1 Aplicación Móvil Android (`CargaUYAndroid`)
```bash
cd CargaUYAndroid
./gradlew assembleDebug
```
Configurar la URL del backend en la app según el entorno (emulador Android: `10.0.2.2:8080`, o IP local de la máquina en red LAN).

### 6.2 Función FaaS de Desvío de Ruta (`faas/tracking-desvio-lambda`)
Contiene `lambda_function.py` con la función de cálculo geométrico de desvío sobre el corredor vial para despliegue en AWS Lambda.

---

## 7. Pipeline CI/CD (`.gitlab-ci.yml`)

El pipeline de GitLab ejecuta automáticamente en cada push:
1. Compilación Maven (`compile`).
2. Ejecución de pruebas unitarias y reporte JaCoCo (`test`).
3. Análisis estático de calidad (`quality` / SonarQube).
4. Empaquetado final del EAR (`package`).
