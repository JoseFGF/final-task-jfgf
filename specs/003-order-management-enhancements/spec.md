# Feature Specification: Gestión de órdenes por email y visualización de evidencia

**Feature Branch**: `003-order-management-enhancements`

**Created**: 2026-07-14

**Status**: Draft

**Input**: User description: "Se modifica la asignación/reasignación de órdenes para identificar al technician por su email (campo único) en lugar de por UUID. El rol dispatcher podrá crear órdenes nuevas (actualmente no existe esa capacidad, solo hay datos de seed). Dentro del detalle de una orden, si tiene fotos de evidencia asociadas, estas se podrán visualizar (actualmente no se muestran). Se corrige el bug del frontend por el cual no se puede asignar una orden que está en estado sin asignar (draft) — actualmente el flujo de asignación/reasignación solo contempla órdenes ya asignadas (assigned, in_progress, pending_review), y hay que permitir también asignar un technician por primera vez a una orden en draft."

## Clarifications

### Session 2026-07-14

- Q: ¿Qué información mínima debe aportar el dispatcher al crear una orden nueva? → A: La orden requiere una descripción de texto libre del trabajo a realizar (único campo nuevo); no se añaden cliente/ubicación/prioridad en este alcance.
- Q: ¿Puede el dispatcher asignar ya un technician (por email) en el mismo paso de creación de la orden? → A: Sí, es opcional: si se aporta un email de technician válido al crear la orden, esta nace directamente en `assigned`; si no se aporta, nace en `draft` sin technician.
- Q: ¿Cómo se protege el acceso a las fotos de evidencia al visualizarlas? → A: Se requiere la misma autenticación/sesión que el resto de la API (ningún acceso público sin autenticar); se aplican las mismas reglas de rol ya vigentes para ver el detalle de la orden.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Asignar/reasignar por email en lugar de UUID (Priority: P1)

Como dispatcher, quiero identificar al technician por su dirección de email al asignar o reasignar una orden, en lugar de tener que conocer y escribir su UUID interno, para no depender de un identificador técnico que no tengo forma de consultar en el día a día.

**Why this priority**: Es la funcionalidad más usada del flujo de dispatch y actualmente es prácticamente inutilizable en la práctica (el dispatcher no tiene forma de conocer el UUID de un technician sin consultar la base de datos directamente).

**Independent Test**: Se puede probar completamente creando/teniendo una orden en estado `draft` o `assigned`, introduciendo el email de un technician existente en el formulario de asignación, y verificando que la orden queda asignada a ese technician.

**Acceptance Scenarios**:

1. **Given** una orden en estado `assigned`, `in_progress` o `pending_review` y un email de un technician existente, **When** el dispatcher solicita la reasignación con ese email, **Then** la orden queda asignada al technician correspondiente a ese email.
2. **Given** un email que no corresponde a ningún usuario con rol technician, **When** el dispatcher solicita la asignación/reasignación con ese email, **Then** el sistema rechaza la solicitud indicando que el email no corresponde a un technician válido.
3. **Given** un email con distinta capitalización que el registrado (p. ej. `Tania@fieldops.test` vs `tania@fieldops.test`), **When** el dispatcher solicita la asignación, **Then** el sistema identifica correctamente al technician (la búsqueda por email no distingue mayúsculas/minúsculas).
4. **Given** una orden con un technician asignado, **When** un usuario con acceso a esa orden consulta su detalle, **Then** el technician asignado se identifica por su **email**, no por su UUID interno (de lo contrario el dispatcher seguiría necesitando consultar la base de datos para saber a quién asignó, reintroduciendo el problema que esta historia resuelve).

---

### User Story 2 - Corregir la asignación inicial de órdenes en estado draft (Priority: P1)

Como dispatcher, quiero poder asignar un technician a una orden que todavía no tiene ninguno asignado (estado `draft`), para poder poner en marcha órdenes recién creadas.

**Why this priority**: Es un bug bloqueante: sin esto, ninguna orden nueva (`draft`) puede llegar nunca a ejecutarse, independientemente de que ahora se puedan crear órdenes nuevas (User Story 3).

**Independent Test**: Se puede probar completamente tomando una orden en estado `draft` sin technician asignado, asignándole un technician por email, y verificando que la orden pasa a estado `assigned` con ese technician.

**Acceptance Scenarios**:

1. **Given** una orden en estado `draft` sin technician asignado, **When** el dispatcher le asigna un technician válido, **Then** la orden pasa a estado `assigned` con ese technician como responsable.
2. **Given** una orden en estado `closed`, **When** el dispatcher intenta asignarle o reasignarle un technician, **Then** el sistema rechaza la solicitud (una orden cerrada no admite cambios de asignación).
3. **Given** el frontend, **When** el dispatcher abre el detalle de una orden en estado `draft`, **Then** el enlace/acción para asignar un technician está visible y habilitado (a diferencia del comportamiento actual, que no lo muestra para `draft`).

---

### User Story 3 - Crear órdenes nuevas (Priority: P2)

Como dispatcher, quiero crear órdenes de trabajo nuevas, para poder registrar trabajo entrante sin depender de datos de seed precargados.

**Why this priority**: Habilita el ciclo de vida completo de una orden de principio a fin, pero depende conceptualmente de que la asignación (User Stories 1 y 2) funcione correctamente sobre las órdenes recién creadas.

**Independent Test**: Se puede probar completamente iniciando sesión como dispatcher, creando una orden nueva sin indicar technician, y verificando que aparece en el listado de órdenes en estado `draft`; y, por separado, creando una orden indicando ya el email de un technician válido, verificando que aparece directamente en estado `assigned`.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado con rol dispatcher, **When** crea una orden nueva aportando una descripción del trabajo a realizar y sin indicar ningún technician, **Then** la orden se crea en estado `draft` sin technician asignado, con esa descripción, y aparece en el listado de órdenes.
2. **Given** un usuario autenticado con rol dispatcher, **When** crea una orden nueva aportando una descripción y el email de un technician existente, **Then** la orden se crea directamente en estado `assigned` con ese technician como responsable.
3. **Given** un usuario autenticado con rol dispatcher, **When** crea una orden indicando un email que no corresponde a ningún technician existente, **Then** el sistema rechaza la creación indicando que el email no corresponde a un technician válido.
4. **Given** un usuario autenticado con rol dispatcher, **When** intenta crear una orden sin aportar ninguna descripción, **Then** el sistema rechaza la solicitud por faltar un campo obligatorio.
5. **Given** un usuario autenticado con rol technician o supervisor, **When** intenta crear una orden, **Then** el sistema rechaza la solicitud por no tener el rol adecuado.

---

### User Story 4 - Visualizar fotos de evidencia en el detalle de la orden (Priority: P3)

Como usuario con acceso al detalle de una orden (dispatcher, technician o supervisor, según las reglas de acceso ya existentes), quiero poder ver las fotos de evidencia asociadas a la orden directamente en su detalle, para poder verificar el trabajo realizado sin depender de acceder al almacenamiento por separado.

**Why this priority**: Mejora la experiencia de revisión y seguimiento, pero no bloquea el resto del ciclo de vida de la orden (el registro y la aprobación de ejecuciones ya funcionan sin esto).

**Independent Test**: Se puede probar completamente abriendo el detalle de una orden que tenga al menos una foto de evidencia asociada y verificando que la foto se muestra visualmente en la página, no solo referenciada por texto/enlace.

**Acceptance Scenarios**:

1. **Given** una orden con una o más fotos de evidencia asociadas, **When** un usuario con acceso a esa orden abre su detalle, **Then** las fotos se muestran visualmente en la página.
2. **Given** una orden sin fotos de evidencia asociadas, **When** un usuario abre su detalle, **Then** no se muestra ninguna sección de fotos rota o vacía que resulte confusa.

### Edge Cases

- ¿Qué ocurre si dos dispatchers intentan asignar/reasignar la misma orden `draft` al mismo tiempo con emails de technicians distintos? (Ya existe manejo de reasignación concurrente para órdenes no-draft; debe aplicar el mismo criterio de resolución también al caso de asignación inicial).
- ¿Qué ocurre si el dispatcher introduce un email con espacios en blanco al principio/final? El sistema debe normalizarlo (recortar espacios) antes de buscar el technician correspondiente.
- ¿Qué ocurre si se crea una orden sin indicar ningún technician (el único campo opcional del formulario de creación)? Debe quedar en `draft` sin technician asignado, igual que las órdenes de seed existentes en ese estado (la descripción, en cambio, es obligatoria — ver FR-007a).
- ¿Qué ocurre si una foto de evidencia referenciada en la orden ya no existe en el almacenamiento? El sistema debe mostrar el resto de fotos disponibles sin romper la página completa.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un dispatcher asignar o reasignar el technician responsable de una orden indicando el **email** del technician, en lugar de su identificador interno (UUID).
- **FR-002**: El sistema DEBE validar que el email indicado corresponde a un usuario existente con rol technician; si no es así, DEBE rechazar la solicitud con un mensaje que indique que el email no corresponde a un technician válido.
- **FR-003**: La búsqueda de technician por email DEBE ser insensible a mayúsculas/minúsculas y DEBE ignorar espacios en blanco al principio o al final del valor introducido.
- **FR-003a**: El sistema DEBE identificar al technician asignado a una orden por su **email** (no por su UUID interno) en cualquier vista o respuesta que muestre el detalle de la orden.
- **FR-004**: El sistema DEBE permitir asignar un technician a una orden en estado `draft` (asignación inicial), además de reasignar en los estados ya soportados (`assigned`, `in_progress`, `pending_review`).
- **FR-005**: Al asignarse un technician a una orden en estado `draft`, el sistema DEBE hacer transicionar la orden al estado `assigned`.
- **FR-006**: El sistema DEBE seguir rechazando cualquier intento de asignación o reasignación sobre una orden en estado `closed`.
- **FR-007**: El sistema DEBE permitir a un usuario con rol dispatcher crear una orden nueva aportando una descripción de texto libre del trabajo a realizar; si no se indica ningún technician, la orden queda en estado `draft` sin technician asignado.
- **FR-007a**: El sistema DEBE rechazar la creación de una orden si no se aporta ninguna descripción, o si esta contiene únicamente espacios en blanco.
- **FR-007b**: El sistema DEBE permitir, opcionalmente, indicar el email de un technician ya en el momento de crear la orden; si se indica y corresponde a un technician válido, la orden DEBE crearse directamente en estado `assigned` con ese technician como responsable, en lugar de en `draft`.
- **FR-007c**: Toda asignación de technician a una orden DEBE quedar auditada de forma consistente (quién asignó y cuándo), sin importar si ocurrió durante la creación de la orden (FR-007b), como asignación inicial sobre `draft` (FR-004/FR-005) o como reasignación posterior (FR-001) — ninguna de estas tres vías debe dejar la asignación sin registro de auditoría mientras las otras sí lo hacen.
- **FR-008**: El sistema DEBE rechazar la creación de órdenes solicitada por usuarios con rol technician o supervisor.
- **FR-009**: El sistema DEBE mostrar, en el detalle de una orden, las fotos de evidencia asociadas a ella de forma visual (no solo como referencia textual o enlace de descarga), para cualquier usuario con acceso al detalle de esa orden según las reglas de acceso ya existentes.
- **FR-009a**: El acceso al contenido de una foto de evidencia DEBE requerir la misma autenticación que el resto de la API; el sistema NO DEBE permitir obtener el contenido de una foto sin una sesión válida, aunque se conozca su identificador.
- **FR-010**: Si una orden no tiene fotos de evidencia asociadas, el sistema DEBE mostrar su detalle sin ninguna sección de fotos rota, vacía o confusa.
- **FR-011**: El frontend DEBE ofrecer la acción de asignar/reasignar technician también cuando la orden está en estado `draft`, no solo en los estados ya contemplados actualmente.

### Key Entities

- **Orden (Order)**: unidad de trabajo de campo, ya existente. Se extiende su ciclo de vida para que la transición `draft` → `assigned` pueda originarse desde tres vías equivalentes en cuanto a auditoría: creación con technician indicado, asignación inicial explícita sobre `draft`, o reasignación posterior. Se añade un campo nuevo de **descripción** (texto libre, obligatorio), aportado por el dispatcher al crear la orden. El technician asignado se identifica por email en cualquier vista del detalle, no por su UUID interno.
- **Usuario (User)**: persona que interactúa con el sistema, ya existente; el email (ya definido como único) pasa a ser también el identificador operativo usado por el dispatcher para referirse a un technician al asignar/reasignar órdenes.
- **Foto de Evidencia (Evidence Photo)**: entidad ya existente (registrada al ejecutar una orden); se añade la capacidad de visualizarla, no de crearla ni modificarla, desde el detalle de la orden.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las asignaciones/reasignaciones realizadas por un dispatcher usando el email de un technician existente completan correctamente sin necesidad de consultar ningún identificador técnico adicional.
- **SC-002**: El 100% de los intentos de asignar/reasignar con un email que no corresponde a ningún technician son rechazados con un mensaje claro sobre la causa.
- **SC-003**: El 100% de las órdenes en estado `draft` pueden recibir una asignación inicial de technician desde el detalle de la orden, sin errores ni acciones deshabilitadas.
- **SC-004**: El 100% de las órdenes creadas por un dispatcher aparecen inmediatamente en el listado de órdenes, en `draft` o en `assigned` según si se indicó o no un technician al crearlas.
- **SC-005**: El 100% de las órdenes con fotos de evidencia asociadas muestran esas fotos visualmente en su página de detalle, sin pasos adicionales por parte del usuario.
- **SC-006**: El 100% de las vistas de detalle de una orden asignada identifican al technician responsable por su email, sin exponer su UUID interno.
- **SC-007**: El 100% de las asignaciones de technician a una orden (por creación, asignación inicial o reasignación) quedan registradas con quién la realizó y cuándo, sin importar la vía utilizada.

## Assumptions

- El email de un usuario ya es único en el sistema (según el modelo de datos existente), por lo que sirve como identificador no ambiguo para localizar a un technician.
- La asignación de un technician en el momento de crear la orden es opcional: si no se indica, la orden queda en `draft` y la asignación es una acción posterior (cubierta por las User Stories 1 y 2); si se indica un email válido, la orden nace directamente en `assigned`.
- La visualización de fotos de evidencia reutiliza el almacenamiento ya existente para las fotos subidas por el technician al registrar la ejecución (no se introduce un mecanismo de almacenamiento nuevo), y se sirve a través de un acceso autenticado (misma sesión/token que el resto de la API), no mediante una URL pública sin autenticar.
- Las reglas de acceso (qué rol puede ver el detalle de qué orden) no cambian con esta feature; solo se añade contenido visual (las fotos) dentro de una vista que ya era accesible.
- La resolución de conflictos concurrentes ya implementada para la reasignación (reintento sobre la versión más reciente de la orden) se extiende sin cambios de diseño al caso de asignación inicial sobre `draft`.
