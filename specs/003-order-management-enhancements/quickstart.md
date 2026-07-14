# Quickstart: Gestión de órdenes por email y visualización de evidencia

Guía de validación end-to-end. No incluye código de implementación — eso vive
en `src/` y `tests/` durante `/speckit.implement`. Referencia
`contracts/openapi.yaml` y `data-model.md` para el detalle de cada operación.
Complementa (no sustituye) `specs/001-order-lifecycle-workflow/quickstart.md`.

## Prerrequisitos

- Entorno ya levantado según el quickstart de la feature 001 (Docker Compose,
  migraciones Flyway aplicadas — incluida la nueva `V4__add_order_description.sql`).
- Usuarios de seed existentes (`dispatcher@fieldops.test`,
  `technician@fieldops.test`, `technician2@fieldops.test`,
  `supervisor@fieldops.test`), todos con password `password123`
  (`V3__seed_login_passwords.sql`).

## Verificar (tests)

```bash
docker compose run --rm backend ./mvnw test
docker compose run --rm frontend npm test
```

Ambos deben terminar en verde antes de considerar cualquier tarea cerrada
(Principio VII).

## Escenarios de validación manual (uno por historia de usuario)

1. **US1 — Asignar/reasignar por email**: como dispatcher, sobre la orden
   seed `a2222222-...` (en `assigned`), llamar a
   `POST /orders/{id}/reassignment` con
   `{"newTechnicianEmail": "Technician2@Fieldops.test"}` (nótese la
   capitalización distinta) y confirmar 200 con la orden reasignada a
   `technician2@fieldops.test`; repetir con un email inexistente y confirmar
   422.
2. **US2 — Asignación inicial sobre `draft`**: como dispatcher, sobre la
   orden seed `a1111111-...` (en `draft`, sin technician), llamar al mismo
   endpoint con un email de technician válido y confirmar que la respuesta
   trae `status: "assigned"`; repetir sobre una orden en `closed` y confirmar
   409. En el frontend, abrir el detalle de una orden `draft` y confirmar que
   la acción de asignar está visible y habilitada (antes de esta feature no
   lo estaba).
3. **US3 — Crear órdenes**: como dispatcher, llamar a `POST /orders` con solo
   `description` y confirmar 201 con `status: "draft"`; repetir aportando
   también `technicianEmail` de un technician válido y confirmar `status:
   "assigned"` con `lastReassignedBy`/`lastReassignedAt` ya fijados; repetir
   sin `description` y confirmar 422; repetir autenticado como technician o
   supervisor y confirmar 403.
4. **US4 — Visualizar fotos de evidencia**: como dispatcher o supervisor,
   abrir el detalle de la orden seed `a4444444-...` (en `pending_review`, con
   una foto ya registrada) y confirmar que la foto se renderiza visualmente
   en la página (no solo un enlace); llamar directamente a
   `GET /orders/{id}/evidence-photos/{photoId}` sin `Authorization` y
   confirmar 401.

## Regresión rápida sobre la feature 001

Repetir los escenarios US1–US5 de
`specs/001-order-lifecycle-workflow/quickstart.md` para confirmar que el
cambio de `newTechnicianId` a `newTechnicianEmail` y de
`assignedTechnicianId` a `assignedTechnicianEmail` no rompió el resto del
ciclo de vida (ejecución, revisión, resumen de IA).
