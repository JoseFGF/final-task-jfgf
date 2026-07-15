# Research: Transiciones de estado de la orden por rol

Decisiones técnicas tomadas durante Plan, con alternativa descartada y motivo,
conforme al Principio VIII de la constitution (ADR con criterio de revisión).

## ADR-009: Un único endpoint genérico de cambio de estado, con reglas por rol

- **Decision**: Se añade `POST /orders/{orderId}/status` (body: `{"status":
  "<estado destino>"}`), usado tanto por el technician (US1) como por
  dispatcher/supervisor (US2), en vez de dos endpoints separados
  (`/start` + `/manual-status`). La lógica de negocio decide qué transición
  es válida según el rol de quien la solicita:
  - `TECHNICIAN`: únicamente `assigned → in_progress`, y solo si es el
    technician asignado a esa orden (FR-001 a FR-003).
  - `DISPATCHER`/`SUPERVISOR`: cualquier par de estados **adyacentes** en la
    cadena `draft ↔ assigned ↔ in_progress ↔ pending_review → closed`
    (FR-004), sujeto a FR-005 (evidencia mínima antes de `pending_review`) y
    FR-006 (`closed` terminal).
- **Rationale**: Ambas historias de usuario son, en el fondo, la misma
  operación de dominio ("cambiar el estado de una orden"), solo que con un
  conjunto de transiciones permitidas distinto según el rol. Un único
  endpoint con una tabla de adyacencia compartida evita definir la regla de
  "qué transiciones existen" en dos sitios que podrían divergir con el
  tiempo.
- **Alternatives considered**: dos endpoints separados
  (`POST .../start` para el technician, `POST .../reassignment-status` o
  similar para dispatcher/supervisor) — descartado porque duplicaría la
  tabla de adyacencia y el criterio de concurrencia (FR-008) en dos
  servicios distintos, con riesgo real de que diverjan al mantenerlos.
- **Revisar si**: se necesita en el futuro una transición que rompa el
  patrón "un solo estado destino por rol" (p. ej. que el technician también
  pueda hacer una corrección limitada), momento en el que un único endpoint
  genérico podría quedarse corto.

## ADR-010: Tabla de adyacencia explícita, no un grafo de estados genérico

- **Decision**: Las transiciones válidas para dispatcher/supervisor se
  codifican como un conjunto fijo de pares adyacentes (`draft↔assigned`,
  `assigned↔in_progress`, `in_progress↔pending_review`,
  `pending_review→closed` en un solo sentido), no como una regla genérica
  "cualquier estado a cualquier estado" ni como un motor de máquina de
  estados configurable.
- **Rationale**: Es exactamente la resolución de la ambigüedad detectada en
  `/speckit.checklist` (CHK001/CHK007): `FR-004` decía "cualquier dirección"
  pero el Edge Case ya rechazaba explícitamente `draft→closed`. Una tabla
  fija y pequeña (5 pares) es suficiente para el alcance actual y evita
  construir una abstracción de máquina de estados genérica que nadie ha
  pedido.
- **Alternatives considered**: motor de máquina de estados configurable
  (declarar transiciones en base de datos o config) — descartado por
  sobre-ingeniería para 5 estados fijos que no van a cambiar con frecuencia.
- **Revisar si**: se añaden nuevos estados al ciclo de vida de la orden (hoy
  son 5, fijos desde la feature 001).

## ADR-011: Reutilizar el mecanismo de bloqueo optimista ya existente (FR-008)

- **Decision**: El nuevo servicio de cambio de estado reutiliza exactamente
  el mismo patrón ya usado por `ReassignmentService` (bloqueo optimista vía
  `Order.version` + reintento con auto-referencia `@Lazy` para pasar por el
  interceptor `@Transactional` en la re-invocación).
- **Rationale**: Es el mismo tipo de operación (leer orden, validar, escribir
  un cambio) con el mismo riesgo de concurrencia (dos personas cambiando el
  estado de la misma orden a la vez); reutilizar un patrón ya probado evita
  introducir una segunda estrategia de concurrencia en el mismo dominio.
- **Alternatives considered**: bloqueo pesimista (`SELECT ... FOR UPDATE`) —
  descartado por ser un cambio de estrategia de concurrencia no justificado
  para este slice, cuando el patrón optimista ya existente cubre el mismo
  caso de uso.

## Seguridad — modelado de amenazas ligero (STRIDE), endpoint nuevo de esta feature

| Endpoint (grupo) | Amenaza relevante | Mitigación → AC de seguridad |
|---|---|---|
| `POST /orders/{orderId}/status` (technician) | Elevation of Privilege: un technician intenta iniciar el trabajo de una orden que no tiene asignada, o mover a un estado distinto de `in_progress` | Validación explícita de propiedad (technician asignado) y de transición exacta (`assigned→in_progress`) en el servicio, no solo en el frontend (FR-002, FR-003) |
| `POST /orders/{orderId}/status` (dispatcher/supervisor) | Tampering: se intenta saltar un estado intermedio (p. ej. `draft→closed`) o reabrir una orden `closed` | Tabla de adyacencia fija (ADR-010) validada en el servicio; `closed` nunca aparece como origen válido (FR-006) |
| `POST /orders/{orderId}/status` (cualquier rol) | Tampering: se fuerza `pending_review` sin evidencia registrada, saltándose el control ya exigido al technician en el registro de ejecución | Misma comprobación de evidencia mínima (FR-005) reutilizada, no una regla nueva y potencialmente distinta |

## Criterios de reversión

- **Backend/Frontend**: revertir al commit anterior vía git; sin migraciones
  de base de datos en este slice (no se añade ninguna columna nueva — FR-009
  descarta explícitamente la auditoría, así que no hace falta un campo de
  auditoría que revertir).
- **Endpoint nuevo**: si se detecta un problema, se puede desactivar la ruta
  sin afectar al resto del ciclo de vida (las transiciones ya existentes
  —ejecución, revisión, reasignación— no dependen de este endpoint para
  seguir funcionando).

## Resueltas: Technical Context

Todas las entradas de Technical Context quedan resueltas reutilizando las
decisiones ya vigentes de las features 001/003 (mismo stack, misma base de
datos, mismo mecanismo de auth); las decisiones nuevas de este slice son las
ADR-009 a ADR-011 anteriores. No quedan marcadores `NEEDS CLARIFICATION`
pendientes.
