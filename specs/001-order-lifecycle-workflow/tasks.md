---

description: "Task list for 001-order-lifecycle-workflow"
---

# Tasks: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

**Input**: Design documents from `specs/001-order-lifecycle-workflow/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, `contracts/openapi.yaml` (repo root), quickstart.md

**Tests**: incluidos y NO opcionales — la constitution (Principio VII) exige
frontend + backend + tests en verde a la vez, y el Principio IV exige tests
de contrato que verifiquen que la implementación no diverge de
`contracts/openapi.yaml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: US1–US5, mapeadas a spec.md
- Cada tarea incluye ruta de archivo exacta

## Path Conventions

Según `plan.md` (Additional Constraints de la constitution): `src/backend/`,
`src/frontend/`, `tests/{unit,contract,integration}/`, `evals/`,
`contracts/openapi.yaml` (raíz), `docs/`.

## Phase 1: Setup

- [X] T001 Crear estructura de repositorio: `src/backend/`, `src/frontend/`, `tests/unit/`, `tests/contract/`, `tests/integration/`, `evals/incident-summary/` per plan.md
- [X] T002 Inicializar proyecto Spring Boot 3.3 (Java 21) con dependencias Web, Data JPA, Security, Validation, Flyway en `src/backend/pom.xml`
- [X] T003 [P] Inicializar proyecto Angular 18 (standalone components) + Tailwind CSS en `src/frontend/`
- [X] T004 [P] Configurar linting/formatting: Spotless (backend) en `src/backend/pom.xml`; ESLint + Prettier (frontend) en `src/frontend/eslint.config.js` + `src/frontend/.prettierrc`
- [X] T005 Configurar `docker-compose.yml` (raíz) con servicios `backend`, `frontend`, `postgres`, variable `ANTHROPIC_API_KEY` como secreto de entorno

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura común que bloquea todas las historias de usuario.

**⚠️ CRITICAL**: Ninguna historia de usuario arranca hasta cerrar esta fase.

- [X] T006 Migración Flyway inicial (`users`, `orders`, `evidence_photos`, `access_audit_log`) en `src/backend/src/main/resources/db/migration/V1__init.sql`, según `data-model.md`
- [X] T007 [P] Entidades JPA `User`, `Order`, `EvidencePhoto`, `AccessAuditEntry` en `src/backend/src/main/java/com/fieldops/model/`
- [X] T008 [P] Autenticación JWT (Spring Security) con rol como claim, en `src/backend/src/main/java/com/fieldops/security/` (ADR-002, research.md)
- [X] T009 [P] Manejo de errores global (401/403/404/409/422 → `ErrorResponse` consistente con `contracts/openapi.yaml`) en `src/backend/src/main/java/com/fieldops/exception/GlobalExceptionHandler.java`
- [X] T010 [P] Auditoría de accesos rechazados (FR-023) en `src/backend/src/main/java/com/fieldops/security/AccessAuditAspect.java`, escribe en `AccessAuditEntry`
- [X] T010a [P] Integration test: un acceso rechazado por falta de sesión (401) o rol incorrecto (403) escribe una entrada en `access_audit_log` con `attemptedAction` y `reason` correctos (FR-023) en `tests/integration/AccessAuditTest.java`
- [X] T011 [P] Configurar Testcontainers (PostgreSQL) para tests de integración/contrato en `tests/integration/BaseIntegrationTest.java`
- [X] T012 Seed de datos de prueba (3 roles, órdenes en `draft/assigned/in_progress/pending_review/closed`) en `src/backend/src/main/resources/db/migration/V2__seed_data.sql`
- [X] T013 [P] Guard de ruta por rol + interceptor HTTP (adjunta JWT) en `src/frontend/src/app/core/auth.guard.ts` y `src/frontend/src/app/core/auth.interceptor.ts`

**Checkpoint**: Con esto listo, las historias de usuario pueden empezar (en paralelo si hay capacidad).

---

## Phase 3: User Story 1 - Consultar mis órdenes (Priority: P1) 🎯 MVP

**Goal**: Cada rol ve exactamente las órdenes que le corresponden (FR-001 a FR-004).

**Independent Test**: Con datos de seed, cada rol inicia sesión y consulta `GET /orders` / `GET /orders/{id}`, verificando visibilidad y rechazo de acceso indebido.

### Tests for User Story 1

- [X] T014 [P] [US1] Contract test `GET /orders` (200, 401) en `tests/contract/OrdersListContractTest.java`
- [X] T015 [P] [US1] Contract test `GET /orders/{id}` (200, 401, 403, 404) en `tests/contract/OrderDetailContractTest.java`
- [X] T016 [P] [US1] Integration test: technician ve solo sus órdenes asignadas en `tests/integration/OrderVisibilityTechnicianTest.java`
- [X] T017 [P] [US1] Integration test: dispatcher y supervisor ven todas las órdenes en `tests/integration/OrderVisibilityManagementTest.java`

### Implementation for User Story 1

- [X] T018 [US1] `OrderRepository` con queries de visibilidad por rol en `src/backend/src/main/java/.../repository/OrderRepository.java`
- [X] T019 [US1] `OrderService.listVisibleOrders(user)` / `getOrderDetail(user, id)` (aplica FR-004) en `src/backend/src/main/java/.../service/OrderService.java` (depende de T018)
- [X] T020 [US1] `OrderController` `GET /orders`, `GET /orders/{id}` con `@PreAuthorize` en `src/backend/src/main/java/.../controller/OrderController.java` (depende de T019)
- [X] T021 [P] [US1] `OrderApiService` (Angular, HTTP) en `src/frontend/src/app/orders/order-api.service.ts`
- [X] T022 [P] [US1] Componente lista de órdenes (por rol) en `src/frontend/src/app/orders/order-list.component.ts`
- [X] T023 [US1] Componente detalle de orden en `src/frontend/src/app/orders/order-detail.component.ts` (depende de T021)

**Checkpoint**: US1 funcional e independientemente verificable.

---

## Phase 4: User Story 2 - Registrar la ejecución de una orden (Priority: P1) 🎯 MVP

**Goal**: El technician registra ejecución con evidencia fotográfica (FR-005 a FR-008, FR-022).

**Independent Test**: Orden seed en `in_progress`; registrar sin foto (422), luego con foto (200, pasa a `pending_review`).

### Tests for User Story 2

- [X] T024 [P] [US2] Contract test `POST /orders/{id}/execution` (200, 401, 403, 404, 409, 422) en `tests/contract/ExecutionContractTest.java`
- [X] T025 [P] [US2] Integration test: registro válido con foto → `pending_review` en `tests/integration/ExecutionRegisterTest.java`
- [X] T026 [P] [US2] Integration test: sin foto → 422 (FR-006) en `tests/integration/ExecutionMissingPhotoTest.java`
- [X] T027 [P] [US2] Integration test: orden no `in_progress` o no asignada al technician → 409/403 (FR-007) en `tests/integration/ExecutionInvalidStateTest.java`
- [X] T027a [P] [US2] Integration test: foto corrupta o formato no soportado → 422, ejecución no registrada (FR-006a) en `tests/integration/ExecutionInvalidPhotoTest.java`
- [X] T027b [P] [US2] Integration test: `GET /orders/{id}` devuelve el `rejectionComment` correcto tras un rechazo, antes de volver a registrar ejecución (FR-022) en `tests/integration/ExecutionRejectionCommentVisibilityTest.java`

### Implementation for User Story 2

- [X] T028 [US2] `FileStorageService` (guarda fotos en volumen Docker, ADR-004; valida formato/integridad del archivo, FR-006a) en `src/backend/src/main/java/.../storage/FileStorageService.java`
- [X] T029 [US2] `ExecutionService.registerExecution(...)` (valida foto + estado + propiedad) en `src/backend/src/main/java/.../service/ExecutionService.java` (depende de T028)
- [X] T030 [US2] `OrderController` `POST /orders/{id}/execution` (multipart) en `src/backend/src/main/java/.../controller/OrderController.java` (depende de T029)
- [X] T031 [P] [US2] Formulario Angular de registro de ejecución (nota + subida de fotos) en `src/frontend/src/app/orders/execution-form.component.ts`
- [X] T032 [US2] Mostrar `rejectionComment` previo al technician al corregir (FR-022) en `src/frontend/src/app/orders/execution-form.component.ts` (depende de T031)

**Checkpoint**: US1 + US2 = MVP funcional del núcleo (consultar + ejecutar).

---

## Phase 5: User Story 3 - Aprobar o rechazar una orden en revisión (Priority: P2)

**Goal**: El supervisor cierra el ciclo de calidad (FR-009 a FR-012a).

**Independent Test**: Orden seed en `pending_review`; rechazar sin comentario (422), rechazar con comentario (200, `in_progress`), aprobar (200, `closed`).

### Tests for User Story 3

- [X] T033 [P] [US3] Contract test `POST /orders/{id}/review` (200, 401, 403, 404, 409, 422) en `tests/contract/ReviewContractTest.java`
- [X] T034 [P] [US3] Integration test: aprobar → `closed` en `tests/integration/ReviewApproveTest.java`
- [X] T035 [P] [US3] Integration test: rechazar con comentario → `in_progress` + comentario guardado (FR-012) en `tests/integration/ReviewRejectTest.java`
- [X] T036 [P] [US3] Integration test: rechazar sin comentario → 422 (FR-012a) en `tests/integration/ReviewRejectMissingCommentTest.java`

### Implementation for User Story 3

- [X] T037 [US3] `ReviewService.approve(...)` / `reject(...)` en `src/backend/src/main/java/.../service/ReviewService.java`
- [X] T038 [US3] `OrderController` `POST /orders/{id}/review` en `src/backend/src/main/java/.../controller/OrderController.java` (depende de T037)
- [X] T039 [P] [US3] Componente Angular de revisión (aprobar/rechazar con comentario obligatorio) en `src/frontend/src/app/orders/review.component.ts`

**Checkpoint**: US1-US3 cubren el ciclo completo de valor (consultar → ejecutar → revisar).

---

## Phase 6: User Story 4 - Reasignar una orden entre técnicos (Priority: P3)

**Goal**: El dispatcher reorganiza el trabajo (FR-013, FR-014, FR-021).

**Independent Test**: Reasignar orden `in_progress` (200), reasignar orden `closed` (409), reasignaciones concurrentes → last-write-wins.

### Tests for User Story 4

- [X] T040 [P] [US4] Contract test `POST /orders/{id}/reassignment` (200, 401, 403, 404, 409) en `tests/contract/ReassignmentContractTest.java`
- [X] T041 [P] [US4] Integration test: reasignar en `assigned`/`in_progress`/`pending_review` → 200 en `tests/integration/ReassignmentValidStatesTest.java`
- [X] T042 [P] [US4] Integration test: reasignar `closed` → 409 en `tests/integration/ReassignmentClosedTest.java`
- [X] T043 [P] [US4] Integration test: dos reasignaciones concurrentes → gana la última válida procesada (FR-021) en `tests/integration/ReassignmentConcurrencyTest.java`

### Implementation for User Story 4

- [X] T044 [US4] `ReassignmentService` con bloqueo optimista (`@Version` en `Order`) en `src/backend/src/main/java/.../service/ReassignmentService.java`
- [X] T045 [US4] `OrderController` `POST /orders/{id}/reassignment` en `src/backend/src/main/java/.../controller/OrderController.java` (depende de T044)
- [X] T046 [P] [US4] Componente Angular de reasignación (dispatcher) en `src/frontend/src/app/orders/reassignment.component.ts`

**Checkpoint**: US1-US4 = slice funcional completo sin el componente de IA.

---

## Phase 7: User Story 5 - Resumen automático de la incidencia vía asistente de IA (Priority: P3)

**Goal**: Asistente con fail-safe para el supervisor (FR-015, FR-016; ADR-001).

**Independent Test**: Nota sustancial → resumen fiel; nota vacía/insuficiente o fallo de la API externa → `sufficient: false`, sin invención.

### Tests for User Story 5

- [X] T047 [P] [US5] Contract test `POST /orders/{id}/incident-summary` (200, 401, 403, 404, 409) en `tests/contract/IncidentSummaryContractTest.java`
- [X] T048 [P] [US5] Integration test: nota suficiente → resumen fiel al contenido en `tests/integration/IncidentSummarySufficientTest.java`
- [X] T049 [P] [US5] Integration test: nota vacía/insuficiente → `sufficient: false`, `summary: null` en `tests/integration/IncidentSummaryInsufficientTest.java`
- [X] T050 [P] [US5] Integration test: fallo/timeout del cliente Anthropic (mock) → `sufficient: false` (fail-safe, ADR-001) en `tests/integration/IncidentSummaryProviderFailureTest.java`

### Implementation for User Story 5

- [X] T051 [US5] `AnthropicClient` (HTTP, timeout, manejo de error) en `src/backend/src/main/java/.../ai/AnthropicClient.java`
- [X] T052 [US5] `IncidentSummaryService` (orquesta llamada + fallback fail-safe) en `src/backend/src/main/java/.../service/IncidentSummaryService.java` (depende de T051)
- [X] T053 [US5] `OrderController` `POST /orders/{id}/incident-summary` en `src/backend/src/main/java/.../controller/OrderController.java` (depende de T052)
- [X] T054 [P] [US5] Panel Angular de resumen de incidencia para supervisor en `src/frontend/src/app/orders/incident-summary.component.ts`
- [X] T055 [US5] Golden cases + umbrales de aceptación en `evals/incident-summary/` (skill `ai-eval-harness`; depende de T052)

**Checkpoint**: Las 5 historias de usuario funcionan de forma independiente y en conjunto.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Cierre transversal exigido por la constitution, no específico de una historia.

- [X] T056 [P] Configurar TLS/HTTPS en el reverse proxy de `docker-compose.yml` y cifrado en reposo (columna/volumen) para `execution_note` y fotos (FR-019, FR-020)
- [X] T056a [P] Integration/smoke test: una conexión HTTP no cifrada es rechazada y la conexión a PostgreSQL usa TLS (FR-019, FR-020, SC-006) en `tests/integration/EncryptionSmokeTest.java` (depende de T056)
- [X] T057 [P] Revisión de clean code backend — agente `java-reviewer` sobre `src/backend/`
- [X] T058 [P] Revisión de clean code frontend — agente `frontend-reviewer` sobre `src/frontend/`
- [X] T059 Actualizar `docs/traceability.md` de "Planeado" a "Cubierto" con la ruta real de cada test, conforme se completen T014-T055, T010a, T027b, T056a y T064-T065 — skill `traceability-matrix`
- [X] T060 [P] `README.md` (raíz): instalación Docker y comandos de test — agente `docs-writer`
- [X] T061 [P] Documentación funcional en `docs/` (historias de usuario en lenguaje de negocio) — agente `functional-writer`
- [X] T062 Ejecutar `quickstart.md` end-to-end sobre el entorno Docker completo — `docker compose up --build` (tras crear los `Dockerfile` de backend/frontend que faltaban, ver T067), login real + `GET /orders` autenticado verificados vía `curl` contra los 3 servicios levantados. Encontrado y corregido un bug crítico real en `src/frontend/nginx.conf`: el proxy `/api/` con `proxy_pass` basado en variable no reescribía el path y enviaba literalmente `/api/` al backend (descartando `v1/orders`), rompiendo TODAS las llamadas autenticadas hechas a través del frontend — invisible para los tests unitarios/integración porque ninguno ejercita el proxy real de nginx. Corregido quitando el sufijo de path del `proxy_pass` (ver comentario en el propio archivo) y reforzando el forwarding explícito del header `Authorization`. Verificado en vivo: login + `GET /orders` con token vía `http://localhost:4200/api/v1/...` → 200.
- [X] T063 `docs/SLICE-REVIEW.md` con las 4 preguntas de cierre (Development Workflow, punto 11 de la constitution) + decisión de aprobación
- [ ] T064 [P] Medir SC-004 (registro de ejecución end-to-end <3min) con cronometraje manual sobre `quickstart.md` escenario US2, en al menos 3 corridas — **no ejecutado**: este entorno de trabajo no tiene navegador ni dispositivo con cámara para una sesión manual real; procedimiento exacto documentado en `specs/001-order-lifecycle-workflow/quickstart.md` ("Medición manual de SC-004"), pendiente de ejecución real
- [X] T065 [P] Medir SC-005 (<2s en GET /orders, POST execution/review/reassignment) con un test de latencia ligero en `tests/integration/PerformanceSmokeTest.java`
- [X] T066 Hueco detectado durante `/speckit.implement`: no existía ningún endpoint de login (los tests generaban el JWT directamente vía `JwtService`, sin flujo HTTP real). Añadido retroactivamente: FR-024 en `spec.md`, `V3__seed_login_passwords.sql` (hash BCrypt real reemplazando el placeholder de V2), `AuthService`/`AuthController` (`POST /api/v1/auth/login`), `/auth/login` en `contracts/openapi.yaml`, `tests/contract/AuthLoginContractTest.java` y `tests/integration/AuthLoginIntegrationTest.java`, `src/frontend/src/app/auth/login.component.ts` (pantalla de login real), y corrección de `README.md` (credenciales de seed reales + comportamiento exacto de `ANTHROPIC_API_KEY` ausente)
- [X] T067 Hueco detectado durante `/speckit.implement`: `docker-compose.yml` referenciaba `build: context: ./src/backend` y `./src/frontend`, pero ninguno de los dos tenía `Dockerfile` — `docker compose up --build` fallaba. Añadidos `src/backend/Dockerfile` (multi-stage Maven→JRE) y `src/frontend/Dockerfile` (multi-stage Node→nginx) + `src/frontend/nginx.conf` (fallback SPA + proxy `/api/` hacia el backend). Verificado con build real de las 3 imágenes y `docker compose up` completo.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup; **bloquea** todas las historias
- **User Stories (Phase 3-7)**: dependen de Foundational; independientes entre sí (pueden ir en paralelo si hay capacidad) o en orden de prioridad P1→P1→P2→P3→P3
- **Polish (Phase 8)**: depende de que las historias deseadas estén completas; T059 depende específicamente de que existan los tests de todas las historias

### Dependencias entre historias

- **US1 (P1)**: sin dependencia de otras historias
- **US2 (P1)**: usa entidades de Foundational; no depende de US1 en código, pero comparte `Order`/`OrderController`
- **US3 (P2)**: opera sobre órdenes en `pending_review`, que en producción llegan vía US2 — pero el test es independiente (orden sembrada directamente en ese estado)
- **US4 (P3)**: independiente; comparte `Order`/`OrderController` con US1-US3
- **US5 (P3)**: depende de que exista `executionNote` (dato que produce US2), pero el test es independiente (dato sembrado)

### Dentro de cada historia

- Tests antes que implementación (deben fallar primero)
- Repository/Service antes que Controller
- Backend antes que el componente Angular que lo consume (o en paralelo si el frontend usa mocks del contrato)

## Parallel Example: User Story 2

```bash
# Tests de US2 en paralelo:
Task: "Contract test POST /orders/{id}/execution en tests/contract/ExecutionContractTest.java"
Task: "Integration test registro válido con foto en tests/integration/ExecutionRegisterTest.java"
Task: "Integration test sin foto → 422 en tests/integration/ExecutionMissingPhotoTest.java"
Task: "Integration test estado inválido → 409/403 en tests/integration/ExecutionInvalidStateTest.java"
```

## Implementation Strategy

### MVP First (User Stories 1 y 2, ambas P1)

1. Setup → Foundational (bloqueante)
2. US1 (consultar órdenes)
3. US2 (registrar ejecución)
4. **STOP y VALIDAR**: correr `quickstart.md` escenarios 1-2, tests en verde
5. Continuar con US3 (P2), luego US4/US5 (P3)

### Entrega incremental

Setup+Foundational → US1 → US2 (MVP demo) → US3 → US4 → US5 → Polish, validando cada checkpoint antes de avanzar, conforme al Principio VII (frontend+backend+tests juntos en cada paso, no por separado).

## Notes

- `[P]` = archivos distintos, sin dependencias entre sí
- `[Story]` mapea cada tarea a su historia de usuario para trazabilidad (alimenta `docs/traceability.md`, T059)
- Verificar que los tests fallan antes de implementar
- Commit por tarea o grupo lógico, conforme a la disciplina de gates de la constitution
- Ningún checkpoint se da por bueno sin `mvn test` y `npm test` en verde (Principio VII)
