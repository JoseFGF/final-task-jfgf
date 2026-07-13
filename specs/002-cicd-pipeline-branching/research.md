# Research: Pipeline de CI/CD y Estrategia de Ramas

Decisiones técnicas tomadas durante Plan, con alternativa descartada y motivo,
conforme al Principio VII de `pipeline-constitution.md` (gates bloqueantes) y
en general al mismo criterio de ADR que ya usa `specs/001-.../research.md`.

## ADR-P1: Mecanismo de detección de componente y de archivos del propio pipeline

- **Decision**: cada workflow de PR/CI usa el filtro nativo `paths:` del
  trigger de GitHub Actions: `src/backend/**` para el backend,
  `src/frontend/**` para el frontend. Además, un job de "guardián de
  constitución" se declara con su propio filtro `paths:` que cubre
  `.github/workflows/**`, `pipeline-constitution.md` y `pipeline-spec.md`, y
  se ejecuta en la PR independientemente de si esa misma PR también toca
  código de un componente (FR-020).
- **Rationale**: `paths:` es soportado nativamente por GitHub Actions, no
  necesita un job previo que calcule el diff — reduce superficie de
  workflows a mantener y evita depender de una acción de terceros solo para
  esto.
- **Alternatives considered**: calcular el diff en un job previo con
  `dorny/paths-filter` y condicionar el resto de jobs a su salida — se
  descarta porque añade una dependencia externa (sujeta a Principio III, pin
  por SHA) para resolver algo que el propio disparador de GitHub ya resuelve
  de forma nativa.
- **Revisar si**: se necesita lógica de detección más compleja que un
  filtro de rutas (p. ej. detectar cambios en un paquete compartido entre
  ambos componentes que hoy no existe en el repo).

## ADR-P2: Cálculo automático de la versión final en `main`

- **Decision**: la versión se calcula a partir de los mensajes de commit
  acumulados desde la última versión publicada, siguiendo la convención de
  *Conventional Commits* (`feat:`, `fix:`, `BREAKING CHANGE:` → minor/patch/
  major respectivamente), usando `semantic-release` (o una acción
  equivalente que implemente el mismo cálculo) fijada por SHA.
- **Rationale**: es el mecanismo estándar para calcular semver de forma
  determinista sin depender de que una persona recuerde crear un tag antes
  de fusionar (Clarify, sesión 2026-07-13); si los commits no siguen la
  convención, la herramienta no calcula versión nueva y el pipeline lo
  señala en vez de inventar un número (FR-011).
- **Alternatives considered**: tag de git manual creado por quien fusiona —
  descartado explícitamente en Clarify por depender de un paso humano fácil
  de olvidar.
- **Revisar si**: el equipo deja de seguir Conventional Commits en los
  mensajes, momento en que el cálculo automático dejaría de ser fiable.

## ADR-P3: Ruta de hotfix sin workflow adicional

- **Decision**: el workflow `ci-main-*.yml` se dispara con el evento `push`
  sobre `main`, sin importar si ese push llegó por un merge desde `develop`
  o desde una rama `hotfix/*` — GitHub no distingue el origen en un evento de
  push a `main`, así que un hotfix pasa exactamente por el mismo pipeline y
  los mismos gates que cualquier otra entrega a `main` (FR-017), sin
  necesidad de un séptimo workflow dedicado.
- **Rationale**: cumplir "las mismas validaciones bloqueantes" (User Story 6)
  es más simple si es literalmente el mismo workflow, no una copia paralela
  que habría que mantener sincronizada.
- **Reintegración a develop (FR-019)**: un job adicional, solo en el
  workflow de PR hacia `main` desde una rama `hotfix/*`, abre automáticamente
  una pull request de `main` hacia `develop` tras la fusión (acción tipo
  `peter-evans/create-pull-request`, fijada por SHA); esa PR de
  reintegración pasa por las mismas validaciones que cualquier otra antes de
  poder fusionarse — no se auto-fusiona sin gates.
- **Alternatives considered**: cherry-pick manual del commit de hotfix a
  develop — descartado por depender de que alguien se acuerde de hacerlo;
  automatizar la apertura de la PR de reintegración es más fiable sin
  quitarle la revisión a un humano antes de fusionarla.
- **Revisar si**: el volumen de hotfixes crece lo suficiente como para que
  los conflictos de reintegración automática se vuelvan frecuentes.

## ADR-P4: Guardián de constitución

- **Decision**: un job dedicado en cada workflow de PR invoca la API del
  agente (Claude Code Action, o una llamada equivalente a la API de
  Anthropic) pasándole el diff de la PR y el contenido de
  `pipeline-constitution.md` (y, si la PR toca código de aplicación,
  también `.specify/memory/constitution.md`), y falla explícitamente el job
  si detecta una violación — lo que bloquea el merge vía status check
  requerido (FR-004, FR-020).
- **Rationale**: es el mismo mecanismo que ya se usa conceptualmente en este
  proyecto para el flujo Spec Kit (revisión contra reglas no negociables);
  reutilizar el patrón evita inventar un verificador de reglas ad-hoc.
- **Alternatives considered**: un linter de reglas estático basado en
  patrones de texto — descartado porque las reglas de esta constitution
  (p. ej. "no reconstruir en el despliegue") no son verificables con un
  regex sobre el diff, requieren entender la intención del cambio.
- **Revisar si**: el volumen de PRs hace que el coste de la llamada al
  agente por cada PR sea significativo.

## ADR-P5: Registro de imágenes y convención de tags

- **Decision**: GHCR, con el formato
  `ghcr.io/<org-o-usuario>/<repo>/fieldops-<componente>:<version>` (ej.
  `fieldops-backend:1.0.0-snapshot.abc1f3e` en develop,
  `fieldops-backend:1.0.0` en main), autenticado con el `GITHUB_TOKEN`
  inyectado automáticamente.
- **Rationale**: ya fijado como no negociable en `pipeline-constitution.md`
  (Additional Constraints); no requiere gestionar ni rotar secretos
  adicionales.
- **Alternatives considered**: ninguna — es una decisión ya cerrada en la
  constitution del pipeline, no una elección abierta en esta fase.

## ADR-P6: Entornos y aprobación de producción

- **Decision**: tres GitHub Environments — `dev` y `pre` sin regla de
  protección (despliegue automático), y `prod` configurado con una lista de
  revisores requeridos (Environment protection rule → "Required reviewers"),
  distinta del conjunto general de mantenedores del repositorio (Clarify,
  sesión 2026-07-13).
- **Rationale**: es el mecanismo nativo de GitHub para modelar exactamente
  "una persona concreta de una lista debe aprobar antes de que el job
  continúe", sin necesitar un paso de aprobación construido a mano.
- **Alternatives considered**: un job que espera un comentario específico en
  la PR o issue — descartado por ser más fácil de falsificar y no dejar un
  registro de aprobación tan claro como el de un Environment nativo.
- **Revisar si**: se necesita un flujo de aprobación con más de un revisor
  obligatorio simultáneo (hoy no está pedido).

## ADR-P7: Sin reconstrucción en el despliegue

- **Decision**: cada job de despliegue (`dev`, `pre`, `prod`) recibe como
  entrada el tag de imagen ya publicado por el job de build correspondiente
  y ejecuta únicamente `docker pull`/actualización del servicio de destino
  con ese tag exacto — ningún job de despliegue tiene un paso de `docker
  build`.
- **Rationale**: cumple directamente el Principio V de
  `pipeline-constitution.md`; verificable de forma trivial auditando que
  ningún job con `environment:` contiene un step de build.
- **Alternatives considered**: ninguna — es un principio no negociable, no
  una decisión de diseño abierta.

## ADR-P8: Umbral de severidad del análisis de vulnerabilidades (Trivy)

- **Decision**: el job de Trivy bloquea la fusión ante cualquier hallazgo de
  severidad `CRITICAL` o `HIGH`; severidades `MEDIUM`/`LOW` se reportan pero
  no bloquean.
- **Rationale**: bloquear ante cualquier severidad generaría demasiado
  ruido (muchas imágenes base tienen `LOW`/`MEDIUM` sin parche disponible);
  limitar el bloqueo a `CRITICAL`/`HIGH` mantiene el gate significativo sin
  paralizar el flujo por hallazgos de bajo riesgo real.
- **Alternatives considered**: bloquear ante cualquier hallazgo (descartado,
  demasiado ruidoso); no bloquear nunca y solo reportar (descartado, viola
  el espíritu del Principio VII de `pipeline-constitution.md`).

## Validación del propio pipeline

- **Decision**: no se construye un framework de test unitario para los
  archivos YAML. La validación es funcional: se abre una PR de prueba real
  por componente contra `develop` (y, más adelante, una fusión de prueba a
  `main`) y se observa que los gates, la publicación de imagen y el
  despliegue ocurren como se especificó — enfoque ya sugerido como criterio
  de cierre de la primera jornada de trabajo.
- **Rationale**: un pipeline de CI/CD es, en última instancia, configuración
  que orquesta herramientas externas (Spectral, oasdiff, Gitleaks, Trivy,
  Docker, GHCR); simularlo con un framework de test añadiría una capa de
  indirección sin aportar más confianza que probarlo contra el propio
  GitHub Actions real.

## Resueltas: Technical Context

Todas las entradas de Technical Context quedan resueltas con las decisiones
anteriores; no quedan marcadores `NEEDS CLARIFICATION` pendientes.
