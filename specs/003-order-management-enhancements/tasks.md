---

description: "Task list for 003-order-management-enhancements"
---

# Tasks: Gestión de órdenes por email y visualización de evidencia

**Input**: Design documents from `specs/003-order-management-enhancements/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, `contracts/openapi.yaml` (repo root, v0.2.0), quickstart.md

**Tests**: incluidos y NO opcionales — la constitution (Principio VII) exige
frontend + backend + tests en verde a la vez, y el Principio IV exige tests
de contrato que verifiquen que la implementación no diverge de
`contracts/openapi.yaml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: US1–US4, mapeadas a `spec.md`
- Cada tarea incluye ruta de archivo exacta

## Path Conventions

Mismas de la feature 001 (`plan.md`, Additional Constraints de la
constitution): `src/backend/`, `src/frontend/`, `tests/{contract,integration}/`,
`contracts/openapi.yaml` (raíz), `docs/`. Esta feature no crea carpetas
nuevas, extiende archivos existentes.

## Phase 1: Setup

Sin tareas — esta feature extiende un proyecto ya inicializado (Spring Boot +
Angular, Docker Compose, linters ya configurados en la feature 001). No se
añade ninguna dependencia ni herramienta nueva.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pieza compartida por US1 y US3: resolver un technician por email
de forma insensible a mayúsculas/minúsculas.

**⚠️ CRITICAL**: Ninguna historia de usuario de esta feature arranca hasta
cerrar esta fase.

- [X] T001 [P] `UserRepository.findByEmailIgnoreCase(String email)` (método derivado de Spring Data) en `src/backend/src/main/java/com/fieldops/repository/UserRepository.java` (ADR-005, research.md)
- [X] T002 [P] Integration test: `findByEmailIgnoreCase` encuentra al usuario con distinta capitalización (`Tania@Fieldops.test` vs `tania@fieldops.test`) y devuelve vacío si no existe, en `tests/integration/UserRepositoryEmailLookupTest.java` (depende de T001)

**Checkpoint**: Con esto listo, US1 y US3 pueden avanzar.

---

## Phase 3: User Story 1 - Asignar/reasignar por email en lugar de UUID (Priority: P1) 🎯 MVP

**Goal**: El dispatcher asigna/reasigna por email (FR-001 a FR-003, FR-003a); cualquier vista del detalle de una orden identifica al technician por email, no por UUID.

**Independent Test**: `POST /orders/{id}/reassignment` con `newTechnicianEmail` de un technician existente (incluida distinta capitalización) → 200, orden reasignada; con un email que no es technician → 422; `GET /orders/{id}` no expone ningún UUID de technician, solo su email.

### Tests for User Story 1

- [ ] T003 [P] [US1] Actualizar contract test de `POST /orders/{orderId}/reassignment` para el nuevo body `{"newTechnicianEmail": "..."}` y los códigos 200/401/403/404/409/422 en `tests/contract/ReassignmentContractTest.java`
- [ ] T004 [P] [US1] Integration test: reasignar con email válido en distinta capitalización y con espacios al principio/final → 200, technician correcto resuelto (FR-003, SC-001) en `tests/integration/ReassignmentByEmailTest.java`
- [ ] T005 [P] [US1] Integration test: reasignar con email que no corresponde a ningún technician (no existe, o existe con otro rol) → 422 (FR-002, SC-002) en `tests/integration/ReassignmentInvalidEmailTest.java`
- [ ] T006 [P] [US1] Actualizar contract tests de `GET /orders` y `GET /orders/{orderId}` para verificar que la respuesta trae `assignedTechnicianEmail` y ya NO trae `assignedTechnicianId` (FR-003a) en `tests/contract/OrdersListContractTest.java` y `tests/contract/OrderDetailContractTest.java`

### Implementation for User Story 1

- [ ] T007 [US1] `ReassignmentRequest`: sustituir `newTechnicianId` (UUID) por `newTechnicianEmail` (String, `@NotBlank`) en `src/backend/src/main/java/com/fieldops/dto/ReassignmentRequest.java`
- [ ] T008 [US1] `ReassignmentService.doReassign`: resolver el technician vía `userRepository.findByEmailIgnoreCase(email.strip())` (FR-003); lanzar `ValidationException` (422) si no existe o su rol no es `TECHNICIAN` (FR-002) — sustituye el `findById`/`NotFoundException` actual en `src/backend/src/main/java/com/fieldops/service/ReassignmentService.java` (depende de T001, T007)
- [ ] T009 [US1] `OrderController.reassignOrder`: pasar `request.newTechnicianEmail()` en `src/backend/src/main/java/com/fieldops/controller/OrderController.java` (depende de T008)
- [ ] T010 [P] [US1] `OrderSummaryResponse`: sustituir `assignedTechnicianId` (UUID) por `assignedTechnicianEmail` (String, desde `order.getAssignedTechnician().getEmail()`) en `src/backend/src/main/java/com/fieldops/dto/OrderSummaryResponse.java`
- [ ] T011 [P] [US1] `OrderDetailResponse`: mismo cambio en `src/backend/src/main/java/com/fieldops/dto/OrderDetailResponse.java`
- [ ] T012 [P] [US1] Frontend: `OrderSummary`/`OrderDetail` — sustituir `assignedTechnicianId` por `assignedTechnicianEmail` en `src/frontend/src/app/orders/order.model.ts`
- [ ] T013 [US1] Frontend: `OrderApiService.reassignOrder(orderId, newTechnicianEmail)` — renombrar parámetro y clave del body en `src/frontend/src/app/orders/order-api.service.ts` (depende de T012)
- [ ] T014 [US1] Frontend: `ReassignmentComponent` — sustituir `UUID_PATTERN`/label "UUID del nuevo technician" por validación de email (`Validators.email`) y label/placeholder acordes en `src/frontend/src/app/orders/reassignment.component.ts` y `reassignment.component.html` (depende de T013)
- [ ] T015 [US1] Frontend: mostrar `currentOrder.assignedTechnicianEmail` (no `assignedTechnicianId`) como "Técnico actual" (SC-006) en `src/frontend/src/app/orders/reassignment.component.html` y en `src/frontend/src/app/orders/order-detail.component.html` (depende de T012)

**Checkpoint**: US1 funcional e independientemente verificable.

---

## Phase 4: User Story 2 - Corregir la asignación inicial de órdenes en estado draft (Priority: P1) 🎯 MVP

**Goal**: El dispatcher puede asignar (no solo reasignar) un technician a una orden `draft` (FR-004, FR-005).

**Independent Test**: `POST /orders/{id}/reassignment` sobre una orden seed `draft` (`a1111111-...`) con un email de technician válido → 200, `status: "assigned"`; sobre una orden `closed` → sigue en 409.

### Tests for User Story 2

- [ ] T016 [P] [US2] Integration test: reasignar (asignar) una orden en `draft` con email de technician válido → 200, `status` pasa a `assigned` (FR-004, FR-005, SC-003) en `tests/integration/ReassignmentDraftInitialAssignmentTest.java`
- [ ] T017 [P] [US2] Confirmar (regresión) que reasignar una orden `closed` sigue devolviendo 409 tras el cambio de T019 (FR-006), en `tests/integration/ReassignmentClosedTest.java` (ya existe de la feature 001; ampliar si hace falta)
- [ ] T018 [P] [US2] Frontend unit test: `OrderDetailComponent.canReassign()` ya devuelve `true` para `status === 'draft'` — test de regresión que documenta que el frontend NO necesita cambios para esta historia (el bug real estaba solo en el backend) en `src/frontend/src/app/orders/order-detail.component.spec.ts`

### Implementation for User Story 2

- [ ] T019 [US2] `ReassignmentService.REASSIGNABLE_STATUSES`: añadir `OrderStatus.draft` al `Set` (además de `assigned`, `in_progress`, `pending_review`) en `src/backend/src/main/java/com/fieldops/service/ReassignmentService.java` (depende de T008 — mismo archivo, tarea secuencial)

**Checkpoint**: US1 + US2 = MVP funcional (asignar/reasignar por email, incluida la asignación inicial sobre `draft`).

---

## Phase 5: User Story 3 - Crear órdenes nuevas (Priority: P2)

**Goal**: El dispatcher crea órdenes nuevas, opcionalmente ya asignadas (FR-007, FR-007a, FR-007b, FR-007c, FR-008).

**Independent Test**: `POST /orders` con solo `description` → 201, `draft`; con `description` + `technicianEmail` válido → 201, `assigned` con `lastReassignedBy`/`lastReassignedAt` fijados; sin `description` → 422; como technician/supervisor → 403.

### Tests for User Story 3

- [ ] T020 [P] [US3] Contract test `POST /orders` (201/401/403/422) en `tests/contract/OrderCreationContractTest.java`
- [ ] T021 [P] [US3] Integration test: crear con solo `description` → `draft`, sin technician (FR-007, SC-004) en `tests/integration/OrderCreationDraftTest.java`
- [ ] T022 [P] [US3] Integration test: crear con `description` + `technicianEmail` válido → `assigned`, con `lastReassignedBy`/`lastReassignedAt` fijados al usuario/momento de la creación (FR-007b, FR-007c, SC-004, SC-007) en `tests/integration/OrderCreationAssignedTest.java`
- [ ] T023 [P] [US3] Integration test: crear sin `description` (ausente, vacía, o solo espacios en blanco) → 422 (FR-007a) en `tests/integration/OrderCreationMissingDescriptionTest.java`
- [ ] T024 [P] [US3] Integration test: crear como technician o supervisor → 403 (FR-008) en `tests/integration/OrderCreationForbiddenRoleTest.java`
- [ ] T025 [P] [US3] Integration test: crear con `technicianEmail` que no corresponde a ningún technician → 422 (FR-007b, FR-002) en `tests/integration/OrderCreationInvalidTechnicianTest.java`

### Implementation for User Story 3

- [ ] T026 [US3] Migración Flyway `ALTER TABLE orders ADD COLUMN description TEXT NULL` (aditiva, sin backfill — ADR-007) en `src/backend/src/main/resources/db/migration/V4__add_order_description.sql`
- [ ] T027 [US3] `Order`: añadir campo `description` (getter/setter) en `src/backend/src/main/java/com/fieldops/model/Order.java` (depende de T026)
- [ ] T028 [P] [US3] `CreateOrderRequest` DTO (`description` obligatoria no vacía, `technicianEmail` opcional) en `src/backend/src/main/java/com/fieldops/dto/CreateOrderRequest.java`
- [ ] T029 [US3] `OrderService.createOrder(currentUser, request)`: valida rol dispatcher (defensa en profundidad), rechaza `description` en blanco tras recortar espacios (FR-007a), crea la orden en `draft` o resuelve `technicianEmail` (reutiliza `findByEmailIgnoreCase`, mismo criterio 422 que T008) y la crea directamente en `assigned` fijando `lastReassignedBy`/`lastReassignedAt` (FR-007c) en `src/backend/src/main/java/com/fieldops/service/OrderService.java` (depende de T001, T027, T028)
- [ ] T030 [US3] `OrderController`: `POST /orders` con `@PreAuthorize("hasRole('DISPATCHER')")` en `src/backend/src/main/java/com/fieldops/controller/OrderController.java` (depende de T029)
- [ ] T031 [P] [US3] `OrderSummaryResponse` / `OrderDetailResponse`: añadir campo `description` en `src/backend/src/main/java/com/fieldops/dto/OrderSummaryResponse.java` y `OrderDetailResponse.java` (depende de T027)
- [ ] T032 [P] [US3] Frontend: añadir `description` a `OrderSummary`/`OrderDetail` y definir `CreateOrderRequest` en `src/frontend/src/app/orders/order.model.ts`
- [ ] T033 [US3] Frontend: `OrderApiService.createOrder(description, technicianEmail?)` (`POST /orders`) en `src/frontend/src/app/orders/order-api.service.ts` (depende de T032)
- [ ] T034 [US3] Frontend: nuevo `OrderCreateComponent` (formulario: descripción obligatoria + email de technician opcional) y ruta `orders/new` protegida por `roleGuard('DISPATCHER')` en `src/frontend/src/app/orders/order-create.component.ts`, `order-create.component.html` y `src/frontend/src/app/app.routes.ts` (depende de T033)
- [ ] T035 [P] [US3] Frontend: enlace "Crear orden" visible solo para dispatcher en el listado en `src/frontend/src/app/orders/order-list.component.html` (depende de T034)

**Checkpoint**: US1-US3 cubren asignación por email (incluida inicial) + creación de órdenes.

---

## Phase 6: User Story 4 - Visualizar fotos de evidencia en el detalle de la orden (Priority: P3)

**Goal**: Cualquier usuario con acceso al detalle de una orden ve sus fotos de evidencia de forma visual, con acceso autenticado (FR-009, FR-009a, FR-010).

**Independent Test**: `GET /orders/{id}/evidence-photos/{photoId}` con sesión válida y acceso a la orden → 200 + binario; sin `Authorization` → 401; como technician no asignado a esa orden → 403; el detalle de una orden sin fotos no muestra ninguna sección rota.

### Tests for User Story 4

- [ ] T036 [P] [US4] Contract test `GET /orders/{orderId}/evidence-photos/{photoId}` (200/401/403/404) en `tests/contract/EvidencePhotoContentContractTest.java`
- [ ] T037 [P] [US4] Integration test: dispatcher/supervisor obtienen el binario de cualquier foto; technician asignado obtiene las de su orden; technician no asignado → 403 (FR-009a) en `tests/integration/EvidencePhotoAccessTest.java`
- [ ] T038 [P] [US4] Integration test: petición sin `Authorization` → 401 aunque el `photoId` sea válido (FR-009a) en `tests/integration/EvidencePhotoUnauthenticatedTest.java`
- [ ] T039 [P] [US4] Frontend unit test: `OrderDetailComponent` renderiza una imagen por cada `evidencePhotoIds` y ninguna sección de fotos cuando la lista está vacía (FR-009, FR-010) en `src/frontend/src/app/orders/order-detail.component.spec.ts`

### Implementation for User Story 4

- [ ] T040 [US4] `FileStorageService.loadAsResource(String storagePath)`: lee el archivo ya guardado en el volumen Docker (ADR-004) y lo devuelve como `Resource` para streaming en `src/backend/src/main/java/com/fieldops/storage/FileStorageService.java`
- [ ] T041 [US4] `OrderService`: extraer un método reutilizable (p. ej. `Order requireOrderVisibleTo(currentUser, orderId)`) a partir de la lógica ya usada por `getOrderDetail` (`findOrderOrThrow` + `requireVisibleTo`), para que lo reutilice el servicio de fotos en `src/backend/src/main/java/com/fieldops/service/OrderService.java`
- [ ] T042 [US4] `EvidencePhotoService.getPhotoContent(currentUser, orderId, photoId)`: reutiliza T041 para la autorización, localiza la foto entre `order.getEvidencePhotos()` (404 si no pertenece a la orden) y devuelve su `Resource` + `contentType` (ADR-006) en `src/backend/src/main/java/com/fieldops/service/EvidencePhotoService.java` (depende de T040, T041)
- [ ] T043 [US4] `OrderController`: `GET /orders/{orderId}/evidence-photos/{photoId}` devolviendo `ResponseEntity<Resource>` con el `Content-Type` correcto en `src/backend/src/main/java/com/fieldops/controller/OrderController.java` (depende de T042)
- [ ] T044 [P] [US4] Frontend: `OrderApiService.getEvidencePhotoUrl(orderId, photoId)` o método que obtenga el blob autenticado (según cómo el interceptor JWT ya adjunta el header) en `src/frontend/src/app/orders/order-api.service.ts`
- [ ] T045 [US4] Frontend: `OrderDetailComponent`/`order-detail.component.html` — renderizar una imagen por cada foto de `evidencePhotoIds` (o vacío si no hay ninguna, FR-009, FR-010, SC-005) en `src/frontend/src/app/orders/order-detail.component.ts` y `order-detail.component.html` (depende de T044)

**Checkpoint**: Las 4 historias de usuario funcionan de forma independiente y en conjunto.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cierre transversal exigido por la constitution, no específico de una historia.

- [ ] T046 [P] Revisión de clean code backend — agente `java-reviewer` sobre los cambios de `src/backend/`
- [ ] T047 [P] Revisión de clean code frontend — agente `frontend-reviewer` sobre los cambios de `src/frontend/`
- [ ] T048 Actualizar `docs/traceability.md` con la ruta real de cada test de T002-T045 conforme se completen — skill `traceability-matrix`
- [ ] T049 [P] Actualizar `README.md` (raíz) si cambian credenciales/comandos de uso (p. ej. crear una orden desde la UI) — agente `docs-writer`
- [ ] T050 Ejecutar `quickstart.md` (feature 003) end-to-end sobre el entorno Docker: los 4 escenarios de validación manual + la regresión rápida sobre los escenarios de la feature 001
- [ ] T051 `docs/SLICE-REVIEW.md`: añadir la revisión de cierre de esta feature (4 preguntas, Development Workflow punto 11)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin tareas
- **Foundational (Phase 2)**: sin dependencias externas; **bloquea** US1 y US3 (ambas usan `findByEmailIgnoreCase`)
- **US1 (Phase 3, P1)**: depende de Foundational
- **US2 (Phase 4, P1)**: depende de que exista `ReassignmentService` con el cambio de email de US1 (T008) — no es independiente en código de US1 (mismo archivo, mismo método), aunque su *acceptance test* (T016) es una escena propia y separable
- **US3 (Phase 5, P2)**: depende de Foundational; reutiliza el criterio de resolución de technician de US1 (T008) como referencia, pero su propio código (`OrderService.createOrder`) es un archivo/método distinto — independiente en implementación
- **US4 (Phase 6, P3)**: independiente de US1-US3; solo comparte `OrderDetailResponse`/`OrderController` como puntos de extensión
- **Polish (Phase 7)**: depende de que las historias deseadas estén completas

### Dependencias entre historias

- **US1 (P1)**: depende de Foundational (T001)
- **US2 (P1)**: depende de US1 (T008 ya migrado a email) antes de ampliar `REASSIGNABLE_STATUSES` sobre el mismo servicio — en la práctica, implementar T019 justo después de T008-T009
- **US3 (P2)**: depende de Foundational (T001); independiente de US1/US2 en código, comparte servicio/controller
- **US4 (P3)**: independiente; comparte `OrderController`/`OrderDetailResponse` con las demás

### Dentro de cada historia

- Tests antes que implementación (deben fallar primero)
- DTO/entidad antes que servicio; servicio antes que controller
- Backend antes que el componente Angular que lo consume

## Parallel Example: User Story 3

```bash
# Tests de US3 en paralelo:
Task: "Contract test POST /orders en tests/contract/OrderCreationContractTest.java"
Task: "Integration test creación solo con descripción → draft en tests/integration/OrderCreationDraftTest.java"
Task: "Integration test creación con descripción+email → assigned en tests/integration/OrderCreationAssignedTest.java"
Task: "Integration test sin descripción → 422 en tests/integration/OrderCreationMissingDescriptionTest.java"
Task: "Integration test rol no dispatcher → 403 en tests/integration/OrderCreationForbiddenRoleTest.java"
Task: "Integration test email no technician → 422 en tests/integration/OrderCreationInvalidTechnicianTest.java"
```

## Implementation Strategy

### MVP First (User Stories 1 y 2, ambas P1)

1. Foundational (bloqueante: `findByEmailIgnoreCase`)
2. US1 (asignar/reasignar por email + mostrar por email)
3. US2 (fix del bug de asignación inicial sobre `draft`)
4. **STOP y VALIDAR**: `quickstart.md` escenarios 1-2, tests en verde
5. Continuar con US3 (P2), luego US4 (P3)

### Entrega incremental

Foundational → US1 → US2 (MVP demo) → US3 → US4 → Polish, validando cada
checkpoint antes de avanzar, conforme al Principio VII (frontend+backend+tests
juntos en cada paso, no por separado).

## Notes

- `[P]` = archivos distintos, sin dependencias entre sí
- `[Story]` mapea cada tarea a su historia de usuario para trazabilidad (alimenta `docs/traceability.md`, T048)
- Verificar que los tests fallan antes de implementar
- Commit por tarea o grupo lógico, conforme a la disciplina de gates de la constitution
- Ningún checkpoint se da por bueno sin `mvn test` y `npm test` en verde (Principio VII)
- FR-010/FR-011 (señalados en `plan.md` como requisitos de UI sin endpoint propio): FR-010 se cubre en T045; FR-011 ya está satisfecho por el código actual de `canReassign()` (T018 lo confirma como regresión, no como fix)
