# Quickstart: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

Guía de validación end-to-end. No incluye código de implementación — eso vive
en `src/` y `tests/` durante `/speckit.implement`. Referencia
`contracts/openapi.yaml` y `data-model.md` para el detalle de cada operación.

## Prerrequisitos

- Docker y Docker Compose instalados.
- Variable de entorno `ANTHROPIC_API_KEY` disponible (para el componente de
  IA, ADR-001 en `research.md`) — nunca commitear su valor.

## Arrancar el entorno

```bash
docker compose up --build
```

Levanta: backend Spring Boot, frontend Angular, PostgreSQL, y aplica las
migraciones (Flyway) con datos de prueba (seed) de los tres roles.

## Verificar (tests)

```bash
docker compose run --rm backend ./mvnw test
docker compose run --rm frontend npm test
```

Ambos deben terminar en verde antes de considerar cualquier tarea cerrada
(Principio VII).

## Escenarios de validación manual (uno por historia de usuario)

1. **US1 — Consultar órdenes**: iniciar sesión como cada uno de los tres
   roles de seed; confirmar que dispatcher y supervisor ven todas las
   órdenes, y que technician solo ve las suyas (`GET /orders`).
2. **US2 — Registrar ejecución**: como technician, sobre una orden seed en
   `in_progress`, llamar a `POST /orders/{id}/execution` sin foto (esperar
   422) y luego con una foto (esperar 200 y estado `pending_review`).
3. **US3 — Aprobar/rechazar**: como supervisor, sobre una orden en
   `pending_review`, llamar a `POST /orders/{id}/review` con
   `decision: REJECT` sin `comment` (esperar 422), luego con `comment`
   (esperar 200, estado `in_progress`, y verificar que el technician ve el
   `rejectionComment` en `GET /orders/{id}`).
4. **US4 — Reasignar**: como dispatcher, `POST /orders/{id}/reassignment`
   sobre una orden `closed` (esperar 409) y sobre una `in_progress` (esperar
   200 y `assignedTechnicianId` actualizado).
5. **US5 — Resumen de IA**: `POST /orders/{id}/incident-summary` sobre una
   orden con nota sustancial (esperar `sufficient: true`) y sobre una con
   nota vacía (esperar `sufficient: false`, `summary: null`).

## Verificación de RBAC (doble capa)

Repetir al menos una llamada de cada endpoint sin token (esperar 401) y con
un token de rol incorrecto (esperar 403) — ver `contracts/openapi.yaml` para
el rol requerido de cada operación.

## Verificación del componente de IA (fail-safe)

Ejecutar las evals de `ai-eval-harness` (`/evals`) y confirmar que el umbral
de aceptación se cumple, con especial atención a que ningún golden case de
"evidencia insuficiente" produzca un resumen inventado (Principio V).

## Medición manual de SC-004 (T064)

**SC-004** ("un technician puede completar el registro de una ejecución,
desde que empieza a escribir la nota hasta que la foto queda subida y
confirmada, en menos de 3 minutos en una sesión de uso normal") es
fundamentalmente una medida de experiencia de usuario end-to-end: incluye el
tiempo que tarda una persona real en escribir la nota, no solo la latencia
del backend (eso ya lo cubre SC-005 con `PerformanceSmokeTest`).

**Estado real en este entorno**: esta tarea (T064) no se ha podido ejecutar
como sesión manual real — este entorno de trabajo no tiene un navegador ni un
dispositivo con cámara disponible para completar el flujo de UI end-to-end
(login como technician → abrir orden `in_progress` → escribir nota → adjuntar
foto → confirmar subida) con cronómetro en mano. No se inventa aquí un
resultado ("<3 minutos, verificado en 3 corridas") que no se obtuvo
realmente. SC-004 queda documentado como **pendiente de ejecución manual**,
no como "cubierto".

Procedimiento exacto que debe seguir una persona con acceso al entorno
Docker completo (`docker compose up --build`) y un dispositivo con cámara
para dejar SC-004 verificado:

1. Iniciar sesión en el frontend como el technician de seed
   (`technician@fieldops.test`).
2. Abrir una orden en estado `in_progress` (p. ej. la orden seed
   `a3333333-3333-3333-3333-333333333333`).
3. Arrancar un cronómetro en el instante exacto en que se empieza a escribir
   en el campo de nota de ejecución.
4. Escribir una nota de ejecución representativa de un caso real (no una
   nota trivial de una palabra — debe reflejar el esfuerzo real de
   documentar una incidencia).
5. Adjuntar al menos una foto de evidencia (puede ser una foto tomada en el
   momento con el dispositivo, para simular el flujo real de campo).
6. Confirmar el envío (`POST /orders/{id}/execution`) y esperar la
   confirmación de éxito en la UI (estado `pending_review`).
7. Detener el cronómetro en el instante en que la UI confirma que la foto
   quedó subida y la orden pasó a `pending_review`.
8. Repetir los pasos 2-7 al menos 3 veces (en sesiones separadas, no
   consecutivas sin pausa, para reflejar "uso normal" y no un intento
   optimizado tras práctica repetida), registrando cada tiempo.
9. Registrar aquí (reemplazando esta nota) los 3 tiempos obtenidos. SC-004 se
   considera cumplido solo si las 3 corridas quedan por debajo de 3 minutos;
   si alguna corrida supera el umbral, investigar qué paso de la UI/flujo
   introduce la demora antes de dar la tarea por cerrada.
