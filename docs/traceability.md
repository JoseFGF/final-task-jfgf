# Traceability Matrix

Feature: `specs/001-order-lifecycle-workflow/`

Actualizado tras `/speckit.implement` (US1-US5 + Polish T056-T058): todos los
tests referenciados abajo existen como código real y pasan
(`mvn clean verify` en verde para backend, `ng test --watch=false` 44/44 en
verde para frontend). El estado ya no es "Planeado" sino:

- **Cubierto**: existe un test real, identificado por ruta, que verifica el
  criterio, y ese test pasa.
- **No verificado**: sigue sin existir un test que lo ejercite.

## Requisitos Funcionales

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

## Success Criteria

| SC | Test/tarea | Estado |
|---|---|---|
| SC-001 | `tests/integration/ExecutionMissingPhotoTest.java` | Cubierto |
| SC-002 | 401/403 transversal (ver FR-017/018) | Cubierto |
| SC-003 | `tests/integration/IncidentSummaryInsufficientTest.java`, `evals/incident-summary/` | Cubierto |
| SC-004 | T064 — procedimiento manual documentado en `specs/001-order-lifecycle-workflow/quickstart.md` ("Medición manual de SC-004") | **No verificado** (ver nota) |
| SC-005 | T065 — `tests/integration/PerformanceSmokeTest.java` | Cubierto |
| SC-006 | `tests/integration/EncryptionSmokeTest.java` | Cubierto |

## Huérfanos

Ninguno detectado — todos los archivos de test bajo `tests/contract/` y
`tests/integration/` corresponden a al menos un FR/SC de `spec.md`. Las
clases de soporte (`BaseIntegrationTest.java`, `SupervisorOnlyTestController.java`,
`TestImages.java`) no son huérfanas: son infraestructura de test compartida,
no tests en sí.

## Resumen

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
