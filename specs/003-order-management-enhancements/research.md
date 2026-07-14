# Research: Gestión de órdenes por email y visualización de evidencia

Decisiones técnicas tomadas durante Plan, con alternativa descartada y motivo,
conforme al Principio VIII de la constitution (ADR con criterio de revisión).

## ADR-005: Identificador de technician en request/response (email, no UUID)

- **Decision**: Todos los endpoints que reciben o devuelven el technician
  responsable de una orden usan su **email** como identificador — nunca su
  UUID interno. En el backend, esto se resuelve con
  `UserRepository.findByEmailIgnoreCase(String)` (nuevo método derivado de
  Spring Data, añadido junto al `findByEmail` ya existente) para la búsqueda
  insensible a mayúsculas/minúsculas (FR-003); en las respuestas
  (`OrderSummaryResponse`/`OrderDetailResponse`), el campo
  `assignedTechnicianId` (UUID) se sustituye por `assignedTechnicianEmail`
  (string), resuelto desde la relación `Order.assignedTechnician` ya
  existente — no requiere columna nueva.
- **Rationale**: Es el objetivo explícito de la feature (spec.md, User Story 1
  y FR-003a): el dispatcher no tiene forma de conocer el UUID de un
  technician en el día a día. Cambiar solo el request y dejar el UUID en la
  respuesta reintroduciría el mismo problema al querer verificar a quién se
  asignó una orden.
- **Alternatives considered**: mantener `assignedTechnicianId` además de
  añadir `assignedTechnicianEmail` (ambos campos) — descartado por
  duplicar información sin necesidad de negocio y por dejar la puerta abierta
  a que el frontend siga leyendo el UUID por costumbre; resolver el email en
  el frontend a partir del UUID vía una llamada adicional — descartado por no
  existir (ni estar pedido) un endpoint de listado de usuarios, y por añadir
  una petición extra sin necesidad.
- **Revisar si**: se añade un endpoint de gestión de usuarios que exponga
  UUIDs de forma legítima a otra parte de la UI (en ese momento, evaluar si
  conviene reintroducir el id junto al email).

## ADR-006: Endpoint de contenido de fotos de evidencia (autenticado, sin presigned URLs)

- **Decision**: Nuevo endpoint `GET /orders/{orderId}/evidence-photos/{photoId}`
  que devuelve el binario de la foto (`Content-Type` dinámico según
  `EvidencePhoto.contentType`, leyendo el archivo desde el volumen Docker ya
  existente por `storagePath`, ADR-004 de la feature 001). Protegido por el
  mismo `@PreAuthorize`/regla de autorización que ya usa
  `GET /orders/{orderId}` (FR-009a): un TECHNICIAN solo puede acceder a fotos
  de una orden que tiene asignada; DISPATCHER/SUPERVISOR, a cualquiera.
- **Rationale**: Reutiliza el almacenamiento y el esquema de autorización ya
  existentes (Principio VI, no sobre-ingeniería); un endpoint autenticado
  simple es suficiente para el volumen de este slice (no hay necesidad de un
  servicio de objetos con URLs firmadas).
- **Alternatives considered**: URLs firmadas de corta duración (presigned
  URLs) servidas directamente por nginx/un CDN — descartado por requerir un
  servicio de almacenamiento de objetos (S3/MinIO) que ADR-004 ya rechazó
  para este alcance; exponer las fotos en una ruta estática sin autenticar
  (más simple) — descartado explícitamente en `/speckit.clarify` (Q3) por
  violar el Principio II (RBAC en doble capa) si alguien comparte o adivina
  la URL.
- **Revisar si**: el volumen de fotos servidas crece lo suficiente como para
  que leer el binario directamente desde el backend (en vez de un CDN/objeto
  con cache) se vuelva un cuello de botella — no es el caso en este slice de
  curso.

## ADR-007: Columna `description` — nullable en BD, obligatoria solo en la creación

- **Decision**: La migración Flyway añade `orders.description` como `TEXT
  NULL` (sin backfill de las 5 filas de seed existentes, que quedan con
  `NULL`). La obligatoriedad (FR-007a) se aplica únicamente en la validación
  del nuevo endpoint `POST /orders` — no como constraint `NOT NULL` a nivel
  de base de datos.
- **Rationale**: Forzar `NOT NULL` a nivel de columna rompería las órdenes de
  seed ya existentes (`V2__seed_data.sql`) sin un valor de relleno artificial
  que no aporta nada real. El requisito de negocio (FR-007a) es "toda orden
  *creada a partir de ahora* debe tener descripción", no "toda orden en el
  sistema debe tener descripción retroactivamente".
- **Alternatives considered**: `NOT NULL` con `DEFAULT ''` y backfill de las
  filas existentes — descartado por introducir un valor de relleno ficticio
  en datos de seed que no representa nada (el brief no pide describir
  retroactivamente las órdenes ya creadas).
- **Revisar si**: surge un requisito de negocio real que dependa de que
  *todas* las órdenes (incluidas las antiguas) tengan descripción no nula.

## ADR-008: Asignación inicial sobre `draft` reutiliza el endpoint de reasignación existente

- **Decision**: `ReassignmentService.REASSIGNABLE_STATUSES` se amplía para
  incluir `OrderStatus.draft` (además de `assigned`, `in_progress`,
  `pending_review`); no se crea un endpoint nuevo para "asignación inicial".
  El mismo `POST /orders/{orderId}/reassignment` cubre ambos casos (FR-004,
  FR-005).
- **Rationale**: Ya estaba documentado como Assumption en `spec.md`
  ("La resolución de conflictos concurrentes ya implementada para la
  reasignación... se extiende sin cambios de diseño al caso de asignación
  inicial"). Reutilizar el mecanismo de bloqueo optimista (`@Version`) y
  reintento (`MAX_ATTEMPTS`) ya probado evita duplicar la lógica de
  concurrencia para un caso que es, en esencia, el mismo (cambiar
  `assignedTechnician` de una orden).
- **Alternatives considered**: endpoint dedicado `POST
  /orders/{orderId}/assignment` para el caso `draft` — descartado por
  duplicar la lógica de resolución de conflictos concurrentes y de
  validación de email sin aportar una diferencia de comportamiento real.
- **Revisar si**: el negocio empieza a requerir reglas distintas para la
  asignación inicial frente a la reasignación (p. ej. permitir que cualquier
  rol la dispare, no solo dispatcher).

## Auditoría unificada de asignación (FR-007c)

`Order.lastReassignedBy` / `lastReassignedAt` (ya existentes, feature 001) se
rellenan en las tres vías que llevan una orden a `assigned`:

1. Creación con technician indicado (`POST /orders`, FR-007b): el propio
   `OrderService.createOrder` fija `lastReassignedBy = currentUser` /
   `lastReassignedAt = now()` si se resolvió un technician.
2. Asignación inicial sobre `draft` (ADR-008): ya cubierto por
   `ReassignmentService`, sin cambios adicionales.
3. Reasignación posterior: comportamiento ya existente, sin cambios.

**Alternatives considered**: dejar la vía 1 sin auditoría (más simple) —
descartado porque deja una asignación real sin autor/fecha, contradiciendo el
Principio III (Trazabilidad Total) y el propio FR-007c introducido en la
revisión estructural de `/speckit.clarify`.

## Seguridad — modelado de amenazas ligero (STRIDE), endpoints nuevos de esta feature

| Endpoint (grupo) | Amenaza relevante | Mitigación → AC de seguridad |
|---|---|---|
| Creación de órdenes (`POST /orders`) | Elevation of Privilege: un technician o supervisor crea órdenes directamente contra la API | `@PreAuthorize("hasRole('DISPATCHER')")` en el controller (FR-008), no solo ocultar el botón en el frontend |
| Creación de órdenes con technician (`POST /orders`, FR-007b) | Tampering: se asigna una orden a un email que no es technician (o no existe) | Misma validación de `findByEmailIgnoreCase` + comprobación de rol que ya usa la reasignación (FR-002), reutilizada aquí |
| Contenido de fotos (`GET .../evidence-photos/{photoId}`) | Information Disclosure: acceso a fotos de una orden ajena conociendo/adivinando el `photoId` | Misma comprobación de autorización que `GET /orders/{orderId}` (FR-009a): se resuelve primero la orden dueña de la foto y se aplica la regla de visibilidad por rol antes de servir el binario |
| Reasignación/asignación por email (`newTechnicianEmail`) | Tampering: se reasigna a un usuario con rol distinto de technician (p. ej. a otro dispatcher) | Igual que FR-002 ya vigente: se valida explícitamente `role == TECHNICIAN`, no solo que el email exista |

## Criterios de reversión

- **Backend/Frontend**: revertir al commit anterior vía git; sin cambios de
  esquema destructivos (la migración de `description` es aditiva y
  nullable).
- **Migración `description`**: si se necesita revertir, una migración Flyway
  posterior (`DROP COLUMN`) es segura porque ninguna fila previa dependía de
  ese dato — no hay pérdida de información de negocio real al quitarla.
- **Endpoint de fotos**: si se detecta un problema de seguridad tras
  integrarlo, se puede desactivar la ruta (o devolver 404 genérico) sin
  afectar al resto del ciclo de vida de la orden, que no depende de poder ver
  las fotos para funcionar (aprobación/rechazo ya funcionaban antes de esta
  feature sin visualización).

## Resueltas: Technical Context

Todas las entradas de Technical Context quedan resueltas reutilizando las
decisiones ya vigentes de la feature 001 (mismo stack, mismo mecanismo de
auth, misma base de datos); las decisiones nuevas de este slice son las
ADR-005 a ADR-008 anteriores. No quedan marcadores `NEEDS CLARIFICATION`
pendientes.
