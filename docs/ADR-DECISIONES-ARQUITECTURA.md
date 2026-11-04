# Registros de Decisiones de Arquitectura (ADR) — `carga.uy`

> **Curso:** Taller de Sistemas Empresariales (TSE) — Práctico 1 (2026) — Grupo 07  
> **Referencia de la letra:** Sección 8 (Entregable 8 — *Architecture Decision Records: Contexto, Decisión, Alternativas Descartadas y Consecuencias*).

---

## ADR-001: Monolito Modular Jakarta EE 10 (EAR) con Mensajería Interna JMS (ActiveMQ Artemis)

- **Estado:** Aceptado
- **Contexto:**  
  El sistema nacional `carga.uy` requiere gestionar guías de viaje, flota, asignaciones con bloqueo optimista, motor de fiscalización, portales diferenciados por rol (Autoridad, Funcionario, Técnico, Chofer, Ciudadano) y una API REST para la aplicación móvil Android, manteniendo alta cohesión transaccional y un despliegue reproducible mediante un único comando (`docker compose up`).
- **Decisión:**  
  Adoptar una arquitectura de **Monolito Modular empaquetado como EAR sobre Jakarta EE 10 (WildFly 41)**, separando estrictamente la capa de dominio/servicios/integración (`ejb`) de la presentación JSF PrimeFaces y endpoints JAX-RS (`web`), y utilizando **Jakarta Messaging (ActiveMQ Artemis embebido)** para desacoplar asíncronamente la ingesta de tracking (`TrackingQueue`), la evaluación de hallazgos (`FiscalizacionQueue`) y las alertas (`NotificacionQueue`).
- **Alternativas Descartadas:**
  1. *Arquitectura de Microservicios distribuidos (Spring Boot + Kafka + API Gateway):* Descartada por introducir alta complejidad accidental de red, transacciones distribuidas (Sagas/2PC) entre guías y pesadas, y un consumo excesivo de memoria RAM en el entorno de evaluación local.
  2. *Monolito sin colas asíncronas (procesamiento síncrono en el hilo HTTP):* Descartado porque acoplaría la latencia del cliente móvil a la evaluación de reglas de fiscalización y llamadas a periféricos (FaaS/Balanza), incumpliendo el objetivo $\text{p95} < 500\text{ ms}$ durante ráfagas $3\times$.
- **Consecuencias:**
  - *Positivas:* Transacciones ACID simples con JPA/JTA; menor latencia intra-proceso mediante interfaces `@Local`; absorción de ráfagas mediante MDBs (`maxSession=10`); despliegue atómico y determinista.
  - *Negativas / Trade-offs:* El escalado horizontal implica replicar el EAR completo en lugar de escalar únicamente el consumidor de tracking, mitigado mediante la separación de carga de lectura/escritura de posiciones en MongoDB.

---

## ADR-002: Persistencia Políglota (PostgreSQL 16 Relacional + MongoDB 7 Time-Series para Tracking GPS)

- **Estado:** Aceptado
- **Contexto:**  
  El dominio combina dos patrones de acceso a datos radicalmente distintos: por un lado, entidades transaccionales con fuertes invariantes relacionales y control de concurrencia (Guías de Viaje, Vehículos, Empresas, Asignaciones, Pesadas, Hallazgos y Auditoría); por otro lado, una corriente continua de alta frecuencia de coordenadas GPS (`POSICION`) con consultas temporales y geoespaciales (`GeoJSON Point`).
- **Decisión:**  
  Implementar **Persistencia Híbrida / Políglota**:
  1. **PostgreSQL 16 (JPA / Hibernate)** como sistema de registro transaccional para las entidades de negocio y eventos de ciclo de vida (`INICIO`, `CARGA`, `DESCARGA`, `INCIDENTE`, `DETENCION`, `FIN`).
  2. **MongoDB 7 (Colección Time-Series `cargauy_tracking.eventos_posicion`)** gestionada por `PosicionNoSqlRepository` (`@Singleton`) con `timeField: "tiempo"`, `metaField: "metadata"` (`guiaId`, `matricula`), documentos `GeoJSON Point` e índice de idempotencia por `uuid`, contando además con **fallback automático en memoria** si el contenedor NoSQL no está disponible.
- **Alternativas Descartadas:**
  1. *Almacenar el 100% de las posiciones GPS de alta frecuencia únicamente en PostgreSQL:* Descartado por el costo de mantenimiento de índices B-Tree y *vacuuming* ante millones de inserciones append-only diarias que degradarían las transacciones de negocio.
  2. *Usar únicamente una base NoSQL para todo el sistema:* Descartado porque la emisión de guías, asignaciones de choferes y fiscalización requieren integridad referencial estricta y transacciones ACID multi-tabla.
- **Consecuencias:**
  - *Positivas:* Aislamiento del tráfico de telemetría GPS respecto a la base relacional; compresión nativa de series temporales en MongoDB; continuidad operativa garantizada gracias al respaldo en memoria ante caídas del nodo NoSQL.
  - *Negativas / Trade-offs:* Requiere mantener sincronizada la lectura de trayectorias en `GestionEventosServiceImpl` (consultando primero MongoDB Time-Series y haciendo fallback transparente a JPA/memoria).

---

## ADR-003: Autenticación Federada Dual (OIDC Authorization Code + PKCE con gub.uy/Keycloak y OpenLDAP Interno)

- **Estado:** Aceptado
- **Contexto:**  
  La letra exige dos mecanismos de autenticación diferenciados según la naturaleza del actor (Secciones 3.1 y 4.1.2): ciudadanos, choferes, dueños de empresa y técnicos deben autenticarse mediante el proveedor de identidad nacional **Usuario gub.uy (OpenID Connect)**, mientras que el personal interno de la plataforma (Autoridades y Funcionarios) debe autenticarse contra un **directorio corporativo LDAP**.
- **Decisión:**  
  Implementar un esquema de seguridad federado dual que converge en **tokens JWT internos firmados con HMAC-SHA256 (`ServicioToken`)**:
  1. **Flujo OIDC Authorization Code + PKCE (`S256`)** contra el IdP compatible con gub.uy (Keycloak en `http://localhost:8083/realms/cargauy-test`), validando `state`, `nonce`, firma RSA contra el endpoint JWKS y `code_verifier`.
  2. **Autenticación LDAPv3 Bind (`AutenticadorLdap`)** contra OpenLDAP (`ldap://127.0.0.1:1389`, `dc=cargauy,dc=local`), resolviendo grupos organizacionales (`ou=groups`) hacia los roles `AUTORIDAD` y `FUNCIONARIO`.
- **Alternativas Descartadas:**
  1. *Credenciales locales (usuario/contraseña en tabla de base de datos):* Descartado por incumplir el requerimiento explícito de federación OIDC y directorio LDAP, además de aumentar la superficie de riesgo por almacenamiento de hashes locales.
  2. *Flujo OIDC Implicit Flow:* Descartado por ser inseguro (expone tokens en la URL sin prueba criptográfica PKCE, vulnerable a interceptación en apps móviles).
- **Consecuencias:**
  - *Positivas:* Cumplimiento estricto de estándares AGESIC (OIDC + PKCE); autorización uniforme en backend mediante `ControlAcceso` y `SesionActual` independientemente del origen de autenticación; revocación inmediata de sesiones en `SesionesRevocadas`.
  - *Negativas / Trade-offs:* Mayor cantidad de pasos en el handshake inicial de login, resuelto mediante redirección automática JSF en web y Custom Tabs / navegador integrado en Android.

---

## ADR-004: Sincronización Móvil Offline-First (Room + WorkManager) con Idempotencia por UUID y Bloqueo Optimista

- **Estado:** Aceptado
- **Contexto:**  
  Los choferes operan en rutas nacionales con cobertura móvil intermitente. Deben poder consultar su guía activa, registrar eventos de viaje en modo desconectado y sincronizarlos automáticamente al recuperar conectividad sin duplicar eventos ni sobrescribir silenciosamente cambios concurrentes en la guía.
- **Decisión:**  
  1. En **Android (`CargaUYAndroid`)**, persistir localmente guías y eventos pendientes en **SQLite mediante AndroidX Room (`AppDatabase`)** y programar un worker periódico/reactivo con **AndroidX WorkManager (`SyncEventosWorker`)** restringido a `NetworkType.CONNECTED`.
  2. Asignar un **UUID v4 en el dispositivo móvil** en el instante exacto en que ocurre cada evento y enviarlo en lote a `POST /api/chofer/eventos/sync`, aplicando deduplicación idempotente en servidor (`existeEvento(uuid)`).
  3. Proteger las modificaciones de `GuiaDeViaje` y `Asignacion` con **`@Version` de JPA (Optimistic Locking)**, rechazando actualizaciones basadas en lecturas obsoletas con un conflicto explícito (`OptimisticLockException` $\rightarrow$ error de concurrencia controlado).
- **Alternativas Descartadas:**
  1. *Generar el identificador del evento recién al llegar al servidor:* Descartado porque cualquier reintento por corte de red al recibir el ACK crearía eventos duplicados en el viaje.
  2. *Política Last-Write-Wins (LWW) sin control de versión en Guías:* Descartado porque permitiría que dos actores sobrescriban datos críticos de carga o asignación sin detectar la colisión.
- **Consecuencias:**
  - *Positivas:* Operación 100% fluida sin señal celular; cero duplicación de eventos en reconexiones masivas; detección determinista de conflictos de edición concurrente.
  - *Negativas / Trade-offs:* El cliente móvil debe gestionar estados de sincronización por evento (`PENDIENTE`, `SINCRONIZADO`, `ERROR`).

---

## ADR-005: Resiliencia en Integraciones Periféricas (PDI SOAP y Balanza REST) mediante Bulkhead, Circuit Breaker y Timeouts

- **Estado:** Aceptado
- **Contexto:**  
  `carga.uy` se integra con dos sistemas externos críticos pero potencialmente inestables o lentos: la **Plataforma de Interoperabilidad (PDI - DNIC)** vía SOAP 1.1 con WS-Addressing y el **Sistema Periférico de Balanzas en Ruta** vía HTTP/JSON. Una caída o lentitud de cualquiera de ellos no puede bloquear los hilos de WildFly ni impedir el flujo operativo.
- **Decisión:**  
  Implementar en `ClientePdi` y `BalanzaClient` los patrones de estabilidad empresarial sin dependencias pesadas externas:
  1. **Timeouts estrictos de conexión y lectura** ($1-2\text{ s}$ en PDI, $5\text{ s}$ en Balanza y $1,5\text{ s}$ en sondeos de salud).
  2. **Bulkhead por Semáforo (`Semaphore`)** que limita la cantidad máxima de llamadas salientes simultáneas, evitando el agotamiento de hilos del contenedor.
  3. **Circuit Breaker con estado Half-Open** que abre el circuito durante $10\text{ s}$ tras $3$ fallas consecutivas, fallando rápido (*fail-fast*) y permitiendo una única llamada de prueba para verificar la recuperación.
  4. **Reintentos acotados (máximo 2 intentos)** con propagación de `X-Correlation-ID` y correlación `wsa:MessageID` / `wsa:RelatesTo` en SOAP, más **fallback funcional** (ingreso manual de fecha de nacimiento si PDI no responde; registro manual/diferido de pesada si la balanza está caída).
- **Alternativas Descartadas:**
  1. *Llamadas HTTP/SOAP bloqueantes con timeouts por defecto del sistema operativo:* Descartado por riesgo de *cascading failure* y agotamiento del pool de hilos de WildFly.
  2. *Reintentos infinitos o con backoff largo en el hilo de petición del usuario:* Descartado porque degrada la experiencia de usuario y viola el SLO de respuesta.
- **Consecuencias:**
  - *Positivas:* Aislamiento total de fallas periféricas; recuperación automática cuando el contenedor mock vuelve a estar disponible; trazabilidad completa en `/api/ops/metrics` y `/admin/nodos.xhtml`.
  - *Negativas / Trade-offs:* Cuando el circuito está abierto, las solicitudes durante la ventana de $10\text{ s}$ reciben respuesta de indisponibilidad inmediata hasta que el sondeo *half-open* confirme la recuperación.

---

## ADR-006: Cálculo Híbrido de Desvío de Ruta mediante Función Serverless (FaaS) con Fallback Geométrico Local

- **Estado:** Aceptado
- **Contexto:**  
  La Sección 4.1.6 requiere incorporar al menos un componente bajo paradigma **Serverless / Function-as-a-Service (FaaS)** desacoplado del monolito para una tarea intensiva o acotada de negocio, sin que su eventual indisponibilidad detenga el registro de eventos ni el motor de fiscalización.
- **Decisión:**  
  Externalizar el **cálculo geoespacial de desvío de ruta (`DesvioRutaFaaSClient`)** hacia una función HTTP stateless (`POST /faas/calcular-desvio`) que recibe el par `(origen, destino)` y las coordenadas `(latitud, longitud)` del evento y devuelve la distancia perpendicular en kilómetros al corredor vial esperado. Si el endpoint FaaS externo no está configurado o sufre un timeout ($> 2\text{ s}$), el cliente ejecuta transparentemente un **fallback geométrico local en Java (proyección equirectangular + fórmula de Haversine punto-a-segmento)** sobre las coordenadas de las capitales departamentales de Uruguay.
- **Alternativas Descartadas:**
  1. *Depender exclusivamente de una API comercial externa (Google Directions / Mapbox) sin fallback:* Descartado porque requeriría claves pagas y cualquier corte externo dejaría ciego al motor de fiscalización (`DESVIO_RUTA`).
  2. *Ejecutar un motor GIS pesado (PostGIS completo con mallas viales) dentro del camino crítico transaccional:* Descartado por sobrecosto de infraestructura para el alcance del práctico.
- **Consecuencias:**
  - *Positivas:* Desacople limpio del cálculo matemático/geoespacial; cumplimiento del patrón FaaS con tolerancia a fallos 100% determinista; activación automática de hallazgos `DESVIO_RUTA` cuando el desvío supera el umbral ($15\text{ km}$).
  - *Negativas / Trade-offs:* El modelo lineal punto-a-segmento entre capitales es una aproximación geométrica del trazado carretero real.

---

## ADR-007: Auditoría Inmutable Append-Only en Base de Datos y Privacidad Pública mediante $k$-Anonimato ($k \ge 5$)

- **Estado:** Aceptado
- **Contexto:**  
  Por tratarse de un sistema de fiscalización estatal con valor probatorio administrativo y portal de transparencia ciudadana (Secciones 3.6 y 4.1.2), se requiere: (a) garantizar que ningún usuario ni administrador pueda alterar o borrar registros históricos de auditoría, y (b) asegurar que las estadísticas públicas no permitan re-identificar operaciones individuales de empresas o choferes en celdas con bajo volumen.
- **Decisión:**  
  1. **Auditoría Inmutable a nivel de motor PostgreSQL (`AuditoriaTriggerInstaller`):** Al iniciar la aplicación (`@Startup @Singleton`), se instala la función PL/pgSQL `impedir_modificacion_auditoria()` y el trigger `trg_auditoria_inmutable` (`BEFORE UPDATE OR DELETE ON registro_auditoria`), que aborta a nivel de motor cualquier intento de modificación o borrado incluso desde conexiones SQL directas.
  2. **Privacidad por Diseño y $k$-Anonimato en el Portal Público (`ConsultaPublicaService`):** El endpoint `/api/publico/estadisticas` y la vista pública agregan los datos exclusivamente por mes, rubro y tipo de carga, suprimiendo o agrupando bajo la categoría `"OTROS"` cualquier celda estadística cuya cardinalidad sea menor a $k = 5$ viajes (`MINIMO_K_ANONIMATO = 5`), sin exponer jamás matrículas, cédulas, razones sociales ni coordenadas individuales.
- **Alternativas Descartadas:**
  1. *Confiar únicamente en que el código Java no tenga métodos `update()` o `delete()` sobre la entidad `RegistroAuditoria`:* Descartado porque un usuario con acceso a la base de datos o un bug en una consulta JPQL podría alterar el rastro de auditoría.
  2. *Publicar listados detallados de viajes "sin nombre de chofer" en el portal ciudadano:* Descartado porque la combinación de fecha + origen + destino + rubro permite re-identificar fácilmente a una empresa o transportista en rutas de baja frecuencia (violación de $k$-anonimato).
- **Consecuencias:**
  - *Positivas:* Integridad forense garantizada por el SGBD; cumplimiento estricto de protección de datos personales (Ley N° 18.331 Uruguay) en datos abiertos.
  - *Negativas / Trade-offs:* El crecimiento de `registro_auditoria` es estrictamente monótono, requiriendo políticas de particionado o archivado en despliegues plurianuales.
