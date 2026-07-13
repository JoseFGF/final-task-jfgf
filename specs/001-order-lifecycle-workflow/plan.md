# Implementation Plan: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

**Branch**: `001-order-lifecycle-workflow` | **Date**: 2026-07-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-order-lifecycle-workflow/spec.md`

## Summary

Slice de FieldOps que cubre el ciclo completo de una orden de trabajo:
consulta por rol, registro de ejecución con evidencia fotográfica
(technician), aprobación/rechazo con comentario obligatorio (supervisor),
reasignación (dispatcher), y un resumen de incidencia generado por IA con
fail-safe cuando falta evidencia suficiente. Enfoque técnico: backend Spring
Boot + PostgreSQL exponiendo una API REST documentada en
`contracts/openapi.yaml`, consumida por un frontend Angular + Tailwind, con
RBAC en doble capa (JWT + `@PreAuthorize`) y la API de Anthropic como
proveedor del componente de IA, con fallback fail-safe si la llamada falla.

## Technical Context

**Language/Version**: Java 21 (LTS) + Spring Boot 3.3.x para el backend;
TypeScript 5.x + Angular 18 para el frontend.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security
(JWT), Flyway (migraciones); Angular (standalone components) + Tailwind CSS;
cliente HTTP hacia la API de Anthropic (ADR-001, `research.md`).

**Storage**: PostgreSQL 16 vía JPA/Hibernate (ADR-003); evidencia fotográfica
en volumen de disco Docker, referenciada por ruta desde la BD (ADR-004).

**Testing**: JUnit 5 + Mockito + Testcontainers (Postgres) para
unit/contract/integration en backend; Jasmine + Karma (scaffold estándar de
Angular CLI) en frontend; golden cases vía skill `ai-eval-harness` para el
componente de IA.

**Target Platform**: contenedores Docker (Linux), orquestados con Docker
Compose; sin requisito de despliegue cloud específico en este slice.

**Project Type**: web application (frontend Angular + backend API Spring
Boot, desacoplados).

**Performance Goals**: <2s para las acciones principales bajo uso normal
(SC-005); sin requisito de alta escala en este slice (ver Scale/Scope).

**Constraints**: RBAC en doble capa con 401/403 diferenciados (Principio II,
FR-017/018); cifrado en tránsito (HTTPS) y en reposo para datos sensibles
(FR-019/020); el componente de IA nunca debe inventar contenido cuando falla
la llamada externa o falta evidencia — mismo comportamiento de "evidencia
insuficiente" en ambos casos (Principio V, ADR-001).

**Scale/Scope**: pilotaje de curso — decenas de usuarios concurrentes,
cientos de órdenes; no se diseña para alta escala (fuera de alcance
justificado, ver Complexity Tracking).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Estado | Cómo se cumple |
|---|---|---|
| I. Spec-Antes-Que-Código | PASS | Este plan se escribe después de spec.md + Clarify + Checklist, todos aprobados y con commits propios previstos. |
| II. RBAC en Doble Capa | PASS | ADR-002 (JWT + `@PreAuthorize` en backend); `contracts/openapi.yaml` declara `x-roles` y 401/403 en cada endpoint sensible. |
| III. Trazabilidad y Verificabilidad Total | PASS (pendiente de completar en Implement) | Cada FR referenciado en `contracts/openapi.yaml`; `docs/traceability.md` se completa con la skill `traceability-matrix` una vez existan los tests. |
| IV. Contrato Antes de Implementar | PASS | `contracts/openapi.yaml` generado en este plan, antes de cualquier código de backend/frontend. |
| V. IA Sin Alucinación | PASS | FR-015/016 + ADR-001 (fallback fail-safe también ante fallo/timeout de la API externa, no solo ante nota vacía). |
| VI. Clean Code Spring Boot + Angular | PASS (a verificar en Implement) | Delegado a `java-expert`/`java-reviewer` y `frontend-expert`/`frontend-reviewer` durante Implement. |
| VII. Entorno Reproducible y Tests en Verde | PASS (a completar en Implement) | `quickstart.md` documenta el comando único de arranque (Docker Compose) y de test; `README.md` se redacta en Implement (pendiente, ya señalado en el Sync Impact Report de la constitution). |
| VIII. Riesgo Técnico y Seguridad en el Plan | PASS | `research.md` contiene 4 ADRs con criterio de revisión, una pasada STRIDE por grupo de endpoints, y criterios de reversión. |

Sin violaciones que requieran justificación — no se rellena Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-order-lifecycle-workflow/
├── plan.md              # este archivo
├── research.md           # Phase 0 — ADRs, STRIDE, criterios de reversión
├── data-model.md         # Phase 1 — entidades y transición de estados
├── quickstart.md         # Phase 1 — guía de validación end-to-end
└── checklists/
    ├── requirements.md    # checklist de Specify
    └── spec-quality.md     # checklist de /speckit.checklist
```

### Repositorio (conforme a Additional Constraints de la constitution)

```text
contracts/
└── openapi.yaml          # contrato de la API (Principio IV) — ya generado

docs/
├── assumptions.md         # supuestos no resolubles solo con el brief
└── traceability.md        # se completa durante Implement (Principio III)

src/
├── backend/               # Spring Boot: controllers, services, repositories,
│                           # entidades JPA, seguridad (JWT + @PreAuthorize),
│                           # cliente de la API de Anthropic
└── frontend/               # Angular: componentes standalone por historia de
                             # usuario, servicios HTTP contra contracts/openapi.yaml,
                             # guards de ruta por rol

tests/
├── unit/                  # backend (JUnit+Mockito) y frontend (Jasmine)
├── contract/               # verifican que la implementación no diverge de
│                           # contracts/openapi.yaml (Principio IV)
└── integration/            # Testcontainers (Postgres) + Angular e2e si aplica

evals/
└── incident-summary/       # golden cases + umbrales (skill ai-eval-harness)
```

**Structure Decision**: web application con backend y frontend desacoplados
bajo un `src/` común (según Additional Constraints de la constitution, no la
Option 2 genérica del template que los pondría en carpetas de nivel raíz
separadas). El contrato vive en `contracts/` a nivel de repositorio (no
anidado en `specs/001-.../contracts/`) para que sea la fuente de verdad única
y fácil de encontrar, consistente con dónde ya vive `docs/assumptions.md`.

## Complexity Tracking

*Sin violaciones de la Constitution Check que requieran justificación —
tabla vacía intencionadamente.*

## Validación del plan (paso 7 del Development Workflow)

Cruce spec↔plan↔contrato↔data-model realizado el 2026-07-10: los 23 FRs y las
6 Success Criteria de `spec.md` tienen su correspondencia en
`contracts/openapi.yaml` (o, para NFRs sin endpoint propio como FR-019,
FR-020 y FR-023, en `research.md`/`data-model.md`, justificado). Sin
marcadores `NEEDS CLARIFICATION` pendientes en `plan.md`, `research.md`,
`data-model.md`, `quickstart.md` ni `contracts/openapi.yaml`. Sin
discrepancias encontradas. Plan listo para `/speckit.tasks`.
