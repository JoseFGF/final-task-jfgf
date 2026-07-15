# Quickstart: Transiciones de estado de la orden por rol

Guía de validación end-to-end. No incluye código de implementación — eso
vive en `src/` y `tests/` durante `/speckit.implement`. Referencia
`contracts/openapi.yaml` y `data-model.md` para el detalle de cada
operación. Complementa (no sustituye) los quickstarts de las features
001 y 003.

## Prerrequisitos

- Entorno ya levantado (Docker Compose), migraciones Flyway aplicadas (esta
  feature no añade ninguna nueva).
- Usuarios de seed existentes (`dispatcher@fieldops.test`,
  `technician@fieldops.test`, `technician2@fieldops.test`,
  `supervisor@fieldops.test`), password `password123`.

## Verificar (tests)

```bash
docker compose run --rm backend ./mvnw test
docker compose run --rm frontend npm test
```

## Escenarios de validación manual (uno por historia de usuario)

1. **US1 — Technician inicia el trabajo**: como `technician@fieldops.test`,
   sobre una orden `assigned` a él, `POST /orders/{id}/status` con
   `{"status": "in_progress"}` → 200, `status` pasa a `in_progress`.
   Repetir como `technician2@fieldops.test` (no asignado a esa orden) →
   403. Repetir sobre una orden que ya está `in_progress` → 409.
2. **US2 — Corrección manual de dispatcher/supervisor**: como
   `dispatcher@fieldops.test`, sobre una orden `assigned`, `POST
   /orders/{id}/status` con `{"status": "in_progress"}` → 200; sobre una
   orden `in_progress` sin fotos, `{"status": "pending_review"}` → 422;
   sobre una orden `in_progress` con al menos 1 foto ya registrada,
   `{"status": "pending_review"}` → 200; sobre una orden `assigned`,
   `{"status": "draft"}` → 200 y el technician queda desvinculado; sobre
   una orden `draft`, `{"status": "closed"}` (salto no adyacente) → 409;
   sobre una orden `closed`, cualquier `status` → 409.

## Regresión rápida sobre las features 001 y 003

Repetir los escenarios de reasignación por email (003) y de
ejecución/revisión (001) para confirmar que el nuevo endpoint no interfiere
con los flujos ya existentes (en particular, que el registro de ejecución
del technician sigue siendo la vía normal hacia `pending_review`, y que la
aprobación/rechazo del supervisor sigue funcionando igual).
