# Traceability Matrix

> Este documento cubre varias features del proyecto. Cada una numera sus
> propios FR/SC de forma independiente (p. ej. `FR-001` de
> `001-order-lifecycle-workflow` no es el mismo requisito que `FR-001` de
> `003-order-management-enhancements`) — no combinar filas entre secciones.

## Feature: `specs/001-order-lifecycle-workflow/`

Actualizado tras `/speckit.implement` (US1-US5 + Polish T056-T058): todos los
tests referenciados abajo existen como código real y pasan
(`mvn clean verify` en verde para backend, `ng test --watch=false` 44/44 en
verde para frontend). El estado ya no es "Planeado" sino:

- **Cubierto**: existe un test real, identificado por ruta, que verifica el
  criterio, y ese test pasa.
- **No verificado**: sigue sin existir un test que lo ejercite.

### Requisitos Funcionales

| FR | Acceptance Criteria (spec.md) | Test | Estado |
|---|---|---|---|
| FR-001 | US1 escenario 1 | `tests/integration/OrderVisibilityTechnicianTest.java`, `tests/contract/OrdersListContractTest.java`, `tests/contract/OrderDetailContractTest.java` | Cubierto |
| FR-002 | US1 escenario 2 | `tests/integration/OrderVisibilityManagementTest.java`, `tests/contract/OrdersListContractTest.java` | Cubierto |
| FR-003 | US1 escenario 3 | `tests/integration/OrderVisibilityManagementTest.java`, `tests/contract/OrdersListContractTest.java` | Cubierto |
| FR-004 | US1 escenario 4 | `tests/contract/OrderDetailContractTest.java` (caso 403) | Cubierto |
| FR-005 | US2 escenario 1 | `tests/integration/ExecutionRegisterTest.java`, `tests/integration/ExecutionBlankNoteTest.java`, `tests/contract/ExecutionContractTest.java` | Cubierto |
| FR-006 | US2 escenario 2 | `tests/integration/ExecutionMissingPhotoTest.java` | Cubierto |
| FR-006a | Edge case "foto corrupta/formato no soportado" | `tests/integration/ExecutionInvalidPhotoTest.java` | Cubierto |
| FR-007 | US2 escenarios 3-4 | `tests/integration/ExecutionInvalidStateTest.java` | Cubierto |
| FR-008 | US2 escenario 1 | `tests/integration/ExecutionRegisterTest.java` | Cubierto |
| FR-009 | US3 escenarios 1-2 | `tests/contract/ReviewContractTest.java` | Cubierto |
| FR-010 | US3 escenarios 4-5 | `tests/contract/ReviewContractTest.java` (casos 403/409) | Cubierto |
| FR-011 | US3 escenario 1 | `tests/integration/ReviewApproveTest.java` | Cubierto |
| FR-012 | US3 escenario 2 | `tests/integration/ReviewRejectTest.java` | Cubierto |
| FR-012a | US3 escenario 3 | `tests/integration/ReviewRejectMissingCommentTest.java` | Cubierto |
| FR-013 | US4 escenario 1 | `tests/integration/ReassignmentValidStatesTest.java` | Cubierto |
| FR-014 | US4 escenarios 2-3 | `tests/integration/ReassignmentClosedTest.java`, `tests/contract/ReassignmentContractTest.java` (403) | Cubierto |
| FR-015 | US5 escenario 1 | `tests/integration/IncidentSummarySufficientTest.java`, `tests/contract/IncidentSummaryContractTest.java` | Cubierto |
| FR-016 | US5 escenario 2 | `tests/integration/IncidentSummaryInsufficientTest.java`, `tests/integration/IncidentSummaryProviderFailureTest.java`, `evals/incident-summary/` (modelo real) | Cubierto |
| FR-017 | Edge case "sin sesión válida" | 401 en los 6 contract tests (`tests/contract/*ContractTest.java`) | Cubierto |
| FR-018 | US1-US5 escenarios de rol incorrecto | 403 en los 6 contract tests (`tests/contract/*ContractTest.java`) | Cubierto |
| FR-019 | Clarifications (cifrado en tránsito) | `tests/integration/EncryptionSmokeTest.java` | Cubierto |
| FR-020 | Clarifications (cifrado en reposo/tránsito a BD) | `tests/integration/EncryptionSmokeTest.java` | Cubierto |
| FR-021 | Edge case "reasignación concurrente" | `tests/integration/ReassignmentConcurrencyTest.java` | Cubierto |
| FR-022 | US2 escenario 5 | `tests/integration/ExecutionRejectionCommentVisibilityTest.java` | Cubierto |
| FR-023 | (implícito, auditoría de seguridad) | `tests/integration/AccessAuditTest.java` | Cubierto |
| FR-024 | (añadido en Implement, ver spec.md Assumptions) | `tests/contract/AuthLoginContractTest.java`, `tests/integration/AuthLoginIntegrationTest.java` | Cubierto |

### Success Criteria

| SC | Test/tarea | Estado |
|---|---|---|
| SC-001 | `tests/integration/ExecutionMissingPhotoTest.java` | Cubierto |
| SC-002 | 401/403 transversal (ver FR-017/018) | Cubierto |
| SC-003 | `tests/integration/IncidentSummaryInsufficientTest.java`, `evals/incident-summary/` | Cubierto |
| SC-004 | T064 — procedimiento manual documentado en `specs/001-order-lifecycle-workflow/quickstart.md` ("Medición manual de SC-004") | **No verificado** (ver nota) |
| SC-005 | T065 — `tests/integration/PerformanceSmokeTest.java` | Cubierto |
| SC-006 | `tests/integration/EncryptionSmokeTest.java` | Cubierto |

### Huérfanos

Ninguno detectado — todos los archivos de test bajo `tests/contract/` y
`tests/integration/` corresponden a al menos un FR/SC de `spec.md`. Las
clases de soporte (`BaseIntegrationTest.java`, `SupervisorOnlyTestController.java`,
`TestImages.java`) no son huérfanas: son infraestructura de test compartida,
no tests en sí.

### Resumen

- **Cubierto**: 29/30 (24 FRs + 5 SCs, contando FR-006a, FR-012a y FR-024) = 97%
- **No verificado**: 1/30 (3%): **SC-004** — es una medida de experiencia de
  usuario end-to-end (incluye el tiempo real que tarda una persona en
  escribir la nota), no algo automatizable con sentido. No se ejecutó en
  este entorno de trabajo (no hay navegador ni dispositivo con cámara
  disponible para una sesión manual real), por lo que no se reporta un
  resultado inventado. El procedimiento exacto para medirla queda
  documentado en `specs/001-order-lifecycle-workflow/quickstart.md`
  ("Medición manual de SC-004"), pendiente de que alguien con acceso al
  entorno Docker completo lo ejecute y registre los 3 tiempos.

**Historial**: la matriz llegó a "Planeado" al 100% tras `/speckit.tasks` +
la corrección del hueco encontrado en esa primera pasada (FR-019/020/022/023
sin test — cerrado con T010a, T027b, T056a). Esta actualización, tras
`/speckit.implement`, convierte todo lo que ya tiene test real y en verde a
"Cubierto", incluyendo SC-005 (T065, `tests/integration/PerformanceSmokeTest.java`,
`mvn clean verify` en verde). Queda SC-004 como el único punto pendiente,
por su naturaleza no automatizable, con el procedimiento de medición manual
documentado para quien pueda ejecutarlo.

---

## Feature: `specs/003-order-management-enhancements/`

Actualizado tras `/speckit.implement` (US1-US4 + Polish T046-T047): todos los
tests referenciados abajo existen como código real y pasan (102/102 backend
`mvn test`, 62/62 frontend `ng test --watch=false`).

### Requisitos Funcionales

| FR | Acceptance Criteria (spec.md) | Test | Estado |
|---|---|---|---|
| FR-001 | US1 escenario 1 | `tests/contract/ReassignmentContractTest.java`, `tests/integration/ReassignmentByEmailTest.java` | Cubierto |
| FR-002 | US1 escenario 2 | `tests/integration/ReassignmentInvalidEmailTest.java`, `tests/integration/OrderCreationInvalidTechnicianTest.java` | Cubierto |
| FR-003 | US1 escenario 3 | `tests/integration/ReassignmentByEmailTest.java`, `tests/integration/UserRepositoryEmailLookupTest.java` | Cubierto |
| FR-003a | US1 escenario 4 | `tests/contract/OrdersListContractTest.java`, `tests/contract/OrderDetailContractTest.java` (assignedTechnicianEmail, sin UUID) | Cubierto |
| FR-004 | US2 escenario 1 | `tests/integration/ReassignmentDraftInitialAssignmentTest.java` | Cubierto |
| FR-005 | US2 escenario 1 | `tests/integration/ReassignmentDraftInitialAssignmentTest.java` | Cubierto |
| FR-006 | US2 escenario 2 | `tests/integration/ReassignmentClosedTest.java` | Cubierto |
| FR-007 | US3 escenario 1 | `tests/contract/OrderCreationContractTest.java`, `tests/integration/OrderCreationDraftTest.java` | Cubierto |
| FR-007a | US3 escenario 4 | `tests/integration/OrderCreationMissingDescriptionTest.java` | Cubierto |
| FR-007b | US3 escenarios 2-3 | `tests/integration/OrderCreationAssignedTest.java`, `tests/integration/OrderCreationInvalidTechnicianTest.java` | Cubierto |
| FR-007c | US3 escenario 2 | `tests/integration/OrderCreationAssignedTest.java` | Cubierto |
| FR-008 | US3 escenario 5 | `tests/integration/OrderCreationForbiddenRoleTest.java` | Cubierto |
| FR-009 | US4 escenario 1 | `tests/contract/EvidencePhotoContentContractTest.java`, `tests/integration/EvidencePhotoAccessTest.java`, `order-detail.component.spec.ts` (render de imágenes) | Cubierto |
| FR-009a | (transversal a US4) | `tests/integration/EvidencePhotoUnauthenticatedTest.java`, `tests/integration/EvidencePhotoAccessTest.java` | Cubierto |
| FR-010 | US4 escenario 2 | `order-detail.component.spec.ts` (ninguna sección cuando no hay fotos) | Cubierto |
| FR-011 | US2 escenario 3 | `order-detail.component.spec.ts` (regresión `canReassign()` en `draft`, T018) | Cubierto |

### Success Criteria

| SC | Test/tarea | Estado |
|---|---|---|
| SC-001 | `tests/integration/ReassignmentByEmailTest.java` | Cubierto |
| SC-002 | `tests/integration/ReassignmentInvalidEmailTest.java` | Cubierto |
| SC-003 | `tests/integration/ReassignmentDraftInitialAssignmentTest.java` | Cubierto |
| SC-004 | `tests/integration/OrderCreationDraftTest.java`, `tests/integration/OrderCreationAssignedTest.java` | Cubierto |
| SC-005 | `tests/integration/EvidencePhotoAccessTest.java`, `order-detail.component.spec.ts` | Cubierto |
| SC-006 | `order-detail.component.spec.ts`, `reassignment.component.spec.ts` (muestra email, no UUID) | Cubierto |
| SC-007 | `tests/integration/OrderCreationAssignedTest.java` (auditoría `lastReassignedBy`/`lastReassignedAt`) | Cubierto |

### Huérfanos

Ninguno detectado — todos los tests nuevos bajo `tests/contract/` y
`tests/integration/`, y los `.spec.ts` nuevos/actualizados en
`src/frontend/src/app/orders/`, corresponden a al menos un FR/SC de
`spec.md`.

### Resumen

- **Cubierto**: 23/23 (16 FRs + 7 SCs) = 100%.
- Checklist de calidad (`checklists/general.md`): 15 ítems quedaron
  aceptados explícitamente como hueco (no bloquean, cada uno con su motivo
  anotado — ver `/speckit.clarify`/`/speckit.analyze`), ninguno afecta a la
  cobertura de FR/SC reportada aquí.
- Hallazgos de code review (T046/T047) ya corregidos y commiteados: N+1 en
  `GET /orders`, acoplamiento estático entre `ReassignmentService` y
  `OrderService` (extraído a `TechnicianLookupService`), validación
  duplicada de `description`, y `canSubmit` de `OrderCreateComponent` sin
  descartar descripciones de solo espacios.

---

## Feature: `specs/004-order-state-transitions/`

Actualizado tras `/speckit.implement` (US1-US2 + Polish T023-T028): todos los
tests referenciados abajo existen como código real y pasan (136/136 backend
`mvn test`, 71/71 frontend `ng test --watch=false`).

### Requisitos Funcionales

| FR | Acceptance Criteria (spec.md) | Test | Estado |
|---|---|---|---|
| FR-001 | US1 escenario 1 | `tests/integration/OrderStartByTechnicianTest.java`, `tests/contract/OrderStatusContractTest.java` | Cubierto |
| FR-002 | US1 escenario 2 | `tests/integration/OrderStartWrongTechnicianTest.java` | Cubierto |
| FR-003 | US1 escenarios 3-4 | `tests/integration/OrderStartInvalidStateTest.java` | Cubierto |
| FR-004 | US2 escenarios 1-9 (cadena de adyacencia) | `tests/integration/OrderStatusManualForwardTest.java`, `tests/integration/OrderStatusManualBackwardTest.java`, `tests/integration/OrderStatusManualNonAdjacentTest.java`, `tests/integration/OrderStatusManualToClosedTest.java`, `tests/integration/OrderStatusManualClosedTerminalTest.java` | Cubierto |
| FR-005 | US2 escenario 5 (evidencia mínima antes de `pending_review`) | `tests/integration/OrderStatusManualMissingEvidenceTest.java`, `tests/integration/OrderStatusManualToPendingReviewTest.java` | Cubierto |
| FR-006 | US2 escenario 6 (desvincular technician al volver a `draft`) | `tests/integration/OrderStatusManualToDraftUnassignsTest.java` | Cubierto |
| FR-007 | US1/US2 rol incorrecto | `tests/integration/OrderStartWrongRoleTest.java`, `tests/integration/OrderStatusManualForbiddenForTechnicianTest.java` | Cubierto |
| FR-008 | Edge case "cambio concurrente" | `tests/integration/OrderStatusConcurrencyTest.java` | Cubierto |
| FR-009 | (decisión explícita: sin auditoría de autor/fecha) | N/A — decisión registrada en `spec.md` §Clarifications, no requiere test | Cubierto (por decisión, no por test) |

### Success Criteria

| SC | Test/tarea | Estado |
|---|---|---|
| SC-001 | `tests/integration/OrderStartByTechnicianTest.java` | Cubierto |
| SC-002 | `tests/integration/OrderStartWrongTechnicianTest.java`, `tests/integration/OrderStartWrongRoleTest.java`, `tests/integration/OrderStatusManualForbiddenForTechnicianTest.java` | Cubierto |
| SC-003 | `tests/integration/OrderStatusManualForwardTest.java` | Cubierto |
| SC-004 | `tests/integration/OrderStatusManualBackwardTest.java`, `tests/integration/OrderStatusManualToDraftUnassignsTest.java` | Cubierto |
| SC-005 | `tests/integration/OrderStatusManualNonAdjacentTest.java` | Cubierto |

### Huérfanos

Ninguno detectado — los 15 archivos de test nuevos bajo `tests/contract/` y
`tests/integration/`, y las adiciones a `order-detail.component.spec.ts`,
corresponden a al menos un FR/SC de `spec.md`.

### Resumen

- **Cubierto**: 9/9 (9 FRs + 5 SCs, contando FR-009 como cubierto por
  decisión explícita) = 100%.
- Checklist de calidad (`checklists/general.md`): 5 ítems quedaron
  aceptados explícitamente como hueco (CHK004, CHK006, CHK008, CHK014,
  CHK017 — decisiones intencionales, no olvidos), ninguno afecta a la
  cobertura de FR/SC reportada aquí.
- Hallazgo real de `/speckit.analyze` (C1: faltaba la tarea de frontend
  para el control de corrección manual de estado en US2) corregido antes
  de implementar, añadiendo T022a/T022b a `tasks.md`.
