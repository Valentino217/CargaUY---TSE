# carga.uy — Plataforma de Gestión y Fiscalización de Transporte de Carga

**Grupo 07 — Taller de Sistemas Empresariales (TSE 2026) · Facultad de Ingeniería, UdelaR**

Plataforma empresarial **Jakarta EE 10** (`ejb` + `web` + `ear`) desplegada sobre **WildFly 41** con **PostgreSQL 16**, **MongoDB 7**, **OpenLDAP**, **ActiveMQ Artemis (JMS)** y nodos periféricos externos, destinada al registro de empresas/vehículos, seguimiento GPS de viajes y fiscalización automática para el MTOP.

---

## Inicio Rápido (Docker Compose)

1. **Requisitos previos:** Docker Desktop en ejecución, puerto `5432` libre (detener PostgreSQL local si estuviera corriendo) y archivo privado `.local-p1/gub-real.env` con las credenciales OIDC de Usuario gub.uy (`GUB_CLIENT_SECRET`, etc.).
2. **Levantar la plataforma:**
   ```bash
   docker compose up -d --build
   ```
   *(Para usar Keycloak como simulador local de gub.uy en lugar del proveedor testing real, agregar `--profile mock-gub`.)*
3. **Cargar datos iniciales de prueba (idempotente y multiplataforma):**
   ```bash
   docker compose --profile seed run --rm seed
   ```
   *(Para vaciar PostgreSQL + MongoDB y recargar los datos limpios desde cero: `docker compose --profile reset run --rm reset`.)*
4. **Acceso Web:** [`http://localhost:8080/CargaUY-web/`](http://localhost:8080/CargaUY-web/)

---

## Servicios y Parámetros Principales

| Servicio / Nodo | Endpoint por defecto | Notas |
|---|---|---|
| **CargaUY Web / REST** | `http://localhost:8080/CargaUY-web/` | WildFly 41 (`standalone-full.xml`), puerto admin `9990`. |
| **PostgreSQL 16** | `localhost:5432` (`cargauy`) | BD relacional principal; trigger inmutable en `registro_auditoria`. |
| **MongoDB 7** | `localhost:27017` | Series temporales para posiciones GPS (`eventos_posicion`). |
| **OpenLDAP** | `localhost:1389` | Autenticación interna de `FUNCIONARIO`, `AUTORIDAD` y `ADMINISTRADOR`. |
| **Mock PDI (SOAP)** | `http://localhost:8081/pdi/ws` | Simulador DNIC/PDI en contenedor local. |
| **Nodo Balanzas (REST)** | `${BALANZA_ENDPOINT}` | Configurado en `.env` (Render: `cargauy-mock-balanza-latest.onrender.com`, timeout `15s`). |
| **Nodo Tracking (REST)** | `${TRACKING_ENDPOINT}` | Configurado en `.env` (Render: `cargauy-mock-tracking-latest.onrender.com`, timeout `15s`). |
| **FaaS Desvío de Ruta** | AWS Lambda (`faas/tracking-desvio-lambda`) | Cálculo geométrico de desvío sobre corredor vial; fallback local ante fallo. |

Variables clave en `.env` / `docker-compose.yml`:
- `P1_ENTORNO_LOCAL=true` y `P1_BOOTSTRAP=true`: habilitan el usuario administrador inicial (`BOOTSTRAP_ADMIN_UID`) y endpoints de prueba/dev para el componente móvil (`/api/auth/dev-chofer`). En producción deben deshabilitarse.
- `GUB_PROVEEDOR=iduruguay`: configura OIDC contra `auth-testing.iduruguay.gub.uy` (sobreescribible por perfil local).

---

## Documentación Principal

- **[BUILD.md](BUILD.md)** — Compilación Maven, pruebas unitarias/cobertura JaCoCo, despliegue Docker/WildFly, scripts de datos y compilación de la app Android y FaaS.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Arquitectura Jakarta EE, modelo de datos híbrido (PostgreSQL + MongoDB), mensajería JMS + DLQ, integraciones, seguridad, resiliencia y observabilidad.
