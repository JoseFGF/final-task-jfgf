# SLICE-REVIEW: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

Revisión de cierre conforme al punto 11 del Development Workflow de
`.specify/memory/constitution.md`. Fecha: 2026-07-11.

## 1. ¿Funciona de verdad?

**Sí, verificado en vivo, no solo por tests.** Se construyeron las 3
imágenes Docker reales (`docker compose build`), se levantó el stack
completo (`docker compose up`), y se ejecutaron llamadas HTTP reales contra
el sistema corriendo:

- Login real (`POST /api/v1/auth/login`) con las 4 cuentas de seed → 200 con
  JWT válido.
- `GET /api/v1/orders` con ese token, tanto directo al backend
  (`https://localhost:8443`) como a través del frontend
  (`http://localhost:4200/api/v1/...`, vía el proxy de Nginx) → 200 en
  ambos casos.
- Rechazo correcto sin token (401) y con password incorrecta en login (401).

Esta verificación en vivo encontró y corrigió **dos problemas reales que
ningún test automático había detectado**:

1. **No existía ningún endpoint de login.** Los tests generaban el JWT
   directamente en código, así que la suite entera pasaba en verde aunque
   un usuario real no pudiera nunca obtener un token. Cerrado con FR-024
   (T066).
2. **El proxy `/api/` de Nginx enviaba mal la ruta al backend** (un
   `proxy_pass` con variable no reescribe el URI como uno literal — enviaba
   literalmente `/api/`, descartando el resto de la ruta). Esto rompía
   *todas* las llamadas autenticadas hechas a través del frontend, y ningún
   test unitario/integración lo detecta porque ninguno ejercita el proxy
   real. Cerrado en `src/frontend/nginx.conf` (T062/T067).

Ambos hallazgos son la razón de fondo por la que este proyecto trata
"ejecutarlo de verdad" como un paso obligatorio, no opcional: 117 tests en
verde (70 backend + 47 frontend) no habían detectado ninguno de los dos.

Tests automáticos: backend 70/70 (`mvn clean verify`, incluye contract +
integration + el smoke test de rendimiento y cifrado), frontend 47/47
(`ng test`), ambos con 0 fallos y 0 errores en la última corrida.

**Pendiente**: SC-004 (tiempo de registro de ejecución end-to-end en un
navegador real con cámara) no se ha podido medir en este entorno de trabajo
(sin navegador/dispositivo con cámara disponible) — procedimiento exacto
documentado en `quickstart.md`, no inventado como resultado.

## 2. ¿Se entiende sin tener que preguntar al autor?

**En general sí**, con la documentación que ya existe:
- `README.md` — instalación, arranque, tests, credenciales de seed reales.
- `docs/functional-overview.md` — qué hace el sistema en lenguaje de negocio.
- `specs/001-order-lifecycle-workflow/{spec,plan,research,data-model,quickstart}.md` — decisiones técnicas y de negocio, con su rationale.
- `docs/traceability.md` — qué requisito verifica qué test.
- `docs/assumptions.md` — qué se asumió y por qué, con el impacto si resulta incorrecto.

Dos cosas que un lector nuevo debería saber de antemano, ya documentadas
pero fáciles de pasar por alto: (a) el login (FR-024) no estaba en el diseño
original de ninguna historia de usuario, se añadió al detectarse el hueco
durante Implement; (b) las 4 cuentas de seed comparten la misma contraseña
(`password123`), documentada en el README, no es un despliegue real.

## 3. ¿Los tests realmente fallarían si se rompiera lo que dicen proteger?

**Con confianza razonable, sí**, por diseño explícito:
- Los tests de RBAC verifican 401 y 403 por separado en cada endpoint (no
  solo "rechaza"), así que un cambio que confunda "sin sesión" con "sin
  permiso" los rompería.
- El fail-safe del componente de IA (`IncidentSummaryProviderFailureTest`)
  fuerza un fallo real del cliente mockeado y verifica que la respuesta
  sigue siendo `sufficient: false` — no es un test que solo comprueba el
  camino feliz.
- El test de concurrencia de reasignación (`ReassignmentConcurrencyTest`)
  lanza dos hilos reales contra la misma orden, no simula la concurrencia.
- El smoke test de cifrado verifica un rechazo real de conexión HTTP en
  claro, no solo lee la configuración.

**Honestidad**: no se ha hecho mutation testing formal (romper el código a
propósito y confirmar que el test lo detecta) más allá de los casos
puntuales de arriba verificados manualmente durante el desarrollo. Es una
confianza razonada, no una garantía exhaustiva.

## 4. ¿El autor lo firmaría tal cual está?

**Sí, con una salvedad explícita**: el slice cumple las 5 historias de
usuario, RBAC en doble capa, cifrado, auditoría, trazabilidad al 97%
(28/29 FR+SC — ver `docs/traceability.md`) y se ejecuta de verdad en Docker.
La única pieza pendiente es **SC-004**, que requiere una sesión manual con
navegador y cámara que este entorno de trabajo no tiene disponible; queda
documentado como no verificado, no como "aprobado por defecto".

## Decisión de aprobación

**Aprobado para este alcance**, condicionado a que alguien con acceso a un
navegador y una cámara ejecute el procedimiento de `quickstart.md`
("Medición manual de SC-004") antes de considerar el slice 100% cerrado
según el Principio III de la constitution (trazabilidad total).

---

# SLICE-REVIEW: Gestión de órdenes por email y visualización de evidencia

Revisión de cierre conforme al punto 11 del Development Workflow de
`.specify/memory/constitution.md`. Fecha: 2026-07-14.

## 1. ¿Funciona de verdad?

**Sí, verificado en vivo, no solo por tests.** Se reconstruyeron las 3
imágenes Docker (`docker compose up --build`) y se ejecutaron llamadas HTTP
reales contra el backend corriendo (`https://localhost:8443`), no solo la
suite automatizada:

- Asignación inicial sobre la orden seed `draft` (`a1111111-...`) por email,
  con capitalización y espacios distintos (`"  Technician2@Fieldops.test  "`)
  → 200, `status` pasa a `assigned`, y la respuesta expone
  `assignedTechnicianEmail` (no UUID) — US1 + US2 confirmadas juntas.
- Creación de orden (`POST /orders`): solo con `description` → 201 `draft`;
  con `description` + `technicianEmail` → 201 `assigned`; sin `description`
  → 422; como `technician` → 403 — los 4 casos de US3.
- Fotos de evidencia (`GET .../evidence-photos/{photoId}`) sobre una orden
  con una foto **real** ya subida en una sesión anterior (el volumen de
  Postgres es persistente entre `docker compose up`): 200 con el PNG real
  (confirmado con `file`, no solo por el código HTTP) como supervisor; 401
  sin `Authorization`; 403 como `technician2` (no asignado a esa orden) — US4.
- Caso real de foto referenciada en seed mock (`a4444444-...`) cuyo archivo
  nunca se subió al disco → 404 con mensaje claro, no un 500 ni una página
  rota — confirma que el Edge Case de "foto que ya no existe en el
  almacenamiento" (FR-010) está cubierto también fuera de los tests.

Tests automáticos: backend 102/102 (`mvn test`), frontend 62/62
(`ng test --watch=false`), 0 fallos y 0 errores en la última corrida (tras
aplicar los hallazgos de `java-reviewer`/`frontend-reviewer`).

Esta verificación en vivo no encontró bugs nuevos (a diferencia de la
feature 001, donde sí aparecieron dos huecos reales) — los dos problemas de
diseño que sí aparecieron (N+1 en `GET /orders`, acoplamiento estático entre
servicios) los encontró `java-reviewer` leyendo el código, y se corrigieron
antes de esta ejecución en vivo.

## 2. ¿Se entiende sin tener que preguntar al autor?

**Sí**, con la documentación ya actualizada:
- `README.md` — sección "Novedades (feature 003)" explica el cambio de UUID
  a email y la nueva creación de órdenes.
- `specs/003-order-management-enhancements/{spec,plan,research,data-model,quickstart}.md`
  — decisiones técnicas (ADR-005 a ADR-008) con rationale y criterio de
  revisión.
- `docs/traceability.md` — sección propia para esta feature, 23/23 FR+SC
  cubiertos (100%).
- `docs/assumptions.md` — la suposición abierta de la feature 001 ("¿quién
  crea/asigna una orden?") queda tachada y enlazada a esta feature como
  resuelta.
- `contracts/openapi.yaml` (v0.2.0) — nota explícita sobre la colisión de
  numeración de FRs entre features (cada spec numera los suyos de forma
  independiente).

## 3. ¿Los tests realmente fallarían si se rompiera lo que dicen proteger?

Muestreo de tests que ejercitan comportamiento real, no solo forma:
- `ReassignmentByEmailTest`/`ReassignmentInvalidEmailTest`: usan
  `findByEmailIgnoreCase` contra una base de datos Testcontainers real, no
  un mock — si se rompiera la insensibilidad a mayúsculas, fallarían de
  verdad.
- `OrderCreationAssignedTest`: verifica `lastReassignedBy`/`lastReassignedAt`
  reales tras la creación, no solo el código HTTP — si `OrderService`
  dejara de auditar esa vía (FR-007c), este test lo detectaría.
- `EvidencePhotoAccessTest`/`EvidencePhotoUnauthenticatedTest`: piden el
  binario real de una foto guardada en disco (no un mock de
  `FileStorageService`) y verifican el `Content-Type` de la respuesta.
- Frontend: `order-detail.component.spec.ts` verifica que se revoca cada
  `Object URL` (`URL.revokeObjectURL`) al destruir el componente o recargar
  fotos — protege contra el memory leak que `frontend-reviewer` señaló
  como riesgo a vigilar (confirmado ya cubierto, no un hueco).

**Honestidad**: no se hizo mutation testing formal en esta feature tampoco;
la confianza es la misma que en la feature 001 — razonada por muestreo, no
una garantía exhaustiva.

## 4. ¿El autor lo firmaría tal cual está?

**Sí.** Las 4 historias de usuario (asignación/reasignación por email, fix
del bug de `draft`, creación de órdenes, visualización de fotos) están
implementadas, con RBAC en doble capa verificado en cada endpoint nuevo,
trazabilidad al 100% para esta feature, y verificación en vivo sobre Docker
que confirma que no solo "los tests pasan" sino que el sistema responde
correctamente a peticiones HTTP reales. Los 3 hallazgos de code review
(N+1, acoplamiento estático, validación duplicada) y el hallazgo de UX
(descripción de solo espacios) se corrigieron antes de cerrar, no se
dejaron como deuda técnica silenciosa. Los 15 ítems de checklist aceptados
como hueco (`checklists/general.md`) están documentados con su motivo, no
omitidos.

## Decisión de aprobación

**Aprobado sin condiciones pendientes** para esta feature — a diferencia de
la feature 001 (que quedó con SC-004 pendiente de medición manual), todos
los FR/SC de `003-order-management-enhancements` quedan verificados: por
test automatizado y, adicionalmente, por ejecución real contra el sistema
levantado en Docker.
