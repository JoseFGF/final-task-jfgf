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
