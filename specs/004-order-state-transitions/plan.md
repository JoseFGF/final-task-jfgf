# Implementation Plan: Transiciones de estado de la orden por rol

**Branch**: `004-order-state-transitions` | **Date**: 2026-07-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-order-state-transitions/spec.md`

## Summary

Cierra un hueco bloqueante del ciclo de vida de órdenes: hoy ninguna acción
real lleva una orden de `assigned` a `in_progress`. Añade un único endpoint
`POST /orders/{orderId}/status` con dos conjuntos de reglas según el rol
(ADR-009, research.md): el technician asignado solo puede iniciar el
trabajo (`assigned→in_progress`); dispatcher/supervisor pueden corregir
manualmente el estado entre cualquier par de estados **adyacentes** de la
cadena ya existente `draft↔assigned↔in_progress↔pending_review→closed`
(ADR-010), respetando la evidencia mínima antes de `pending_review` y el
carácter terminal de `closed`. Sin auditoría de autor/fecha para ninguna de
las dos acciones (decisión explícita de `/speckit.clarify`). Enfoque
técnico: mismo backend Spring Boot + PostgreSQL y frontend Angular +
Tailwind de las features 001/003, sin componentes de infraestructura
nuevos y sin migración de base de datos.

## Technical Context

**Language/Version**: Java 21 (LTS) + Spring Boot 3.3.x para el backend;
TypeScript 5.x + Angular 18 para el frontend. Sin cambios.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security
(JWT); Angular (standalone components) + Tailwind CSS. Sin dependencias
nuevas — se reutiliza el mecanismo de bloqueo optimista + reintento ya
usado por `ReassignmentService` (ADR-011, research.md).

**Storage**: PostgreSQL 16 vía JPA/Hibernate. Sin migración Flyway nueva:
esta feature es puramente de comportamiento sobre `orders.status` y
`orders.assigned_technician_id`, ambas columnas ya existentes.

**Testing**: JUnit 5 + Mockito + Testcontainers (Postgres) en backend;
Jasmine + Karma en frontend. Sin cambios de herramienta.

**Target Platform**: contenedores Docker (Linux), Docker Compose. Sin
cambios.

**Project Type**: web application (frontend Angular + backend API Spring
Boot, desacoplados). Sin cambios.

**Performance Goals**: mismos objetivos ya vigentes (<2s para acciones
principales bajo uso normal); un cambio de estado es una operación puntual
sobre una única fila, sin requisito de rendimiento nuevo.

**Constraints**: RBAC en doble capa sin excepción para el endpoint nuevo
(Principio II): el rol y la asignación se validan en el backend, nunca solo
en el frontend.

**Scale/Scope**: sin cambio de escala respecto a las features anteriores.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Cómo se cumple |
|---|---|---|
| I. Spec-Antes-Que-Código | PASS | Este plan se escribe después de spec.md + Clarify (3 preguntas) + Checklist (con resolución de un hallazgo real, CHK001/CHK007), todos completados con sus propios commits previstos, sobre una rama de feature dedicada (`004-order-state-transitions`) desde el inicio. |
| II. RBAC en Doble Capa | PASS | `POST /orders/{orderId}/status` valida rol y, para TECHNICIAN, propiedad de la orden, en el servicio backend (defensa en profundidad), no solo vía `@PreAuthorize`/frontend. |
| III. Trazabilidad y Verificabilidad Total | PASS (a completar en Implement) | Los 9 FRs de `spec.md` tienen su correspondencia en `contracts/openapi.yaml` v0.3.0; `docs/traceability.md` se actualiza en Implement. FR-009 documenta explícitamente la decisión consciente de NO auditar (no es un hueco, es una decisión registrada). |
| IV. Contrato Antes de Implementar | PASS | `contracts/openapi.yaml` actualizado en este plan (v0.2.0 → v0.3.0) antes de tocar ningún controller/servicio/componente. |
| V. IA Sin Alucinación | N/A | Esta feature no toca el componente de resumen de incidencia. |
| VI. Clean Code Spring Boot + Angular | PASS (a verificar en Implement) | Delegado a `java-expert`/`java-reviewer` y `frontend-expert`/`frontend-reviewer`. |
| VII. Entorno Reproducible y Tests en Verde | PASS (a completar en Implement) | `quickstart.md` reutiliza el mismo comando único de arranque; añade sus propios escenarios de validación manual y una regresión rápida sobre las features 001/003. |
| VIII. Riesgo Técnico y de Seguridad en el Plan | PASS | `research.md` contiene 3 ADRs nuevos (009–011) con criterio de revisión, una tabla STRIDE para el endpoint nuevo, y criterios de reversión. |

Sin violaciones que requieran justificación — no se rellena Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/004-order-state-transitions/
├── plan.md              # este archivo
├── research.md           # Phase 0 — ADR-009 a ADR-011, STRIDE, criterios de reversión
├── data-model.md         # Phase 1 — tabla de adyacencia de transiciones
├── quickstart.md         # Phase 1 — guía de validación end-to-end
└── checklists/
    ├── requirements.md    # checklist de Specify
    └── general.md          # checklist de /speckit.checklist (5 hallazgos resueltos en la misma sesión)
```

### Repositorio (conforme a Additional Constraints de la constitution)

```text
contracts/
└── openapi.yaml          # actualizado (v0.2.0 → v0.3.0): +POST /orders/{orderId}/status

docs/
└── traceability.md        # se completa durante Implement (Principio III)

src/
├── backend/               # + OrderStatusService (nuevo), + endpoint en
│                           # OrderController; reutiliza TechnicianLookupService
│                           # no, EvidencePhotoService/Order.evidencePhotos y el
│                           # patrón de bloqueo optimista de ReassignmentService
└── frontend/               # + botón "Iniciar trabajo" (technician) y control
                             # de corrección manual de estado (dispatcher/supervisor)
                             # en order-detail.component

tests/
├── contract/               # verifican que la implementación no diverge de
│                           # contracts/openapi.yaml v0.3.0
└── integration/            # Testcontainers (Postgres)
```

**Structure Decision**: misma estructura de las features 001/003 (web
application con `src/backend` + `src/frontend` bajo un `src/` común,
contrato en `contracts/` a nivel de repositorio). Esta feature no introduce
carpetas nuevas — extiende `OrderController` con un endpoint más y añade un
servicio nuevo (`OrderStatusService`) en el mismo paquete `service/` ya
existente.

## Complexity Tracking

*Sin violaciones de la Constitution Check que requieran justificación —
tabla vacía intencionadamente.*

## Validación del plan (paso 7 del Development Workflow)

Cruce spec↔plan↔contrato↔data-model realizado el 2026-07-14 sobre los 9 FRs
y las 5 Success Criteria de `spec.md`: todos tienen correspondencia directa
en `contracts/openapi.yaml` v0.3.0 (`POST /orders/{orderId}/status`) o en
`research.md`/`data-model.md` para las decisiones que no son un endpoint
propio (ADR-009 a ADR-011, tabla de adyacencia). Sin marcadores `NEEDS
CLARIFICATION` pendientes en ningún artefacto de esta feature. Sin
discrepancias encontradas entre lo que pide la spec y lo que declara el
contrato — en particular, la tabla de adyacencia de `data-model.md`
coincide exactamente con las 7 filas de transición de la spec (incluidos
los dos escenarios nuevos añadidos al resolver CHK001/CHK007 en el
checklist). Plan listo para `/speckit.tasks`.
