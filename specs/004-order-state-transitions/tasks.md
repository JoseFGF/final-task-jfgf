---

description: "Task list for 004-order-state-transitions"
---

# Tasks: Transiciones de estado de la orden por rol

**Input**: Design documents from `specs/004-order-state-transitions/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, `contracts/openapi.yaml` (repo root, v0.3.0), quickstart.md

**Tests**: incluidos y NO opcionales — la constitution (Principio VII) exige
frontend + backend + tests en verde a la vez, y el Principio IV exige tests
de contrato que verifiquen que la implementación no diverge de
`contracts/openapi.yaml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: US1–US2, mapeadas a `spec.md`
- Cada tarea incluye ruta de archivo exacta

## Path Conventions

Mismas de las features 001/003: `src/backend/`, `src/frontend/`,
`tests/{contract,integration}/`, `contracts/openapi.yaml` (raíz), `docs/`.
Esta feature no crea carpetas nuevas.

## Phase 1: Setup

Sin tareas — extiende un proyecto ya inicializado. Sin dependencias nuevas
ni migración de base de datos (FR-009 descarta explícitamente un campo de
auditoría nuevo).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: El DTO del nuevo endpoint es compartido por ambas historias de
usuario (el mismo `POST /orders/{orderId}/status` sirve a technician y a
dispatcher/supervisor, ADR-009).

- [ ] T001 [P] `OrderStatusChangeRequest` DTO (`status: OrderStatus`, `@NotNull`) en `src/backend/src/main/java/com/fieldops/dto/OrderStatusChangeRequest.java`

**Checkpoint**: Con esto listo, US1 puede avanzar (US2 depende de que US1 haya creado `OrderStatusService`, ver más abajo).

---

## Phase 3: User Story 1 - El technician inicia el trabajo de una orden asignada (Priority: P1) 🎯 MVP

**Goal**: El technician asignado a una orden `assigned` puede marcarla como iniciada (`in_progress`), cerrando el hueco bloqueante del ciclo de vida (FR-001 a FR-003).

**Independent Test**: `POST /orders/{id}/status` con `{"status": "in_progress"}` como el technician asignado a una orden `assigned` → 200; como otro technician → 403; sobre una orden que no está en `assigned` → 409.

### Tests for User Story 1

- [ ] T002 [P] [US1] Contract test `POST /orders/{orderId}/status` — esqueleto del endpoint y forma de `ErrorResponse` (200/401/403/404/409) en `tests/contract/OrderStatusContractTest.java`
- [ ] T003 [P] [US1] Integration test: technician asignado marca `assigned→in_progress` → 200 (FR-001, SC-001) en `tests/integration/OrderStartByTechnicianTest.java`
- [ ] T004 [P] [US1] Integration test: technician NO asignado a la orden intenta iniciarla → 403 (FR-002, SC-002) en `tests/integration/OrderStartWrongTechnicianTest.java`
- [ ] T005 [P] [US1] Integration test: technician asignado intenta iniciar una orden que no está en `assigned` (p. ej. ya `in_progress`) → 409 (FR-003) en `tests/integration/OrderStartInvalidStateTest.java`
- [ ] T006 [P] [US1] Integration test: dispatcher o supervisor intentan usar esta acción específica de "iniciar trabajo" (semánticamente, pedir un `status` distinto del que su rol tiene permitido, o intentar sin ser el technician) → 403 en `tests/integration/OrderStartWrongRoleTest.java`

### Implementation for User Story 1

- [ ] T007 [US1] `OrderStatusService`: constante de adyacencia (ADR-010, `data-model.md`) — `Set` de pares `(origen, destino)` reconocidos: `draft↔assigned`, `assigned↔in_progress`, `in_progress↔pending_review`, `pending_review→closed`; método `changeStatus(CurrentUser, orderId, OrderStatus target)` con rama TECHNICIAN: exige `target == in_progress`, `order.getStatus() == assigned` y que el technician autenticado sea `order.getAssignedTechnician()` (FR-001 a FR-003); reutiliza el patrón de bloqueo optimista + reintento con auto-referencia `@Lazy` de `ReassignmentService` (ADR-011) en `src/backend/src/main/java/com/fieldops/service/OrderStatusService.java` (depende de T001)
- [ ] T008 [US1] `OrderController`: `POST /orders/{orderId}/status` con `@PreAuthorize("hasAnyRole('DISPATCHER','TECHNICIAN','SUPERVISOR')")` (el servicio decide la regla exacta por rol) en `src/backend/src/main/java/com/fieldops/controller/OrderController.java` (depende de T007)
- [ ] T009 [P] [US1] Frontend: `OrderApiService.changeOrderStatus(orderId, status)` (`POST /orders/{orderId}/status`) en `src/frontend/src/app/orders/order-api.service.ts`
- [ ] T010 [US1] Frontend: botón "Iniciar trabajo" en `order-detail.component` — visible si `role === TECHNICIAN` y `currentOrder.status === 'assigned'` (control de UX, Principio II: el backend valida la propiedad real vía T007; el frontend no conoce el UUID del technician asignado, solo su email, y el JWT no lo expone — mismo patrón ya usado por `canReview`/`canReassign`) en `src/frontend/src/app/orders/order-detail.component.ts` y `order-detail.component.html` (depende de T009)
- [ ] T011 [P] [US1] Frontend unit test: el botón "Iniciar trabajo" aparece solo para `TECHNICIAN` sobre una orden `assigned`, y llama a `changeOrderStatus(id, 'in_progress')` en `src/frontend/src/app/orders/order-detail.component.spec.ts`

**Checkpoint**: US1 funcional e independientemente verificable — cierra el hueco bloqueante (SC-001).

---

## Phase 4: User Story 2 - Dispatcher y supervisor corrigen manualmente el estado de una orden (Priority: P2)

**Goal**: Dispatcher/supervisor pueden mover una orden entre pares de estados adyacentes, en cualquier dirección, respetando la evidencia mínima antes de `pending_review` y el carácter terminal de `closed` (FR-004 a FR-006).

**Independent Test**: Como dispatcher o supervisor, `POST /orders/{id}/status` sobre una orden en cualquier estado no terminal, hacia un destino adyacente → 200; hacia uno no adyacente → 409; hacia `pending_review` sin evidencia → 422; sobre `closed` → 409.

### Tests for User Story 2

- [ ] T012 [P] [US2] Integration test: dispatcher/supervisor cambian `assigned→in_progress` manualmente → 200 (mismo resultado que US1) (FR-004, SC-001) en `tests/integration/OrderStatusManualForwardTest.java`
- [ ] T013 [P] [US2] Integration test: dispatcher/supervisor intentan `in_progress→pending_review` sin ninguna foto de evidencia → 422 (FR-005, SC-003) en `tests/integration/OrderStatusManualMissingEvidenceTest.java`
- [ ] T014 [P] [US2] Integration test: dispatcher/supervisor cambian `in_progress→pending_review` con evidencia ya registrada → 200 (FR-005) en `tests/integration/OrderStatusManualToPendingReviewTest.java`
- [ ] T015 [P] [US2] Integration test: dispatcher/supervisor cambian `assigned→draft` (retroceso) → 200 y el technician queda desvinculado (`assignedTechnicianEmail: null`) en `tests/integration/OrderStatusManualToDraftUnassignsTest.java`
- [ ] T016 [P] [US2] Integration test: dispatcher/supervisor cambian `pending_review→in_progress` manualmente (sin comentario de rechazo) → 200 en `tests/integration/OrderStatusManualBackwardTest.java`
- [ ] T017 [P] [US2] Integration test: dispatcher/supervisor cambian `pending_review→closed` manualmente → 200 (FR-004) en `tests/integration/OrderStatusManualToClosedTest.java`
- [ ] T018 [P] [US2] Integration test: dispatcher/supervisor intentan una transición no adyacente (`draft→closed`, `draft→in_progress`, `assigned→pending_review`, `assigned→closed`, `in_progress→closed`) → 409 en cada caso (FR-004, SC-005) en `tests/integration/OrderStatusManualNonAdjacentTest.java`
- [ ] T019 [P] [US2] Integration test: dispatcher/supervisor intentan cambiar una orden `closed` a cualquier otro estado → 409 (FR-006, SC-004) en `tests/integration/OrderStatusManualClosedTerminalTest.java`
- [ ] T020 [P] [US2] Integration test: un technician intenta usar el cambio manual de estado fuera de su única transición permitida (p. ej. pedir `assigned→draft`, o pedir un cambio sobre una orden que no tiene asignada) → 403 (FR-007) en `tests/integration/OrderStatusManualForbiddenForTechnicianTest.java`
- [ ] T021 [P] [US2] Integration test: dos cambios de estado concurrentes sobre la misma orden → gana el último procesado válidamente, sin dejar la orden en estado contradictorio (FR-008) en `tests/integration/OrderStatusConcurrencyTest.java`

### Implementation for User Story 2

- [ ] T022 [US2] `OrderStatusService.changeStatus`: añadir la rama DISPATCHER/SUPERVISOR — valida que `(origen, destino)` esté en la tabla de adyacencia (T007), que el destino `pending_review` exija `!order.getEvidencePhotos().isEmpty()` (FR-005), que `closed` nunca sea origen (FR-006), y que mover a `draft` limpie `order.setAssignedTechnician(null)` (data-model.md) en `src/backend/src/main/java/com/fieldops/service/OrderStatusService.java` (depende de T007 — mismo archivo, tarea secuencial)

**Checkpoint**: US1 + US2 = ciclo de vida de la orden completamente transitable por los tres roles, con corrección manual disponible para dispatcher/supervisor.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Cierre transversal exigido por la constitution, no específico de una historia.

- [ ] T023 [P] Revisión de clean code backend — agente `java-reviewer` sobre los cambios de `src/backend/`
- [ ] T024 [P] Revisión de clean code frontend — agente `frontend-reviewer` sobre los cambios de `src/frontend/`
- [ ] T025 Actualizar `docs/traceability.md` con la ruta real de cada test de T002-T021 conforme se completen — skill `traceability-matrix`
- [ ] T026 [P] Actualizar `README.md` (raíz) si cambia el flujo de uso (p. ej. cómo un technician inicia el trabajo, cómo dispatcher/supervisor corrigen el estado) — agente `docs-writer`
- [ ] T027 Ejecutar `quickstart.md` (feature 004) end-to-end sobre el entorno Docker: los 2 escenarios de validación manual + la regresión rápida sobre las features 001/003
- [ ] T028 `docs/SLICE-REVIEW.md`: añadir la revisión de cierre de esta feature (4 preguntas, Development Workflow punto 11)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin tareas
- **Foundational (Phase 2)**: sin dependencias externas; bloquea US1 (el DTO es compartido)
- **US1 (Phase 3, P1)**: depende de Foundational; crea `OrderStatusService` desde cero (T007)
- **US2 (Phase 4, P2)**: depende de que exista `OrderStatusService` con la rama TECHNICIAN de US1 (T007) antes de añadir la rama DISPATCHER/SUPERVISOR (T022) sobre el mismo archivo — no es independiente en código de US1, aunque sus *acceptance tests* (T012-T021) son un bloque propio y separable
- **Polish (Phase 5)**: depende de que las historias deseadas estén completas

### Dentro de cada historia

- Tests antes que implementación (deben fallar primero)
- DTO antes que servicio; servicio antes que controller
- Backend antes que el componente Angular que lo consume

## Parallel Example: User Story 2 (tests)

```bash
Task: "Integration test assigned->in_progress manual en tests/integration/OrderStatusManualForwardTest.java"
Task: "Integration test in_progress->pending_review sin evidencia -> 422 en tests/integration/OrderStatusManualMissingEvidenceTest.java"
Task: "Integration test assigned->draft desvincula technician en tests/integration/OrderStatusManualToDraftUnassignsTest.java"
Task: "Integration test transicion no adyacente -> 409 en tests/integration/OrderStatusManualNonAdjacentTest.java"
Task: "Integration test closed es terminal -> 409 en tests/integration/OrderStatusManualClosedTerminalTest.java"
Task: "Integration test concurrencia en tests/integration/OrderStatusConcurrencyTest.java"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Foundational (bloqueante: DTO compartido)
2. US1 (technician inicia el trabajo) — cierra el hueco bloqueante por sí sola
3. **STOP y VALIDAR**: `quickstart.md` escenario 1, tests en verde
4. Continuar con US2 (P2)

### Entrega incremental

Foundational → US1 (MVP demo) → US2 → Polish, validando cada checkpoint
antes de avanzar, conforme al Principio VII.

## Notes

- `[P]` = archivos distintos, sin dependencias entre sí
- `[Story]` mapea cada tarea a su historia de usuario para trazabilidad (alimenta `docs/traceability.md`, T025)
- Verificar que los tests fallan antes de implementar
- Commit por tarea o grupo lógico
- Ningún checkpoint se da por bueno sin `mvn test` y `npm test` en verde (Principio VII)
- FR-009 (sin auditoría) no requiere ninguna tarea propia: es la ausencia deliberada de un campo, no una implementación — se verifica por omisión en los tests de T003/T012 (la respuesta no incluye ningún campo nuevo de autor/fecha).
