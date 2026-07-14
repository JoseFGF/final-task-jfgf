# Implementation Plan: Gestión de órdenes por email y visualización de evidencia

**Branch**: `003-order-management-enhancements` | **Date**: 2026-07-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-order-management-enhancements/spec.md`

## Summary

Extiende el slice de ciclo de vida de órdenes (feature 001) con cuatro
cambios: (1) identificar al technician por **email** en lugar de UUID al
asignar/reasignar, y al mostrarlo en cualquier vista de detalle; (2)
corregir el bug que impedía asignar un technician a una orden en `draft`,
reutilizando el mismo endpoint/mecanismo de concurrencia de reasignación ya
existente; (3) permitir al dispatcher crear órdenes nuevas, opcionalmente ya
asignadas; (4) mostrar visualmente las fotos de evidencia en el detalle de
la orden, vía un nuevo endpoint autenticado que sirve su binario. Enfoque
técnico: mismo backend Spring Boot + PostgreSQL y frontend Angular +
Tailwind de la feature 001, sin componentes de infraestructura nuevos.

## Technical Context

**Language/Version**: Java 21 (LTS) + Spring Boot 3.3.x para el backend;
TypeScript 5.x + Angular 18 para el frontend. Sin cambios respecto a la
feature 001.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security (JWT),
Flyway; Angular (standalone components) + Tailwind CSS. Sin dependencias
nuevas — se reutiliza `UserRepository` (nuevo método derivado
`findByEmailIgnoreCase`) y el mecanismo de `ReassignmentService` ya
existente (ADR-005, ADR-008 en `research.md`).

**Storage**: PostgreSQL 16 vía JPA/Hibernate; nueva migración Flyway
`V4__add_order_description.sql` (columna aditiva, nullable — ADR-007).
Evidencia fotográfica: mismo volumen Docker ya existente (ADR-004 de la
feature 001), sin mecanismo de almacenamiento nuevo — solo se añade un
endpoint de lectura (ADR-006).

**Testing**: JUnit 5 + Mockito + Testcontainers (Postgres) en backend;
Jasmine + Karma en frontend. Sin cambios de herramienta respecto a la
feature 001.

**Target Platform**: contenedores Docker (Linux), Docker Compose. Sin
cambios.

**Project Type**: web application (frontend Angular + backend API Spring
Boot, desacoplados). Sin cambios.

**Performance Goals**: mismos objetivos de la feature 001 (<2s para acciones
principales bajo uso normal); servir el binario de una foto de evidencia
individual no introduce un requisito de rendimiento nuevo (tamaño de imagen
de campo típico, sin streaming de vídeo ni archivos grandes).

**Constraints**: RBAC en doble capa sin excepción para los 2 endpoints
nuevos (creación de órdenes, contenido de fotos — Principio II); el acceso a
fotos de evidencia nunca es público sin autenticar (FR-009a, Clarify Q3).

**Scale/Scope**: sin cambio de escala respecto a la feature 001 (pilotaje de
curso, decenas de usuarios, cientos de órdenes).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Cómo se cumple |
|---|---|---|
| I. Spec-Antes-Que-Código | PASS | Este plan se escribe después de spec.md + Clarify (3 preguntas) + Checklist + revisión estructural, todos completados con sus propios commits previstos. |
| II. RBAC en Doble Capa | PASS | `POST /orders` exige `@PreAuthorize("hasRole('DISPATCHER')")` (FR-008); el nuevo endpoint de fotos reutiliza exactamente la misma regla de autorización que `GET /orders/{orderId}` (FR-009a) — ver ADR-006. |
| III. Trazabilidad y Verificabilidad Total | PASS (a completar en Implement) | Los 11+ FRs de `spec.md` tienen ya su endpoint/comportamiento correspondiente en `contracts/openapi.yaml`; `docs/traceability.md` se actualiza en Implement. La suposición resuelta de `docs/assumptions.md` (creación/asignación de órdenes) queda tachada y enlazada a esta feature. |
| IV. Contrato Antes de Implementar | PASS | `contracts/openapi.yaml` actualizado en este plan (v0.1.0 → v0.2.0) antes de tocar ningún controller/servicio Angular. |
| V. IA Sin Alucinación | N/A | Esta feature no toca el componente de resumen de incidencia; sin cambios sobre ADR-001 de la feature 001. |
| VI. Clean Code Spring Boot + Angular | PASS (a verificar en Implement) | Delegado a `java-expert`/`java-reviewer` y `frontend-expert`/`frontend-reviewer`. |
| VII. Entorno Reproducible y Tests en Verde | PASS (a completar en Implement) | `quickstart.md` reutiliza el mismo comando único de arranque; añade sus propios escenarios de validación manual y una regresión rápida sobre los escenarios de la feature 001. |
| VIII. Riesgo Técnico y de Seguridad en el Plan | PASS | `research.md` contiene 4 ADRs nuevos (005–008) con criterio de revisión, una tabla STRIDE específica para los 2 endpoints nuevos, y criterios de reversión. |

Sin violaciones que requieran justificación — no se rellena Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-order-management-enhancements/
├── plan.md              # este archivo
├── research.md           # Phase 0 — ADR-005 a ADR-008, STRIDE, criterios de reversión
├── data-model.md         # Phase 1 — cambios sobre el modelo de la feature 001
├── quickstart.md         # Phase 1 — guía de validación end-to-end
└── checklists/
    ├── requirements.md    # checklist de Specify
    └── general.md          # checklist de /speckit.checklist + revisión estructural
```

### Repositorio (conforme a Additional Constraints de la constitution)

```text
contracts/
└── openapi.yaml          # actualizado (v0.1.0 → v0.2.0): +POST /orders,
                           # +GET .../evidence-photos/{photoId}, email en
                           # vez de UUID en reassignment y en los DTOs

docs/
├── assumptions.md         # fila de creación/asignación de órdenes, resuelta
└── traceability.md        # se completa durante Implement (Principio III)

src/
├── backend/               # + OrderController.createOrder, +
│                           # evidence-photos endpoint; ReassignmentService
│                           # y UserRepository extendidos, no reescritos
└── frontend/               # + formulario de creación de órdenes; +
                             # visualización de fotos en order-detail; fix
                             # de canReassign para incluir draft

tests/
├── unit/                  # backend (JUnit+Mockito) y frontend (Jasmine)
├── contract/               # verifican que la implementación no diverge de
│                           # contracts/openapi.yaml v0.2.0
└── integration/            # Testcontainers (Postgres)
```

**Structure Decision**: misma estructura de la feature 001 (web application
con `src/backend` + `src/frontend` bajo un `src/` común, contrato en
`contracts/` a nivel de repositorio). Esta feature no introduce carpetas
nuevas — extiende archivos ya existentes en los mismos paquetes/módulos
(`OrderController`, `ReassignmentService`, `UserRepository`,
`order-detail.component`, `reassignment.component`).

## Complexity Tracking

*Sin violaciones de la Constitution Check que requieran justificación — tabla
vacía intencionadamente.*

## Validación del plan (paso 7 del Development Workflow)

Cruce spec↔plan↔contrato↔data-model realizado el 2026-07-14 sobre los **16
FRs** de `spec.md` (FR-001 a FR-011, contando las variantes FR-003a, FR-007a,
FR-007b, FR-007c y FR-009a) y las 7 Success Criteria:

- **14 FRs** tienen correspondencia directa en `contracts/openapi.yaml`
  v0.2.0 (`POST /orders`; `POST /orders/{orderId}/reassignment` actualizado;
  `GET /orders/{orderId}/evidence-photos/{photoId}`) o en `research.md`
  (ADR-005 a ADR-008) para las decisiones que no son un endpoint propio.
- **FR-010** (no mostrar una sección de fotos rota/vacía si la orden no
  tiene ninguna) y **FR-011** (el frontend debe ofrecer la acción de
  asignar también en `draft`) son requisitos puramente de comportamiento de
  UI, sin endpoint propio — igual que el resto de reglas de renderizado de
  la feature 001 (p. ej. mostrar `rejectionComment`), no se documentan como
  entrada de contrato sino como nota de implementación para `/speckit.tasks`.
  FR-010 requiere código nuevo (guarda condicional en la plantilla de
  fotos). FR-011, tras revisar `order-detail.component.ts` durante
  `/speckit.tasks`, resultó estar **ya satisfecho** por el código actual de
  `canReassign()` (que solo excluye `closed`, no `draft`) — no requiere
  cambio de producción, solo un test de regresión que lo deje documentado
  (`tasks.md` T018).
- Cuidado con la colisión de numeración: `FR-009`/`FR-010` de esta feature
  (003) no son los mismos requisitos que `FR-009`/`FR-010` de la feature 001
  (ambas specs numeran sus FRs de forma independiente); al leer
  `contracts/openapi.yaml`, que combina ambas features en un único archivo,
  hay que fijarse en a qué spec apunta cada referencia entre paréntesis.

Sin marcadores `NEEDS CLARIFICATION` pendientes en `plan.md`, `research.md`,
`data-model.md`, `quickstart.md` ni `contracts/openapi.yaml`. Plan listo para
`/speckit.tasks`, con la salvedad de FR-010/FR-011 señalada arriba para que
se traduzcan en tareas explícitas de frontend y no se den por hechas solo por
existir ya en la spec.
