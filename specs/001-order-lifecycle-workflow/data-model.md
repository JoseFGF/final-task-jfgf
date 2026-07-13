# Data Model: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

Derivado de spec.md (Key Entities) + decisiones de research.md (ADR-003,
ADR-004). Nombres de campo en inglés (convención de código), descripciones en
español.

## User

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID (PK) | |
| `email` | string, único | identificador de login |
| `passwordHash` | string | nunca se expone vía API |
| `role` | enum(`DISPATCHER`, `TECHNICIAN`, `SUPERVISOR`) | exactamente un rol por usuario (Assumption, `docs/assumptions.md`) |
| `fullName` | string | |
| `createdAt` | timestamp | |

**Validación**: `role` es inmutable tras la creación en este slice (no hay
historia de usuario que pida cambiar de rol).

## Order

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID (PK) | |
| `status` | enum(`draft`, `assigned`, `in_progress`, `pending_review`, `closed`) | ver transición de estados abajo |
| `assignedTechnicianId` | FK → User | nulo solo en `draft` |
| `createdAt` / `updatedAt` | timestamp | responde a CHK003 |
| `executionNote` | text, nullable | nota del technician (FR-005) |
| `evidencePhotos` | 1:N → `EvidencePhoto` | al menos 1 requerido para pasar a `pending_review` (FR-006) |
| `rejectionComment` | text, nullable | motivo del último rechazo del supervisor (FR-012, FR-012a); se limpia al aprobarse |
| `lastReassignedBy` / `lastReassignedAt` | FK → User / timestamp, nullable | soporta auditoría de reasignación (research.md, STRIDE) |

### Transición de estados (state machine)

```
draft --(asignación por dispatcher, fuera de alcance de este slice)--> assigned
assigned --(dispatcher reasigna)--> assigned          [FR-013]
assigned --(technician empieza a trabajar, fuera de alcance de este slice)--> in_progress
in_progress --(dispatcher reasigna)--> in_progress    [FR-013]
in_progress --(technician registra ejecución + ≥1 foto)--> pending_review   [FR-005, FR-008]
pending_review --(dispatcher reasigna; NO cambia el status)--> pending_review   [FR-013]
pending_review --(supervisor aprueba)--> closed       [FR-011]
pending_review --(supervisor rechaza + comentario obligatorio)--> in_progress  [FR-012, FR-012a]
closed --(terminal; reasignación rechazada)--                     [FR-014]
```

**Nota**: la transición `draft → assigned` (creación/asignación inicial) no
tiene un FR propio en este slice (ver `docs/assumptions.md`); el modelo la
contempla para no bloquear datos de prueba, pero no hay endpoint dedicado a
ella en `contracts/openapi.yaml`.

## EvidencePhoto

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID (PK) | |
| `orderId` | FK → Order | |
| `storagePath` | string | ruta en el volumen Docker (ADR-004), no la imagen en sí |
| `contentType` | string | `image/jpeg` o `image/png` (Assumption) |
| `sizeBytes` | integer | |
| `uploadedAt` | timestamp | |

## IncidentSummary (no persistida como entidad propia)

El resumen de incidencia (FR-015/FR-016) se genera bajo demanda a partir de
`Order.executionNote` y no se modela como tabla propia en este slice: no hay
requisito de negocio que pida guardar un historial de resúmenes generados
(ver CHK019, aceptado como hueco). Se representa solo como el contrato de
request/response en `contracts/openapi.yaml`.

## Relaciones

- `User (TECHNICIAN)` 1—N `Order` (vía `assignedTechnicianId`)
- `Order` 1—N `EvidencePhoto`
- `User (SUPERVISOR/DISPATCHER)` no tiene FK directa en `Order`: su relación
  es de autorización (Principio II), no de propiedad de datos.

## Auditoría de accesos rechazados (FR-023)

Tabla separada, append-only, sin relación de borrado con `Order`/`User`:

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID (PK) | |
| `attemptedByUserId` | FK → User, nullable | nulo si no había sesión válida |
| `attemptedAction` | string | endpoint/acción intentada |
| `reason` | enum(`NO_SESSION`, `ROLE_NOT_ALLOWED`) | corresponde a 401/403 (FR-017/018) |
| `occurredAt` | timestamp | |
