# Data Model: Transiciones de estado de la orden por rol

Derivado de `spec.md` (Key Entities) + decisiones de `research.md` (ADR-009 a
ADR-011). Extiende el modelo ya definido en
`specs/001-order-lifecycle-workflow/data-model.md` y
`specs/003-order-management-enhancements/data-model.md`; no lo sustituye.
Sin cambios de esquema (sin migración Flyway): esta feature es puramente de
comportamiento sobre columnas ya existentes.

## Order (sin cambios de columnas)

Se documenta aquí, por primera vez de forma completa, la tabla de
transiciones reconocidas del ciclo de vida (antes repartida implícitamente
entre varias features):

| Origen | Destino | Quién | Requisito adicional |
|---|---|---|---|
| `draft` | `assigned` | Dispatcher (asigna por email, feature 003) o Dispatcher/Supervisor (corrección manual, FR-004) | Debe indicarse un technician válido al pasar a `assigned` |
| `assigned` | `draft` | Dispatcher/Supervisor (corrección manual, FR-004) | El technician asignado se desvincula (queda `NULL`) |
| `assigned` | `in_progress` | Technician asignado (FR-001, **nuevo**) o Dispatcher/Supervisor (FR-004) | Ninguno |
| `in_progress` | `assigned` | Dispatcher/Supervisor (corrección manual, FR-004) | Ninguno |
| `in_progress` | `pending_review` | Technician (registro de ejecución, feature 001) o Dispatcher/Supervisor (FR-004) | Al menos 1 foto de evidencia ya registrada (FR-005) |
| `pending_review` | `in_progress` | Supervisor (rechazo con comentario, feature 001) o Dispatcher/Supervisor (corrección manual sin comentario, FR-004) | Ninguno |
| `pending_review` | `closed` | Supervisor (aprobación, feature 001) o Dispatcher/Supervisor (corrección manual, FR-004) | Al menos 1 foto de evidencia ya registrada (heredado de FR-005, ya cumplido si llegó a `pending_review`) |
| `closed` | *(ninguno)* | — | `closed` es terminal; ninguna transición sale de este estado (FR-006) |

**Nota de diseño (ADR-010)**: esta tabla es la fuente de verdad única de qué
pares se consideran "adyacentes" — cualquier par no listado aquí (p. ej.
`draft→in_progress`, `draft→closed`, `assigned→pending_review`,
`assigned→closed`, `in_progress→closed`, o cualquier transición que empiece
en `closed`) se rechaza como transición no reconocida.

## Relaciones y campos existentes reutilizados (sin cambios)

- `Order.assignedTechnician`: se limpia (`NULL`) cuando la corrección manual
  mueve la orden hacia atrás hasta `draft` (ver tabla arriba); se conserva
  sin cambios en cualquier otra transición.
- `Order.evidencePhotos`: se consulta (no se modifica) para validar el
  requisito de evidencia mínima antes de `pending_review`, sea cual sea la
  vía (registro de ejecución o corrección manual).
- `Order.version`: bloqueo optimista ya existente (feature 001), reutilizado
  sin cambios para resolver cambios de estado concurrentes (FR-008,
  ADR-011).
- **Sin campo de auditoría nuevo**: por decisión explícita de
  `/speckit.clarify` (FR-009), ni "iniciar trabajo" ni la corrección manual
  registran quién ni cuándo, a diferencia de `lastReassignedBy`/
  `lastReassignedAt` (feature 003), que son exclusivos de la reasignación de
  technician y no se tocan en esta feature.

## Representación en API (DTOs) — cambio de contrato

Nuevo `OrderStatusChangeRequest` (`contracts/openapi.yaml`): `{"status":
"<uno de los 5 valores de OrderStatus>"}`. No se modifica
`OrderSummaryResponse` ni `OrderDetailResponse` (ya exponen `status`, que es
lo único que cambia).
