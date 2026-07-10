# Feature Specification: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

**Feature Branch**: `001-order-lifecycle-workflow`

**Created**: 2026-07-10

**Status**: Draft

**Input**: User description: notas de reunión del product owner sobre FieldOps, una
plataforma de gestión de órdenes de trabajo para técnicos de campo, con tres roles
(dispatcher, technician, supervisor) y un ciclo de estados
`draft → assigned → in_progress → pending_review → closed`.

**Fuera de alcance de este spec** (excluido explícitamente por el usuario y por el
propio brief, que los aplaza en la misma frase): el dashboard de métricas de
productividad y las notificaciones push a técnicos. Ambos se tratan como una
iniciativa aparte, no como parte de este slice.

## Clarifications

### Session 2026-07-10

- Q: FR-002/FR-003 dejan abierto si dispatcher y supervisor ven todas las
  órdenes del sistema o un subconjunto acotado. → A: Dispatcher y supervisor
  ven TODAS las órdenes del sistema, sin importar estado ni technician
  asignado.
- Q: El brief exige que el sistema sea "seguro" al manejar datos de cliente,
  pero eso solo estaba cubierto por RBAC, sin exigencia de cifrado. → A: Se
  exige explícitamente cifrado en tránsito (HTTPS) y en reposo para los datos
  sensibles (evidencia fotográfica y notas de ejecución).
- Q: ¿Cuánto tiempo se conservan la evidencia fotográfica y las notas de
  ejecución tras cerrarse una orden? → A: Se conservan indefinidamente
  mientras la orden exista en el sistema; no hay borrado automático en este
  slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar mis órdenes (Priority: P1)

Cualquier usuario autenticado (dispatcher, technician o supervisor) necesita ver
las órdenes que le corresponden según su rol, para saber qué trabajo tiene
pendiente sin depender de que alguien se lo comunique aparte.

**Why this priority**: Es la base sobre la que se apoyan el resto de historias —
nadie puede ejecutar, reasignar o revisar una orden que no puede ver primero. Sin
esto no hay MVP posible.

**Independent Test**: Con datos de prueba sembrados (órdenes en distintos estados
asignadas a distintos usuarios), cada rol inicia sesión y comprueba que ve
exactamente las órdenes que le corresponden y ninguna otra.

**Acceptance Scenarios**:

1. **Given** un technician con órdenes asignadas en distintos estados, **When**
   consulta su lista de órdenes, **Then** ve todas las órdenes que tiene
   asignadas, con su estado actual visible.
2. **Given** un dispatcher, **When** consulta la lista de órdenes, **Then** ve
   todas las órdenes del sistema, sin importar su estado o a qué technician
   estén asignadas.
3. **Given** un supervisor, **When** consulta la lista de órdenes, **Then** ve
   también todas las órdenes del sistema, con las que están en
   `pending_review` claramente distinguibles como pendientes de su revisión.
4. **Given** cualquier usuario, **When** consulta el detalle de una orden que no
   le corresponde según su rol, **Then** el sistema rechaza el acceso.

---

### User Story 2 - Registrar la ejecución de una orden (Priority: P1)

Un technician que ha completado el trabajo en campo necesita registrar esa
ejecución, incluyendo al menos una foto como evidencia, para que la orden avance
a revisión.

**Why this priority**: Es la acción que genera el trabajo real del sistema y la
que dispara todo el resto del ciclo (revisión, aprobación). Es, junto con la
consulta de órdenes, el núcleo mínimo demostrable del producto.

**Independent Test**: Con una orden en estado `in_progress` asignada a un
technician, este registra la ejecución adjuntando una foto y se verifica que la
orden pasa a `pending_review` con la nota y la evidencia guardadas.

**Acceptance Scenarios**:

1. **Given** una orden en estado `in_progress` asignada al technician, **When**
   registra la ejecución adjuntando al menos una foto de evidencia, **Then** la
   orden pasa a estado `pending_review` y queda guardada la nota junto con la
   evidencia.
2. **Given** una orden en estado `in_progress`, **When** el technician intenta
   registrar la ejecución sin adjuntar ninguna foto, **Then** el sistema rechaza
   el registro y no cambia el estado de la orden.
3. **Given** una orden que no está en estado `in_progress` (por ejemplo `draft` o
   `closed`), **When** el technician intenta registrar una ejecución sobre ella,
   **Then** el sistema rechaza la operación.
4. **Given** una orden asignada a otro technician, **When** un technician distinto
   intenta registrar la ejecución, **Then** el sistema rechaza la operación.
5. **Given** una orden que volvió a `in_progress` tras un rechazo del
   supervisor, **When** el technician la consulta para corregirla, **Then**
   ve el comentario de motivo de rechazo junto a la nota original, y al
   volver a registrar la ejecución la orden pasa de nuevo a `pending_review`.

---

### User Story 3 - Aprobar o rechazar una orden en revisión (Priority: P2)

Un supervisor necesita revisar una orden en `pending_review` y decidir si el
trabajo registrado es correcto (aprobarla) o no (rechazarla), para cerrar el
ciclo de calidad antes de dar el trabajo por bueno.

**Why this priority**: Cierra el ciclo de valor iniciado por la ejecución (US2);
sin esta historia, ninguna orden llega nunca a un estado definitivo.

**Independent Test**: Con una orden sembrada directamente en `pending_review`
(sin depender de que US2 se haya ejecutado antes), un supervisor la aprueba o la
rechaza y se verifica el cambio de estado resultante.

**Acceptance Scenarios**:

1. **Given** una orden en `pending_review`, **When** el supervisor la aprueba,
   **Then** la orden pasa a estado `closed`.
2. **Given** una orden en `pending_review`, **When** el supervisor la rechaza
   adjuntando un comentario explicando el motivo, **Then** la orden vuelve a
   estado `in_progress`, guardando ese comentario, para que el mismo
   technician corrija la ejecución y pueda volver a enviarla a revisión.
3. **Given** una orden en `pending_review`, **When** el supervisor intenta
   rechazarla sin adjuntar ningún comentario, **Then** el sistema rechaza la
   operación y exige un motivo antes de aceptar el rechazo.
4. **Given** una orden que no está en `pending_review`, **When** un supervisor
   intenta aprobarla o rechazarla, **Then** el sistema rechaza la operación.
5. **Given** un usuario que no es supervisor, **When** intenta aprobar o rechazar
   una orden, **Then** el sistema rechaza la operación.

---

### User Story 4 - Reasignar una orden entre técnicos (Priority: P3)

Un dispatcher necesita poder reasignar una orden a un technician distinto del que
la tiene actualmente, para reorganizar el trabajo cuando surge un imprevisto (el
técnico original no puede completarla, hay una urgencia, etc.).

**Why this priority**: El propio brief la describe como algo que ocurre "si hace
falta" — es una operación de gestión, no parte del camino feliz principal, y el
sistema es útil sin ella durante una demo del núcleo (US1-US3), aunque sí forma
parte del slice mínimo exigido.

**Independent Test**: Con una orden sembrada asignada a un technician A, un
dispatcher la reasigna a un technician B y se verifica que la orden aparece en la
lista de B y no en la de A.

**Acceptance Scenarios**:

1. **Given** una orden asignada a un technician, **When** el dispatcher la
   reasigna a otro technician, **Then** la orden pasa a estar asignada al nuevo
   technician y deja de aparecer entre las órdenes del anterior.
2. **Given** una orden en estado `closed`, **When** el dispatcher intenta
   reasignarla, **Then** el sistema rechaza la operación; la reasignación solo
   es válida sobre órdenes en `assigned`, `in_progress` o `pending_review`.
3. **Given** un usuario que no es dispatcher, **When** intenta reasignar una
   orden, **Then** el sistema rechaza la operación.

---

### User Story 5 - Resumen automático de la incidencia vía asistente de IA (Priority: P3)

Un supervisor necesita, al revisar una orden en `pending_review`, disponer de un
resumen automático de la incidencia a partir de las notas del técnico, para no
tener que leer la nota completa antes de decidir.

**Why this priority**: El propio brief la introduce como algo "ideal" (nice-to-have),
no como parte del camino crítico; el flujo de aprobación (US3) funciona
igualmente sin ella, aunque sea más lento de operar.

**Independent Test**: Con una orden que tiene una nota de ejecución sustancial, se
solicita el resumen y se verifica que refleja el contenido de la nota. Con una
orden cuya nota está vacía o es demasiado escueta, se solicita el resumen y se
verifica que el sistema declara explícitamente que no hay evidencia suficiente,
en vez de generar un resumen.

**Acceptance Scenarios**:

1. **Given** una orden con una nota de ejecución clara y suficiente, **When** se
   solicita el resumen de la incidencia, **Then** el sistema devuelve un resumen
   que refleja fielmente el contenido de la nota, sin añadir información que no
   esté en ella.
2. **Given** una orden sin nota de ejecución o con una nota claramente
   insuficiente, **When** se solicita el resumen, **Then** el sistema declara
   explícitamente que no hay evidencia suficiente para resumir, y no genera un
   resumen inventado.
3. **Given** una orden que aún no está en `pending_review`, **When** se solicita
   su resumen, **Then** el sistema rechaza o no ofrece la operación (no aplica
   antes de que exista una ejecución registrada).

---

### Edge Cases

- ¿Qué ocurre si dos dispatchers intentan reasignar la misma orden casi a la vez?
  El sistema debe aplicar la última reasignación válida procesada (la orden
  queda asignada al technician de esa última operación) y no dejar la orden en
  un estado contradictorio (asignada a dos técnicos a la vez).
- ¿Qué ocurre si el technician adjunta una foto corrupta o en un formato no
  soportado? El sistema debe rechazar el archivo y pedir una evidencia válida,
  sin marcar la ejecución como registrada.
- ¿Qué ocurre si un usuario sin sesión válida intenta cualquier acción? El
  sistema debe rechazar la petición por falta de autenticación, no por falta de
  permiso de rol.
- ¿Qué ocurre si la nota del técnico es larga pero irrelevante para la
  incidencia (ruido)? El asistente de IA no debe rellenar huecos inventando
  relevancia que la nota no tiene; si no puede extraer una incidencia clara, lo
  declara igual que si la nota estuviera vacía.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un technician consultar la lista y el
  detalle de las órdenes que tiene asignadas.
- **FR-002**: El sistema DEBE permitir a un dispatcher consultar todas las
  órdenes del sistema, incluyendo a qué technician está asignada cada una,
  independientemente de su estado.
- **FR-003**: El sistema DEBE permitir a un supervisor consultar todas las
  órdenes del sistema, distinguiendo claramente las que están en estado
  `pending_review` como pendientes de su revisión.
- **FR-004**: El sistema DEBE rechazar el acceso al detalle de una orden cuando el
  usuario que lo solicita no tiene, según su rol, motivo para verla.
- **FR-005**: El sistema DEBE permitir a un technician registrar la ejecución de
  una orden propia que esté en estado `in_progress`, adjuntando una nota y al
  menos una foto de evidencia.
- **FR-006**: El sistema DEBE rechazar el registro de ejecución si no se adjunta
  ninguna foto de evidencia.
- **FR-007**: El sistema DEBE rechazar el registro de ejecución sobre una orden
  que no esté en estado `in_progress`, o que no esté asignada al technician que
  intenta registrarla.
- **FR-008**: Cuando se registra una ejecución válida, el sistema DEBE cambiar el
  estado de la orden a `pending_review`.
- **FR-009**: El sistema DEBE permitir a un supervisor aprobar o rechazar una
  orden que esté en estado `pending_review`.
- **FR-010**: El sistema DEBE rechazar la aprobación o el rechazo de una orden que
  no esté en `pending_review`, o si quien lo solicita no es supervisor.
- **FR-011**: Cuando un supervisor aprueba una orden, el sistema DEBE cambiar su
  estado a `closed`.
- **FR-012**: Cuando un supervisor rechaza una orden, el sistema DEBE cambiar su
  estado a `in_progress`, dejándola disponible para que el mismo technician
  corrija la ejecución, y DEBE guardar el comentario de rechazo junto a la
  orden.
- **FR-012a**: El sistema DEBE exigir un comentario no vacío para aceptar un
  rechazo; sin él, DEBE rechazar la operación de rechazo.
- **FR-013**: El sistema DEBE permitir a un dispatcher reasignar una orden a un
  technician distinto del que la tiene actualmente, siempre que la orden esté en
  estado `assigned`, `in_progress` o `pending_review`. Reasignar una orden en
  `pending_review` DEBE cambiar únicamente el technician responsable, sin
  alterar el estado de la orden ni el resultado de la revisión del supervisor
  en curso.
- **FR-014**: El sistema DEBE rechazar la reasignación si quien la solicita no es
  dispatcher, o si la orden está en estado `closed`.
- **FR-015**: El sistema DEBE ofrecer al supervisor, a su solicitud, para una
  orden en `pending_review` con una ejecución registrada, un resumen automático
  de la incidencia generado a partir de la nota del técnico. El resumen DEBE
  ser breve (unas pocas frases, no una reescritura extensa) y DEBE estar en el
  mismo idioma que la nota original.
- **FR-016**: El sistema DEBE declarar explícitamente que no dispone de evidencia
  suficiente para resumir, en vez de generar contenido no soportado por la nota,
  cuando esta esté vacía o sea insuficiente.
- **FR-017**: El sistema DEBE rechazar toda acción (consulta, registro,
  aprobación/rechazo, reasignación) solicitada por un usuario sin sesión válida,
  distinguiendo ese caso del de un usuario autenticado sin el rol requerido.
- **FR-018**: El sistema DEBE rechazar toda acción reservada a un rol cuando la
  solicita un usuario autenticado que no pertenece a ese rol.
- **FR-019**: El sistema DEBE cifrar toda comunicación entre cliente y servidor
  (tránsito), sin aceptar conexiones no cifradas.
- **FR-020**: El sistema DEBE almacenar cifrados en reposo los datos sensibles
  de cliente asociados a una orden (evidencia fotográfica y notas de
  ejecución).
- **FR-021**: Ante reasignaciones concurrentes sobre la misma orden, el sistema
  DEBE aplicar la última reasignación válida procesada como resultado final,
  sin dejar la orden asignada a más de un technician a la vez.
- **FR-022**: El sistema DEBE mostrar al technician el comentario de rechazo
  del supervisor cuando consulte una orden que volvió a `in_progress` tras un
  rechazo.
- **FR-023**: El sistema DEBE registrar, con fines de auditoría, todo rechazo
  de acceso por falta de sesión válida o por rol no autorizado (FR-017,
  FR-018), incluyendo qué se intentó y quién lo intentó.

### Key Entities

- **Orden (Order)**: unidad de trabajo de campo. Atributos relevantes: estado
  actual (`draft`, `assigned`, `in_progress`, `pending_review`, `closed`),
  technician asignado, nota de ejecución, evidencia fotográfica asociada,
  resultado de la revisión del supervisor y, si fue rechazada, el comentario
  de motivo asociado.
- **Usuario (User)**: persona que interactúa con el sistema, con exactamente un
  rol (`dispatcher`, `technician`, `supervisor`) que determina qué acciones puede
  solicitar sobre qué órdenes.
- **Registro de Ejecución (Execution Record)**: la nota y la evidencia
  fotográfica que un technician adjunta al registrar el trabajo realizado sobre
  una orden; es la entrada que consume el resumen de incidencia.
- **Resumen de Incidencia (Incident Summary)**: salida generada a partir del
  Registro de Ejecución de una orden; puede ser un resumen del contenido o una
  declaración explícita de evidencia insuficiente, nunca contenido inventado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de registrar una ejecución sin foto de
  evidencia son rechazados por el sistema.
- **SC-002**: El 100% de las acciones solicitadas por un rol no autorizado para
  esa acción son rechazadas, distinguiendo siempre el caso de "sin sesión" del
  caso de "sesión válida pero rol incorrecto".
- **SC-003**: El 100% de los resúmenes de incidencia generados sobre una nota
  vacía o insuficiente declaran explícitamente la falta de evidencia, sin
  contenido inventado no respaldado por la nota.
- **SC-004**: Un technician puede completar el registro de una ejecución,
  desde que empieza a escribir la nota hasta que la foto queda subida y
  confirmada, en menos de 3 minutos en una sesión de uso normal.
- **SC-005**: Las acciones principales del sistema (consultar órdenes, registrar
  ejecución, aprobar/rechazar, reasignar) responden en menos de 2 segundos bajo
  condiciones normales de uso.
- **SC-006**: El 100% del tráfico entre cliente y servidor viaja cifrado; no se
  acepta ninguna conexión sin cifrar. El 100% de la evidencia fotográfica y las
  notas de ejecución quedan almacenadas cifradas en reposo.

## Assumptions

- El dashboard de métricas de productividad y las notificaciones push a
  técnicos quedan fuera de alcance de este spec: el brief los introduce en la
  misma frase de aplazamiento ("pero bueno, eso lo vemos") y el usuario ha
  confirmado explícitamente tratarlos como una tarea aparte.
- El resto de supuestos tomados por no poder resolverse solo con el brief —
  incluyendo qué impacto tendría cada uno si resultase equivocado— están
  registrados en [`docs/assumptions.md`](../../docs/assumptions.md), conforme
  al Principio III de la constitution.
- No se implementa borrado automático ni política de expiración de datos en
  este slice: la evidencia fotográfica y las notas de ejecución se conservan
  mientras la orden exista en el sistema (ver Clarifications).
