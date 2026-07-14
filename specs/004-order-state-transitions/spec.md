# Feature Specification: Transiciones de estado de la orden por rol

**Feature Branch**: `004-order-state-transitions`

**Created**: 2026-07-14

**Status**: Draft

**Input**: User description: "haz que se pueda pasar de un estado a otro, un tecnico asignado puede pasar de asignado a en curso y pendiente revision y el supervisor o dispacher puede hacerlo todo"

## Clarifications

### Session 2026-07-14

- Q: ¿Qué tan permisivo es "el supervisor o dispatcher puede hacerlo todo" al cambiar manualmente el estado de una orden? → A: Restrictivo — dispatcher/supervisor mueven la orden solo entre los 5 estados ya definidos, respetando la evidencia mínima antes de `pending_review` y `closed` como terminal, sin excepciones.
- Q: ¿Deben auditarse (quién y cuándo) las dos acciones nuevas de esta feature (iniciar trabajo, corrección manual de estado)? → A: No — ninguna de las dos queda auditada; solo se refleja el nuevo estado de la orden, igual que el resto de transiciones automáticas ya existentes que tampoco registran autor/fecha (p. ej. aprobar/rechazar en revisión).
- Q: ¿La corrección manual de dispatcher/supervisor (FR-004) puede mover la orden hacia atrás en su ciclo de vida (no solo hacia adelante)? → A: Sí, bidireccional — puede mover la orden en cualquier dirección entre los 5 estados, sujeto a las mismas reglas de integridad ya definidas (evidencia mínima antes de `pending_review`, `closed` terminal).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El technician inicia el trabajo de una orden asignada (Priority: P1)

Como technician con una orden asignada, quiero marcar que empiezo a trabajarla, para que la orden refleje que ya está en curso y pueda registrar su ejecución cuando termine.

**Why this priority**: Cierra un hueco bloqueante del ciclo de vida: hoy ninguna acción real lleva una orden de `assigned` a `in_progress` (solo llega a ese estado por datos de seed o tras un rechazo del supervisor), así que ninguna orden recién asignada puede avanzar nunca por su ciclo normal.

**Independent Test**: Se puede probar completamente tomando una orden en estado `assigned` con un technician asignado, iniciando sesión como ese technician, marcándola como iniciada, y verificando que pasa a `in_progress`.

**Acceptance Scenarios**:

1. **Given** una orden en estado `assigned` asignada a un technician, **When** ese technician marca que empieza a trabajarla, **Then** la orden pasa a estado `in_progress`.
2. **Given** una orden en estado `assigned` asignada a otro technician distinto, **When** un technician que no es el asignado intenta marcarla como iniciada, **Then** el sistema rechaza la solicitud.
3. **Given** una orden en un estado distinto de `assigned` (p. ej. `draft`, `in_progress`, `pending_review` o `closed`), **When** el technician asignado intenta marcarla como iniciada, **Then** el sistema rechaza la solicitud (esta acción solo aplica desde `assigned`).
4. **Given** una orden en estado `assigned`, **When** un dispatcher o supervisor (no el technician) intenta usar esta acción de "iniciar trabajo", **Then** el sistema rechaza la solicitud (esta acción específica es solo del technician asignado; ver User Story 2 para las capacidades de dispatcher/supervisor).

---

### User Story 2 - Dispatcher y supervisor corrigen manualmente el estado de una orden (Priority: P2)

Como dispatcher o supervisor, quiero poder corregir manualmente el estado de una orden cuando haga falta (por ejemplo, si un technician no puede usar la app pero ya empezó a trabajar en campo, o para deshacer un error de estado), para no depender exclusivamente de las acciones automáticas de cada rol.

**Why this priority**: Es una herramienta de corrección/soporte que mejora la operación diaria, pero no bloquea el ciclo de vida principal (User Story 1 ya permite que las órdenes avancen por su camino normal sin esta capacidad).

**Independent Test**: Se puede probar completamente iniciando sesión como dispatcher o como supervisor, tomando una orden en cualquier estado no terminal, cambiándola manualmente a otro estado válido, y verificando que el cambio se aplica.

**Acceptance Scenarios**:

1. **Given** una orden en estado `assigned`, **When** un dispatcher o un supervisor la cambia manualmente a `in_progress`, **Then** la orden pasa a `in_progress` (mismo resultado que si lo hiciera el technician en la User Story 1).
2. **Given** una orden en estado `in_progress` con nota de ejecución y al menos una foto de evidencia ya registradas, **When** un dispatcher o un supervisor la cambia manualmente a `pending_review`, **Then** la orden pasa a `pending_review`.
3. **Given** una orden en estado `in_progress` sin ninguna foto de evidencia registrada todavía, **When** un dispatcher o un supervisor intenta cambiarla manualmente a `pending_review`, **Then** el sistema rechaza la solicitud (no se salta el requisito de evidencia mínima ya exigido al technician).
4. **Given** una orden en cualquier estado, **When** un technician (no dispatcher ni supervisor) intenta usar esta capacidad de cambio manual de estado, **Then** el sistema rechaza la solicitud.
5. **Given** una orden en estado `closed`, **When** un dispatcher o supervisor intenta cambiarla manualmente a cualquier otro estado, **Then** el sistema rechaza la solicitud (`closed` sigue siendo terminal).
6. **Given** una orden en estado `pending_review`, **When** un dispatcher o un supervisor la cambia manualmente hacia atrás a `in_progress` (sin pasar por el flujo de rechazo con comentario obligatorio), **Then** la orden pasa a `in_progress`.
7. **Given** una orden en estado `assigned` con un technician asignado, **When** un dispatcher o un supervisor la cambia manualmente hacia atrás a `draft`, **Then** la orden pasa a `draft` y queda sin technician asignado (consistente con que `draft` representa una orden sin asignar).
8. **Given** una orden en estado `pending_review` con nota de ejecución y al menos una foto de evidencia ya registradas, **When** un dispatcher o un supervisor la cambia manualmente a `closed`, **Then** la orden pasa a `closed` (mismo resultado que si el supervisor la aprobara vía revisión, pero sin dejar un comentario asociado).
9. **Given** una orden en estado `draft`, **When** un dispatcher o un supervisor intenta cambiarla manualmente a `in_progress` o a `pending_review` (saltándose `assigned`), **Then** el sistema rechaza la solicitud por no ser una transición entre estados adyacentes.

### Edge Cases

- ¿Qué ocurre si dos personas (p. ej. el technician asignado y un dispatcher) intentan cambiar el estado de la misma orden al mismo tiempo? Debe aplicarse el mismo criterio de resolución de conflictos concurrentes ya usado para la reasignación (gana el último cambio válido procesado).
- ¿Qué ocurre si un dispatcher o supervisor intenta un cambio de estado que no corresponde a ninguna transición reconocida del ciclo de vida (p. ej. de `draft` directamente a `closed`, o de `draft` directamente a `in_progress`, saltándose `assigned`)? El sistema debe rechazarlo: la corrección manual solo mueve la orden entre pares de estados adyacentes (ver FR-004), nunca saltando estados intermedios.
- ¿Qué ocurre si el technician asignado a una orden deja de estar asignado (fue reasignada) justo antes de marcarla como iniciada? El sistema debe evaluar la asignación vigente en el momento de la solicitud, no una copia obsoleta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al technician asignado a una orden en estado `assigned` marcarla como iniciada, transicionándola a `in_progress`.
- **FR-002**: El sistema DEBE rechazar la acción de "iniciar trabajo" si quien la solicita no es el technician actualmente asignado a esa orden.
- **FR-003**: El sistema DEBE rechazar la acción de "iniciar trabajo" si la orden no está en estado `assigned`.
- **FR-004**: El sistema DEBE permitir a un dispatcher o a un supervisor cambiar manualmente el estado de una orden, en cualquier dirección, únicamente entre pares de estados **adyacentes** en su ciclo de vida ya definido — es decir, siguiendo la misma cadena `draft ↔ assigned ↔ in_progress ↔ pending_review → closed` que ya existe, sin saltarse ningún estado intermedio (p. ej. `draft → in_progress` o `draft → closed` NO son transiciones reconocidas, aunque ambos sean estados válidos). `closed` conserva su carácter de solo-entrada: se alcanza únicamente desde `pending_review`, y ninguna transición manual puede sacar una orden de `closed` (FR-006). Sujeto además a las mismas reglas de integridad de datos que ya aplican a las transiciones automáticas (FR-005, FR-006).
- **FR-005**: El sistema DEBE seguir exigiendo que una orden tenga al menos una foto de evidencia registrada antes de poder quedar en estado `pending_review`, sin importar si la transición la dispara el technician (registrando su ejecución) o un dispatcher/supervisor (cambio manual).
- **FR-006**: El sistema DEBE seguir tratando `closed` como estado terminal: ningún rol (incluidos dispatcher y supervisor) puede cambiar manualmente una orden que ya está `closed` a otro estado.
- **FR-007**: El sistema DEBE rechazar cualquier intento de cambio manual de estado solicitado por un technician (el cambio manual de estado es exclusivo de dispatcher y supervisor; el technician solo cuenta con la acción específica de FR-001).
- **FR-008**: El sistema DEBE resolver los cambios de estado concurrentes sobre la misma orden con el mismo criterio ya usado para la reasignación (gana el último cambio válido procesado).
- **FR-009**: El sistema NO DEBE registrar auditoría de autor/fecha para la acción de "iniciar trabajo" (FR-001) ni para el cambio manual de estado (FR-004); ambas solo actualizan el estado de la orden, igual que el resto de transiciones automáticas ya existentes que tampoco dejan ese rastro (p. ej. aprobar/rechazar en revisión).

### Key Entities

- **Orden (Order)**: unidad de trabajo de campo, ya existente. Se amplía su ciclo de vida para que la transición `assigned` → `in_progress` tenga, por primera vez, una acción real que la dispare (antes solo existía como dato de seed o como consecuencia de un rechazo del supervisor desde `pending_review`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las órdenes en `assigned` pueden pasar a `in_progress` mediante una acción explícita del technician asignado, sin depender de datos de seed ni de un rechazo previo del supervisor.
- **SC-002**: El 100% de los intentos de iniciar o cambiar de estado una orden por un rol o technician no autorizado son rechazados.
- **SC-003**: El 100% de los cambios manuales de estado hechos por dispatcher/supervisor hacia `pending_review` respetan el requisito de al menos una foto de evidencia ya registrada.
- **SC-004**: El 100% de las órdenes en `closed` permanecen en `closed` frente a cualquier intento de cambio manual de estado.
- **SC-005**: El 100% de los intentos de cambio manual de estado que saltan un estado intermedio (no adyacente) son rechazados, sin importar la dirección.

## Assumptions

- "El supervisor o dispatcher puede hacerlo todo" se interpreta como: pueden mover manualmente una orden entre los estados ya definidos en su ciclo de vida existente (los mismos cinco estados, las mismas transiciones ya reconocidas), no como una capacidad para inventar transiciones nuevas ni para saltarse los requisitos de integridad de datos ya exigidos (evidencia mínima antes de `pending_review`, `closed` como terminal). Si se necesitara un modo de corrección que sí ignore esos requisitos (p. ej. cerrar una orden sin evidencia por un caso excepcional), sería una decisión de negocio distinta que no se pudo inferir solo del brief.
- La acción de "iniciar trabajo" (FR-001) no exige ningún dato adicional (nota, foto): es un cambio de estado puro, a diferencia de registrar la ejecución (que sí exige nota + evidencia) para pasar de `in_progress` a `pending_review`.
- La transición `in_progress` → `pending_review` sigue ocurriendo, para el technician, exclusivamente a través del registro de ejecución ya existente (nota + evidencia); esta feature no añade un segundo camino sin evidencia para que el technician llegue a `pending_review`.
- El cambio manual de estado por dispatcher/supervisor hacia `pending_review` reutiliza la misma orden ya existente (con su nota/evidencia ya registradas por el technician); no permite crear una nota o evidencia nueva en el mismo paso.
- Si el cambio manual mueve una orden hacia atrás hasta `draft`, el technician que tuviera asignado se desvincula (queda sin technician), porque `draft` ya representa por definición una orden sin asignar (consistente con el modelo de datos existente desde la feature 001). Mover hacia atrás a cualquier otro estado (`assigned`, `in_progress`, `pending_review`) conserva el technician ya asignado sin cambios.
