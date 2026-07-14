# Data Model: Gestión de órdenes por email y visualización de evidencia

Derivado de `spec.md` (Key Entities) + decisiones de `research.md` (ADR-005 a
ADR-008). Extiende el modelo ya definido en
`specs/001-order-lifecycle-workflow/data-model.md`; no lo sustituye.

## Order (cambios sobre el modelo existente)

| Campo | Tipo | Notas |
|---|---|---|
| `description` | text, **nullable en BD** | **Nuevo** (FR-007, FR-007a). Obligatorio a nivel de validación solo en `POST /orders`; las órdenes existentes (seed) quedan con `NULL` (ADR-007) — no se retrofitea. |
| `assignedTechnicianId` | FK → User | Sin cambios en el modelo de persistencia (sigue siendo la relación real). Lo que cambia es **cómo se expone**: ver `assignedTechnicianEmail` más abajo. |
| `lastReassignedBy` / `lastReassignedAt` | FK → User / timestamp, nullable | Ya existente (feature 001). Se amplía su alcance: ahora se rellena también al crear una orden con technician indicado (FR-007b), no solo al reasignar (FR-007c). |

**Sin cambios** en el resto de campos (`id`, `status`, `executionNote`,
`rejectionComment`, `evidencePhotos`, `createdAt`/`updatedAt`, `version`) —
ver `specs/001-order-lifecycle-workflow/data-model.md` para su definición
completa.

### Transición de estados (cambios)

```
draft --(dispatcher asigna un technician por email — FR-004/FR-005)--> assigned
draft --(creación con technician indicado — FR-007b)--> assigned   [origen alternativo, mismo estado destino]
```

El resto de la máquina de estados (`assigned` → `in_progress` → …) no cambia
respecto a la feature 001.

## User (sin cambios de esquema)

Sin cambios de columnas. Cambia su **rol operativo**: `email` (ya único,
`specs/001.../data-model.md`) pasa a ser también el identificador que usa el
dispatcher para referirse a un technician en `POST /orders` y en
`POST /orders/{orderId}/reassignment` (FR-001, FR-007b), en vez de su `id`
(UUID). Requiere el método derivado `UserRepository.findByEmailIgnoreCase`
(ADR-005) para la búsqueda insensible a mayúsculas/minúsculas (FR-003).

## EvidencePhoto (sin cambios de esquema)

Sin cambios de columnas (ver `specs/001-order-lifecycle-workflow/data-model.md`
para su definición completa: `id`, `orderId`, `storagePath`, `contentType`,
`sizeBytes`, `uploadedAt`). Se añade la capacidad de **leer** su contenido
binario (`GET /orders/{orderId}/evidence-photos/{photoId}`, ADR-006), no de
crearla ni modificarla desde este slice.

## Representación en API (DTOs) — cambio de contrato

`OrderSummaryResponse` / `OrderDetailResponse` (`contracts/openapi.yaml`):

- Se **sustituye** `assignedTechnicianId` (UUID) por `assignedTechnicianEmail`
  (string, nullable) — FR-003a. Resuelto desde la relación
  `Order.assignedTechnician.email` ya existente; no requiere columna nueva.
- Se **añade** `description` (string) a `OrderDetail` (y a `OrderSummary`,
  para que el listado de órdenes sea útil sin tener que abrir cada detalle).
- `OrderDetail` añade `evidencePhotoIds` ya existente sin cambios; el
  contenido de cada foto se obtiene con una petición aparte al nuevo
  endpoint (no se embebe como base64 en el JSON del detalle, para no
  penalizar el tamaño de la respuesta cuando hay varias fotos).

## Relaciones

Sin cambios respecto a `specs/001-order-lifecycle-workflow/data-model.md`:

- `User (TECHNICIAN)` 1—N `Order` (vía `assignedTechnicianId`)
- `Order` 1—N `EvidencePhoto`
- `User (SUPERVISOR/DISPATCHER)` sin FK directa en `Order`.

## Migración Flyway

`V4__add_order_description.sql`:

```sql
ALTER TABLE orders ADD COLUMN description TEXT NULL;
```

Aditiva y no destructiva (ADR-007); reversible con un `DROP COLUMN` sin
pérdida de información de negocio real, ya que ninguna fila previa dependía
de este dato.
