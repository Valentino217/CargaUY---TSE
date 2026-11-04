# Declaración de Uso de Herramientas de Inteligencia Artificial (`carga.uy`)

> **Curso:** Taller de Sistemas Empresariales (TSE) — Práctico 1 (2026) — Grupo 07  
> **Referencia de la letra:** Sección 9 (*Declaración obligatoria de uso de herramientas de IA*).

---

## 1. Herramientas de IA Utilizadas y Versiones

Durante el desarrollo, verificación y documentación del sistema `carga.uy`, el equipo utilizó de forma asistida las siguientes herramientas de Inteligencia Artificial Generativa:

| Herramienta / Entorno | Modelo / Versión | Rol Principal en el Flujo de Trabajo |
| :--- | :--- | :--- |
| **Antigravity IDE (Google DeepMind)** | Agente de codificación asistida (2026) | Asistencia en refactorización de módulos Jakarta EE 10, generación de esqueletos de pruebas unitarias JUnit 5 / Mockito, scripts de automatización y revisión de consistencia documental. |
| **ChatGPT (OpenAI) / Claude (Anthropic)** | GPT-4o / Claude 3.5 Sonnet | Consulta puntual de sintaxis de configuración de WildFly CLI (`jboss-cli`), mapeos JPA/Hibernate, API nativa de MongoDB Driver 4.11 Time-Series y Jetpack Compose + Room en Android. |

---

## 2. Partes del Proyecto donde se Utilizó IA y Propósito Específico

| Módulo / Artefacto | Propósito del Uso de IA | Intervención y Decisiones del Equipo |
| :--- | :--- | :--- |
| **1. Persistencia Políglota y Dominio (`ejb/domain`, `ejb/nosql`)** | Generación de código base para `PosicionNoSqlRepository` (colección Time-Series MongoDB 7 con documentos `GeoJSON Point` y fallback en memoria) e instalador del trigger PL/pgSQL de auditoría inmutable (`AuditoriaTriggerInstaller`). | El equipo definió el modelo conceptual, las invariantes de negocio (máximo 1 guía `EN_CURSO` por vehículo/chofer, bloqueo optimista `@Version`), y validó el comportamiento ante caída real del contenedor `mongodb`. |
| **2. Mensajería Asíncrona y Motor de Fiscalización (`ejb/jms`, `ejb/service`)** | Estructuración de `TrackingMessageProducer`, `TrackingMDB` y `FiscalizacionMDB`, incluyendo la propagación de `X-Correlation-ID` y contadores atómicos de observabilidad. | El equipo diseñó las 6 reglas de detección de hallazgos (`EXCESO_PESO`, `EXCESO_CAPACIDAD`, `DESVIO_RUTA`, `DETENCION_PROLONGADA`, `INCONSISTENCIA_CARGA`, `VIAJE_SIN_GUIA`) y su deduplicación para evitar alertas repetidas. |
| **3. Seguridad Federada y Resiliencia Periférica (`ejb/security`, `ejb/integration`)** | Asistencia en la construcción del parser XML seguro contra XXE para SOAP 1.1 + WS-Addressing (`ClientePdi`), Circuit Breaker y validación OIDC PKCE (`FlujoGub`, `ValidadorGub`, `AutenticadorLdap`). | El equipo fijó los umbrales de timeout, semáforos Bulkhead, políticas de apertura de circuito ($3$ fallos $\rightarrow 10\text{ s}$) y controles de acceso RBAC por rol en cada servicio. |
| **4. Frontend Web JSF/PrimeFaces y App Móvil Android (`web`, `CargaUYAndroid`)** | Maquetación de vistas XHTML con PrimeFaces 13 + LeafletJS para visualización de trazas GPS y componentes Jetpack Compose + Room + WorkManager para sincronización offline-first en Android. | El equipo ajustó los flujos de navegación por rol, la resolución dinámica de URLs para emulador (`10.0.2.2`) y LAN, y el manejo visual de estados de sincronización y errores en español. |
| **5. Pruebas Automatizadas, Generador de Carga y Documentación (`ejb/src/test`, `scripts`, `docs`)** | Generación asistida de casos de prueba unitarios con JUnit 5 y Mockito para superar el umbral de cobertura del $80\%$ (JaCoCo), script `scripts/generador_carga_tracking.py` y estructuración de diagramas Mermaid y ADRs. | El equipo verificó cada aserción contra los criterios de aceptación de la letra, ejecutó el pipeline `mvn clean verify` y contrastó las métricas de latencia ($\text{p95} < 500\text{ ms}$). |

---

## 3. Metodología de Verificación y Validación Aplicada por el Equipo

Ningún fragmento de código o documento sugerido por herramientas de IA fue incorporado a la rama principal sin atravesar las siguientes etapas de verificación técnica:

1. **Verificación Automatizada de Compilación, Pruebas y Cobertura (`mvn clean verify`):**
   - Ejecución completa de la suite de pruebas unitarias y de integración en los módulos `ejb` y `web` (más de 90 clases de test con JUnit 5 y Mockito).
   - Validación automática mediante **JaCoCo (`jacoco:check`)** exigiendo un mínimo de **$80\%$ de cobertura de instrucciones (`0.80`)** en el módulo de lógica de negocio (`ejb`).
2. **Pruebas de Integración End-to-End en Contenedores (`docker compose up --build`):**
   - Verificación práctica de los 6 contenedores del ecosistema (`app` WildFly 41, `db` PostgreSQL 16, `mongodb` MongoDB 7, `mock-pdi`, `mock-balanza`, `openldap`, `keycloak`).
   - Comprobación manual y por API de los flujos de login OIDC PKCE (`gub.uy`) y LDAP, emisión de guías, asignación con bloqueo optimista, sincronización offline-first desde el emulador Android y visualización de rutas en el mapa Leaflet.
3. **Pruebas de Caos / Degradación de Nodos Periféricos y NoSQL:**
   - Detención deliberada de contenedores (`docker compose stop mock-pdi mock-balanza mongodb`) comprobando que:
     - El panel `/admin/nodos.xhtml` ("Sondear Nodos") detecta el estado `OFFLINE / Degradado`.
     - `ClientePdi` y `BalanzaClient` abren sus respectivos Circuit Breakers tras 3 fallos sin bloquear hilos.
     - `PosicionNoSqlRepository` conmuta al respaldo en memoria sin perder eventos de tracking.
4. **Auditoría de Seguridad y Privacidad:**
   - Verificación de protección contra ataques XXE en SOAP, validación de estado/nonce/PKCE en OIDC, rechazo de `UPDATE`/`DELETE` sobre `registro_auditoria` a nivel de trigger PostgreSQL y cumplimiento de $k$-anonimato ($k \ge 5$) en el endpoint público `/api/publico/estadisticas`.
