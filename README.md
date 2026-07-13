# FieldOps

FieldOps es una API + interfaz web para gestionar el ciclo de vida de órdenes
de trabajo de campo (creación ya asignada → ejecución por un técnico →
revisión de un supervisor → reasignación por un dispatcher), con un
componente de IA que resume la incidencia registrada por el técnico a partir
de su nota de texto.

Este slice cubre exclusivamente el flujo de órdenes descrito en
`specs/001-order-lifecycle-workflow/spec.md` (US1-US5), más un endpoint
mínimo de login (`POST /api/v1/auth/login`, FR-024) añadido durante
`/speckit.implement` al detectarse que faltaba. **No incluye**
dashboard/analítica, push notifications, ni alta/registro de usuarios: los
cuatro usuarios de prueba se cargan por seed (ver [Roles de prueba](#roles-de-prueba-seed-data)),
no hay pantalla de registro ni de gestión de cuentas, pero sí pueden
autenticarse con email+password contra ese login.

## Requisitos previos

- Docker y Docker Compose (`docker compose version` ≥ v2).
- Una clave de la API de Anthropic (`ANTHROPIC_API_KEY`) — recomendada, pero
  **no obligatoria para arrancar**: verificado que el backend arranca sin
  ella (`AnthropicClient` construye el cliente HTTP con una cabecera
  `x-api-key` vacía si no se define, sin fallar en el arranque). Solo falla
  en tiempo de ejecución, con el error que devuelva la API de Anthropic, si
  se llama a `POST /orders/{orderId}/incident-summary` (US5) sin una clave
  válida configurada.
- Variables opcionales, con valor de desarrollo por defecto si no se definen
  (ver `docker-compose.yml`):
  - `JWT_SECRET` (default `dev-only-change-me`).
  - `SERVER_SSL_KEY_STORE_PASSWORD` (default `devchangeit`, contraseña del
    keystore autofirmado empaquetado en `src/backend/src/main/resources/certs/dev-keystore.p12`).

Ninguna de estas variables se commitea con un valor real; exportarlas en la
shell antes de levantar el stack:

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # recomendada; solo necesaria para el resumen de incidencia (US5)
export JWT_SECRET=...                 # opcional, si no se usa el default de dev
export SERVER_SSL_KEY_STORE_PASSWORD=... # opcional, si no se usa el default de dev
```

## Instalación y arranque

```bash
docker compose up --build
```

Levanta tres servicios:

| Servicio   | Puerto host | Detalle |
|------------|-------------|---------|
| `postgres` | `5432`      | Imagen propia (`docker/postgres/`) con TLS habilitado; aplica las migraciones Flyway (esquema + seed) al primer arranque. |
| `backend`  | `8443` (HTTPS) | Spring Boot, perfil `prod`: sirve la API sobre TLS con el certificado autofirmado de desarrollo y exige `sslmode=require` contra PostgreSQL. |
| `frontend` | `4200` → `80` en el contenedor | Angular servido como estático. |

Verificado de punta a punta en este entorno: `docker compose up --build`
levanta los 3 servicios, y un login real (`POST /api/v1/auth/login`) seguido
de una llamada autenticada (`GET /api/v1/orders`) funciona tanto directo
contra el backend (`https://localhost:8443`) como a través del frontend
(`http://localhost:4200/api/v1/...`, que Nginx reenvía al backend).

> **Bug encontrado y corregido durante esta verificación**: el proxy `/api/`
> de `src/frontend/nginx.conf` usaba `proxy_pass` con una variable de host
> (necesaria para resolución DNS perezosa de Docker) seguida de un sufijo de
> path (`.../api/`) — con una variable, nginx **no** reescribe el URI como sí
> hace con un `proxy_pass` literal, así que enviaba literalmente `/api/` al
> backend, descartando el resto de la ruta (`v1/orders`, etc.). Esto rompía
> **todas** las llamadas autenticadas hechas a través del frontend, sin que
> ningún test unitario/de integración lo detectara (ninguno ejercita el
> proxy real de nginx). Corregido quitando el sufijo de path del
> `proxy_pass` (el comentario en `nginx.conf` explica el porqué).

## Cómo correr los tests

### Backend

```bash
cd src/backend
mvn clean verify
```

Requiere Docker disponible en el equipo/agente que ejecuta Maven: los tests
de integración usan Testcontainers para levantar PostgreSQL efímero. Los
tests viven fuera de `src/backend/` (en `tests/unit/`, `tests/contract/` y
`tests/integration/`, en la raíz del repo, según la Organización del
repositorio de `.specify/memory/constitution.md`); el `build-helper-maven-plugin`
configurado en `src/backend/pom.xml` añade esas tres rutas como test-sources
del módulo, por lo que `mvn clean verify` desde `src/backend/` las compila y
ejecuta igual que si vivieran en `src/test/java`. El mismo comando aplica
también el chequeo de formato (Spotless) en fase `verify`.

### Frontend

```bash
cd src/frontend
npm install
npm test       # ng test — unit tests con Karma/Jasmine
npm run lint   # ng lint — ESLint
```

`npm test` levanta Chrome (vía `karma-chrome-launcher`); en un entorno sin
navegador gráfico exportar `CHROME_BIN` a un binario headless o correr con
`ng test --browsers=ChromeHeadless`.

## Evals del componente de IA (opcional, con coste real)

El resumen de incidencia (US5) tiene un arnés de evaluación separado en
`evals/incident-summary/`, que ejercita el modelo y el prompt reales contra
la API de Anthropic (distinto de los tests de `tests/`, que mockean
`AnthropicClient`). No se corre en CI ni como parte de `mvn verify`: cada
ejecución hace llamadas reales facturables y no deterministas.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
pip install pyyaml
python evals/incident-summary/run_evals.py
```

Umbral y criterios de aceptación (incluida la regla de alucinación
bloqueante): ver `evals/incident-summary/README.md`.

## Validación manual end-to-end

`specs/001-order-lifecycle-workflow/quickstart.md` describe los escenarios
manuales por historia de usuario (US1-US5), la verificación de RBAC en doble
capa (401 sin token, 403 con rol incorrecto) y el procedimiento para medir
SC-004 (tiempo de registro de ejecución end-to-end).

## Estructura del repositorio

- `src/backend/` — API Spring Boot (Java 21).
- `src/frontend/` — SPA Angular 18 + Tailwind CSS.
- `tests/{unit,contract,integration}/` — tests del backend, fuera de
  `src/backend/` a propósito (ver más arriba).
- `contracts/openapi.yaml` — contrato de la API, autoridad sobre
  request/response y códigos de error por endpoint.
- `evals/incident-summary/` — golden cases y arnés de evaluación del
  componente de IA.
- `specs/001-order-lifecycle-workflow/` — spec, plan, tareas y quickstart de
  este slice.
- `docs/traceability.md`, `docs/assumptions.md` — trazabilidad
  criterio-test y suposiciones registradas.
- `.specify/memory/constitution.md` — principios no negociables del
  proyecto; referencia obligatoria antes de proponer cambios de proceso.

## Roles de prueba (seed data)

`src/backend/src/main/resources/db/migration/V2__seed_data.sql` carga cuatro
usuarios fijos, uno por rol (más un segundo técnico), y cinco órdenes que
cubren cada estado del ciclo de vida:

| Rol | Email | Nombre |
|-----|-------|--------|
| DISPATCHER | `dispatcher@fieldops.test` | Dana Dispatcher |
| TECHNICIAN | `technician@fieldops.test` | Tomás Technician |
| TECHNICIAN | `technician2@fieldops.test` | Tania Technician |
| SUPERVISOR | `supervisor@fieldops.test` | Sara Supervisor |

**Login**: `src/backend/src/main/resources/db/migration/V3__seed_login_passwords.sql`
sustituye el placeholder `seed-not-a-real-hash` de V2 por un hash BCrypt real.
La contraseña en texto plano es la misma para los cuatro usuarios de seed:
`password123`. Este endpoint (`POST /api/v1/auth/login`, FR-024) se añadió
durante `/speckit.implement` al detectarse que ninguna historia de usuario
original cubría cómo obtener un token real (los tests generan el JWT
directamente vía `JwtService`, sin pasar por HTTP); no hay alta ni registro
de usuarios, solo login contra los cuatro usuarios fijos de arriba.

Ejemplo de login y uso del token contra un endpoint protegido:

```bash
TOKEN=$(curl -sk -X POST https://localhost:8443/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dispatcher@fieldops.test","password":"password123"}' \
  | jq -r '.token')

curl -sk https://localhost:8443/api/v1/orders \
  -H "Authorization: Bearer $TOKEN"
```

(`-k` porque el certificado es autofirmado de desarrollo; cambiar el puerto/
esquema a `http://localhost:8080` si se corre el backend fuera de Docker sin
TLS local.)

Las órdenes seed (`orders` en la misma migración), con IDs fijos para
referenciarlas desde tests o pruebas manuales: una en `draft` (sin técnico
asignado), una en `assigned`, una en `in_progress` (para US2), una en
`pending_review` con nota y foto de evidencia ya cargadas (para US3), y una
`closed` con su ciclo completo aprobado.
