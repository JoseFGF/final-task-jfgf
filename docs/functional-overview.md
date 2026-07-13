# FieldOps — Documentación funcional

Este documento describe **qué hace FieldOps y para quién**, en lenguaje de
negocio. No es una guía técnica: no encontrarás aquí nombres de endpoints,
códigos de error HTTP ni detalles de base de datos. Para esa información,
consulta la documentación técnica (`docs-writer`) y la especificación
completa en `specs/001-order-lifecycle-workflow/spec.md`.

## 1. Qué es FieldOps

FieldOps es una plataforma para gestionar órdenes de trabajo de técnicos que
operan en campo (por ejemplo, reparaciones o instalaciones en la ubicación
del cliente). Su propósito es que una orden de trabajo tenga un recorrido
claro y controlado desde que se crea hasta que se da por cerrada: quién la
tiene asignada, qué se hizo, con qué evidencia, y quién certificó que el
trabajo quedó bien hecho. Resuelve el problema de que, sin un sistema así,
la coordinación entre quien organiza el trabajo, quien lo ejecuta y quien lo
valida depende de comunicación informal (llamadas, mensajes sueltos) y no
deja rastro fiable de qué pasó con cada orden.

## 2. Roles y qué puede hacer cada uno

FieldOps distingue tres roles. Cada usuario tiene exactamente un rol; no hay
usuarios con más de un rol a la vez.

| Rol | Qué puede hacer | Qué NO puede hacer |
|---|---|---|
| **Dispatcher** | Ver todas las órdenes del sistema, sea cual sea su estado o a quién estén asignadas. Reasignar una orden a un técnico distinto cuando surge un imprevisto (el técnico original no puede continuar, hay una urgencia, etc.). | No puede aprobar ni rechazar el trabajo de un técnico; esa decisión es exclusiva del supervisor. No puede reasignar una orden ya cerrada. |
| **Technician** | Ver la lista y el detalle de las órdenes que tiene asignadas. Registrar la ejecución del trabajo (nota + al menos una foto de evidencia) sobre una orden que le corresponde. Ver el motivo si el supervisor le rechazó una entrega anterior. | No puede ver ni actuar sobre órdenes asignadas a otros técnicos. No puede aprobar su propio trabajo. No puede reasignar órdenes. |
| **Supervisor** | Ver todas las órdenes del sistema, con las que están pendientes de su revisión claramente señaladas. Aprobar o rechazar una orden que un técnico ya ejecutó, con motivo obligatorio en caso de rechazo. Pedir al asistente de IA un resumen de la incidencia registrada por el técnico. | No puede registrar la ejecución del trabajo en campo (eso es tarea del técnico). No puede aprobar o rechazar una orden que aún no está lista para revisión. |

**Regla de negocio transversal**: cualquier intento de consultar o modificar
una orden que no le corresponde al rol o al usuario que lo solicita es
rechazado. El sistema distingue dos motivos de rechazo distintos: no haber
iniciado sesión, o haber iniciado sesión con un rol que no tiene permiso
para esa acción concreta. Ambos casos quedan registrados para poder
auditarlos después.

## 3. El ciclo de vida de una orden, contado como historia

Una orden de trabajo recorre los siguientes estados:

```
draft → assigned → in_progress → pending_review → closed
                                       ↑                │
                                       └── rechazo ──────┘
```

1. **Draft**: la orden existe pero todavía no está lista para trabajarse.
   (La creación de una orden y su paso a `assigned` es responsabilidad del
   dispatcher como parte de organizar el trabajo; ver nota de alcance más
   abajo.)
2. **Assigned**: la orden ya tiene un técnico responsable asignado. El
   técnico la ve en su lista de trabajo pendiente.
3. **In progress**: el técnico está trabajando la orden en campo.
4. **Pending review**: el técnico terminó el trabajo, adjuntó su nota y al
   menos una foto de evidencia, y la orden queda esperando que un
   supervisor la revise.
5. **Closed**: el supervisor revisó el trabajo y lo aprobó. La orden queda
   cerrada de forma definitiva.

**Camino alternativo — rechazo del supervisor**: si al revisar una orden en
`pending_review` el supervisor considera que el trabajo no está bien hecho,
la rechaza explicando el motivo. La orden vuelve a `in_progress` (no a
`draft` ni a `assigned`), sigue asignada al mismo técnico, y el técnico ve
el comentario de rechazo junto a su nota original al volver a abrirla. El
técnico corrige lo necesario y vuelve a registrar la ejecución, lo que
manda la orden otra vez a `pending_review`. Este ciclo de corrección puede
repetirse tantas veces como haga falta hasta que el supervisor apruebe.

**Reasignación durante el ciclo**: un dispatcher puede reasignar la orden a
otro técnico mientras esté en `assigned`, `in_progress` o `pending_review`.
Si se reasigna estando en `pending_review`, solo cambia quién es el técnico
responsable; el estado de la orden y la revisión en curso del supervisor no
se ven afectados. Una orden ya `closed` no se puede reasignar.

## 4. Las cinco capacidades principales

### 4.1 Consultar mis órdenes

**Qué puede hacer el usuario y por qué le importa**: cada usuario, al
iniciar sesión, ve el trabajo que le corresponde según su rol, sin depender
de que alguien se lo comunique por otro canal. Es la base de todo lo
demás: nadie puede ejecutar, reasignar o revisar una orden que no puede ver
primero.

| Regla de negocio | Detalle |
|---|---|
| Alcance de visibilidad del technician | Ve únicamente las órdenes que tiene asignadas, con el estado actual de cada una. |
| Alcance de visibilidad del dispatcher | Ve todas las órdenes del sistema, sin importar estado ni a qué técnico están asignadas. |
| Alcance de visibilidad del supervisor | Ve todas las órdenes del sistema; las que están en `pending_review` se distinguen claramente como pendientes de su revisión. |
| Acceso al detalle de una orden ajena | Se rechaza si el usuario, según su rol, no tiene motivo para verla. |

### 4.2 Registrar la ejecución de una orden

**Qué puede hacer el usuario y por qué le importa**: un técnico que terminó
el trabajo en campo registra esa ejecución (una nota describiendo lo hecho
más evidencia fotográfica), lo que manda la orden a revisión. Es la acción
que genera el trabajo real del sistema y dispara todo el ciclo de calidad
posterior.

| Regla de negocio | Detalle |
|---|---|
| Evidencia obligatoria | Hay que adjuntar al menos una foto; sin ninguna foto, el registro se rechaza y la orden no cambia de estado. |
| Formato de la foto | Se rechaza cualquier archivo que no sea una imagen válida (formato no soportado o archivo corrupto); en ese caso tampoco se marca la ejecución como registrada. |
| Estado requerido de la orden | Solo se puede registrar ejecución sobre una orden en `in_progress`. Sobre una orden en `draft`, `closed` u otro estado, se rechaza. |
| Propiedad de la orden | Solo el técnico al que está asignada la orden puede registrar su ejecución; si otro técnico lo intenta, se rechaza. |
| Efecto al registrar correctamente | La orden pasa a `pending_review`, guardando la nota y la evidencia. |
| Reintento tras rechazo del supervisor | Al volver a registrar la ejecución sobre una orden que volvió de un rechazo, el técnico ve el motivo de rechazo junto a su nota original, y la orden vuelve a pasar a `pending_review`. |

### 4.3 Aprobar o rechazar una orden en revisión

**Qué puede hacer el usuario y por qué le importa**: el supervisor decide si
el trabajo registrado por el técnico es correcto o no, cerrando el ciclo de
calidad antes de dar el trabajo por bueno. Sin esta capacidad, ninguna
orden llega nunca a un estado definitivo.

| Regla de negocio | Detalle |
|---|---|
| Quién puede decidir | Solo un supervisor; si lo intenta otro rol, se rechaza. |
| Estado requerido de la orden | Solo se puede aprobar o rechazar una orden que esté en `pending_review`. |
| Efecto de aprobar | La orden pasa a `closed` (estado final). |
| Efecto de rechazar | La orden vuelve a `in_progress`, queda disponible para que el mismo técnico la corrija, y se guarda el comentario de motivo junto a la orden. |
| Motivo obligatorio al rechazar | No se acepta un rechazo sin comentario; sin motivo, la operación se rechaza y se exige uno antes de aceptar. |

### 4.4 Reasignar una orden entre técnicos

**Qué puede hacer el usuario y por qué le importa**: el dispatcher reasigna
una orden a un técnico distinto del que la tiene actualmente, para
reorganizar el trabajo ante un imprevisto (el técnico original no puede
completarla, surge una urgencia, etc.). Es una operación de gestión, no
parte del camino principal del trabajo, pero necesaria para que el sistema
sea operable en la práctica.

| Regla de negocio | Detalle |
|---|---|
| Quién puede reasignar | Solo un dispatcher; si lo intenta otro rol, se rechaza. |
| Estados válidos para reasignar | `assigned`, `in_progress` o `pending_review`. Una orden `closed` no se puede reasignar. |
| Efecto de la reasignación | La orden pasa a estar asignada al nuevo técnico y deja de aparecer en la lista de trabajo del técnico anterior. |
| Reasignación en `pending_review` | Cambia únicamente el técnico responsable; no altera el estado de la orden ni el resultado de la revisión del supervisor en curso. |
| Reasignaciones casi simultáneas | Si dos dispatchers reasignan la misma orden casi al mismo tiempo, se aplica la última reasignación válida procesada como resultado final; la orden nunca queda asignada a dos técnicos a la vez. |

### 4.5 Resumen automático de la incidencia vía asistente de IA

**Qué puede hacer el usuario y por qué le importa**: al revisar una orden en
`pending_review`, el supervisor puede pedir un resumen automático de la
incidencia a partir de la nota del técnico, para no tener que leer la nota
completa antes de decidir. Es una comodidad que agiliza la revisión, no un
paso obligatorio del flujo.

Ver la sección 5 para el detalle de esta capacidad y su regla de negocio
más importante.

| Regla de negocio | Detalle |
|---|---|
| Cuándo está disponible | Solo para una orden en `pending_review` que ya tiene una ejecución registrada; sobre una orden que aún no llegó a ese punto, no se ofrece o se rechaza. |
| Fidelidad del resumen | El resumen debe reflejar fielmente el contenido de la nota, sin añadir información que no esté en ella. |
| Formato del resumen | Debe ser breve (unas pocas frases, no una reescritura extensa) y estar en el mismo idioma que la nota original. |

## 5. El asistente de resumen de incidencia

Cuando un supervisor revisa una orden, puede pedirle al asistente que le
resuma en pocas frases lo que el técnico reportó, en vez de tener que leer
la nota completa. Esto ahorra tiempo en revisiones con muchas órdenes
pendientes.

La regla de negocio más importante de esta capacidad es la siguiente: **si
la nota del técnico está vacía, es demasiado escueta, o es ruido sin
información útil sobre la incidencia, el asistente debe decir explícitamente
que no tiene evidencia suficiente para resumir, en vez de inventarse un
resumen plausible**. El asistente nunca rellena huecos de información que
la nota no contiene.

**Por qué importa esto para el negocio**: un supervisor toma una decisión de
aprobar o rechazar trabajo basándose en lo que lee. Si el asistente
inventara contenido cuando la nota no dice nada útil, el supervisor podría
aprobar (o rechazar) una orden basándose en información que en realidad no
existe, lo cual es más peligroso que no tener resumen en absoluto. La
confianza del supervisor en la herramienta depende de que, cuando el
asistente afirma algo, ese algo esté realmente respaldado por lo que el
técnico escribió. Una herramienta que "alucina" contenido erosiona esa
confianza y puede llevar a decisiones de calidad equivocadas sobre trabajo
real hecho en campo.

## 6. Qué queda fuera de este alcance

Explícitamente no forman parte de esta funcionalidad:

| Fuera de alcance | Por qué |
|---|---|
| Dashboard de métricas de productividad | El propio encargo de negocio lo aplaza para una iniciativa aparte; no se pidió para este slice. |
| Notificaciones push a técnicos | Igual que el dashboard, se difiere explícitamente a otra iniciativa. |
| Alta o registro de usuarios nuevos, más allá de las cuatro cuentas de prueba ya cargadas en el sistema | El diseño original de esta funcionalidad asumía usuarios ya existentes; solo se añadió la posibilidad de iniciar sesión con esas cuentas de prueba, no un proceso de alta de usuarios nuevos. |
| Creación de una orden nueva y su paso inicial de `draft` a `assigned` | El encargo de negocio no describe explícitamente quién hace esto ni cómo; se ha asumido, sin confirmación de negocio, que es tarea del dispatcher, pero no está implementado ni probado como capacidad propia en este alcance. **Esto queda señalado como duda abierta**, documentada también en `docs/assumptions.md`. |
| Borrado o expiración automática de evidencia y notas | Las fotos y notas de ejecución se conservan indefinidamente mientras la orden exista en el sistema; no hay ninguna política de borrado automático en este alcance. |
| Multi-empresa (varios clientes con datos aislados entre sí) | Se asume que FieldOps sirve a una única organización; no hay indicios en el encargo de negocio de que se necesite separar datos entre distintos clientes. |

## Notas sobre dudas abiertas de negocio

Durante la revisión de esta documentación se identificaron puntos que el
encargo original de negocio no resuelve de forma explícita y que fueron
decididos como supuestos de trabajo (no como hechos confirmados por
negocio). Quedan detallados con su posible impacto en
`docs/assumptions.md`; en particular:

- Quién crea una orden y quién decide moverla de `draft` a `assigned` (se
  asumió que es el dispatcher, sin confirmación explícita).
- Si un mismo usuario puede tener más de un rol a la vez (se asumió que no).
- Qué formatos y límites debe cumplir la foto de evidencia más allá de "al
  menos una foto" (se asumieron formatos de imagen estándar sin límites
  adicionales confirmados por negocio).
- Si FieldOps sirve a un único cliente o a varios con datos aislados (se
  asumió un único cliente).
