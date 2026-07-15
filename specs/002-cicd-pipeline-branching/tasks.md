---

description: "Task list for 002-cicd-pipeline-branching"
---

# Tasks: Pipeline de CI/CD y Estrategia de Ramas

**Input**: Design documents from `specs/002-cicd-pipeline-branching/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, `contracts/required-status-checks.md`, quickstart.md

**Tests**: no hay framework de test de YAML (ver research.md, "Validación del
propio pipeline") — la verificación es funcional, contra GitHub Actions real,
ejecutando los escenarios de `quickstart.md`. Cada historia incluye su propia
tarea de verificación manual en vez de una tarea de test automatizado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos/jobs distintos, sin dependencias)
- **[Story]**: US1–US6, mapeadas a spec.md
- Cada tarea incluye la ruta de archivo o el nombre exacto de job afectado

## Path Conventions

Todos los workflows viven en `.github/workflows/` (raíz del repo). Los
nombres de job deben coincidir exactamente con
`contracts/required-status-checks.md` — la protección de rama depende de
esos nombres literales.

## Phase 1: Setup

- [X] T001 Crear el directorio `.github/workflows/` si no existe

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura común que bloquea las 6 historias de usuario.

**⚠️ CRITICAL**: Ninguna historia arranca hasta cerrar esta fase.

- [X] T002 [P] Reunir y fijar por SHA (no por tag) cada Action de terceros a usar (`actions/checkout`, `actions/setup-java`, `actions/setup-node`, Spectral, `oasdiff`, Gitleaks, Trivy, `docker/build-push-action`, `docker/login-action`, `softprops/action-gh-release`, `actions/upload-artifact`, `semantic-release`, `peter-evans/create-pull-request`, Claude Code Action), documentado como referencia en `research.md` (Principio III) — SHAs verificados contra la API de GitHub y volcados en `.github/actions-pins.md`
- [X] T003 Crear el workflow reutilizable `.github/workflows/_constitution-guardian.yml` (`workflow_call`) que invoca la API del agente contra `pipeline-constitution.md` (y `.specify/memory/constitution.md` cuando la PR toca código de aplicación) — FR-004, ADR-P4
- [ ] T004 Configurar los GitHub Environments `dev` y `pre` sin protección, y `prod` con una lista de revisores requeridos distinta del conjunto general de mantenedores — ADR-P6, Clarify — **requiere acceso de administrador al repo en GitHub, no aplicable desde esta sesión; instrucciones exactas dejadas en `.github/ENVIRONMENTS_SETUP.md`**
- [X] T005 [P] Documentar la plantilla de permisos mínimos por tipo de job (lectura de contenido, `packages: write` solo en jobs de publish, `pull-requests: write` solo en el job de reintegración de hotfix) — Principio IV — ver `.github/permissions-reference.md`

**Checkpoint**: Con esto listo, las historias de usuario pueden empezar.

---

## Phase 3: User Story 1 - Bloquear una fusión con validaciones incompletas (Priority: P1) 🎯 MVP

**Goal**: Ninguna PR hacia `develop` puede fusionarse si falla alguna de sus validaciones exigidas (FR-001 a FR-005).

**Independent Test**: Abrir una PR con un cambio que rompa una validación, confirmar que queda bloqueada; corregirlo y confirmar que pasa a poder fusionarse (Escenario 1 de `quickstart.md`).

- [X] T006 [US1] Crear `.github/workflows/pr-validation-back.yml` con trigger `pull_request` hacia `develop`, `paths: ['src/backend/**', 'tests/**']` (ampliado tras descubrir que `build-helper-maven-plugin` añade `tests/{unit,contract,integration}` como test-sources del módulo backend — ver `src/backend/pom.xml`), bloque `permissions:` mínimo (T005)
- [X] T007 [US1] [P] Job `backend-tests` en `pr-validation-back.yml` (Maven sobre `src/backend/`)
- [X] T008 [US1] [P] Job `backend-openapi-lint` en `pr-validation-back.yml` (Spectral contra `contracts/openapi.yaml` de la app; se creó `.spectral.yml` en la raíz — no existía, y sin ruleset la Action no aplica ninguna regla real)
- [X] T009 [US1] [P] Job `backend-breaking-changes` en `pr-validation-back.yml` (oasdiff)
- [X] T010 [US1] [P] Job `backend-secrets-scan` en `pr-validation-back.yml` (Gitleaks, vía contenedor Docker fijado por digest en vez de la Action gestionada — ver `.github/actions-pins.md`)
- [X] T011 [US1] [P] Job `backend-acceptance-check` en `pr-validation-back.yml` (nuevo script real `scripts/acceptance-check.sh`, levanta postgres+backend vía `docker compose` y hace comprobaciones HTTP negras contra la API real con credenciales de seed)
- [X] T012 [US1] [P] Job `backend-image-scan` en `pr-validation-back.yml` (Trivy sobre la imagen que se construiría; bloquea solo ante severidad CRITICAL/HIGH, reporta MEDIUM/LOW sin bloquear — FR-002, ADR-P8)
- [X] T013 [US1] Job `constitution-guardian` en `pr-validation-back.yml`, invocando el workflow reutilizable de T003
- [X] T014 [US1] Crear `.github/workflows/pr-validation-front.yml` con trigger `pull_request` hacia `develop`, `paths: ['src/frontend/**']`, permisos mínimos
- [X] T015 [US1] [P] Job `frontend-tests` en `pr-validation-front.yml` (npm sobre `src/frontend/`)
- [X] T016 [US1] [P] Job `frontend-secrets-scan` en `pr-validation-front.yml` (Gitleaks)
- [X] T017 [US1] Job `constitution-guardian` en `pr-validation-front.yml`, invocando el mismo workflow reutilizable de T003
- [ ] T018 [US1] Configurar la protección de rama de `develop` exigiendo los 7 checks de backend/frontend listados en `contracts/required-status-checks.md` — **requiere acceso de administrador al repo, ver `.github/ENVIRONMENTS_SETUP.md`**
- [ ] T019 [US1] Verificación manual: ejecutar el Escenario 1 de `quickstart.md` — **pendiente: requiere un repositorio GitHub real con Actions habilitado; no ejecutable desde esta sesión local**

**Checkpoint**: US1 funcional e independientemente verificable — es el MVP de este feature.

---

## Phase 4: User Story 2 - No disparar el pipeline del componente que no cambió (Priority: P1)

**Goal**: Un cambio que solo toca un componente no dispara ningún trabajo del otro (FR-006, FR-007).

**Independent Test**: Abrir una PR que solo modifique `src/frontend/` y confirmar que ningún job de `pr-validation-back.yml` aparece, y viceversa (Escenario 2 de `quickstart.md`).

- [X] T020 [US2] Confirmar y, si hace falta, ajustar el filtro `paths: ['src/backend/**']` de `pr-validation-back.yml` (T006) para que no se dispare con cambios exclusivos de frontend — confirmado: filtro es `['src/backend/**', 'tests/**']`, disjunto de `src/frontend/**`
- [X] T021 [US2] [P] Confirmar y, si hace falta, ajustar el filtro `paths: ['src/frontend/**']` de `pr-validation-front.yml` (T014) para que no se dispare con cambios exclusivos de backend — confirmado: filtro disjunto
- [ ] T022 [US2] Verificación manual: ejecutar el Escenario 2 de `quickstart.md` — **pendiente: requiere GitHub Actions real**

**Checkpoint**: US1 + US2 cubren el gate de PR completo, aislado por componente.

---

## Phase 5: User Story 3 - Publicar una versión de prueba al integrar en develop (Priority: P2)

**Goal**: Toda fusión a `develop` construye, publica y despliega automáticamente a `dev` (FR-008, FR-009).

**Independent Test**: Fusionar un cambio de prueba en `develop` y confirmar que aparece una imagen snapshot en GHCR y que `dev` la está corriendo, en menos de 10 minutos (Escenario 3 de `quickstart.md`).

- [X] T023 [US3] Crear `.github/workflows/ci-develop-back.yml`, trigger `push` a `develop`, `paths: ['src/backend/**', 'tests/**']`, permisos mínimos
- [X] T024 [US3] Job `build-and-publish-snapshot` en `ci-develop-back.yml` (build Maven, `docker build` + push a GHCR, tag `x.y.z-snapshot.{sha-corto}`, ADR-P2/ADR-P5)
- [X] T025 [US3] Job `deploy-dev` en `ci-develop-back.yml` (`environment: dev`, solo `docker pull` de la imagen recién publicada, sin build — Principio V; sin host/clúster de destino real definido en plan.md, "desplegar" se demuestra con el pull)
- [X] T026 [US3] [P] Crear `.github/workflows/ci-develop-front.yml`, trigger `push` a `develop`, `paths: ['src/frontend/**']`, permisos mínimos
- [X] T027 [US3] [P] Job `build-and-publish-snapshot` en `ci-develop-front.yml`
- [X] T028 [US3] [P] Job `deploy-dev` en `ci-develop-front.yml`
- [X] T029 [US3] Configurar `actions/upload-artifact` (dist comprimido, retención 90 días) en ambos `ci-develop-*.yml` — corregido tras auditoría posterior (2026-07-15): el paso previo no compilaba nada real (faltaba `mvn package` en backend y `npm ci` en frontend) y tragaba el fallo en silencio, subiendo el artifact vacío; ahora ambos ejecutan el build real antes de comprimir
- [ ] T030 [US3] Verificación manual: ejecutar el Escenario 3 de `quickstart.md` — **pendiente: requiere GitHub Actions real**

**Checkpoint**: US1-US3 cubren PR + integración continua con despliegue automático a dev.

---

## Phase 6: User Story 4 - Publicar una entrega formal al llegar a main (Priority: P2)

**Goal**: Toda fusión a `main` calcula versión, publica una entrega formal y despliega a `pre` (FR-010 a FR-013).

**Independent Test**: Fusionar en `main` un conjunto de commits que sigan Conventional Commits y confirmar entrega + despliegue a `pre`; repetir sin convención y confirmar que se señala el fallo de cálculo sin inventar versión (Escenario 4 de `quickstart.md`).

- [X] T031 [US4] Crear `.github/workflows/ci-main-back.yml`, trigger `push` a `main`, `paths: ['src/backend/**', 'tests/**']`, permisos mínimos
- [X] T032 [US4] Job `calculate-version` en `ci-main-back.yml` (semantic-release/Conventional Commits, ADR-P2; si ningún commit acumulado coincide con un prefijo reconocido — `feat:`, `fix:`, pie `BREAKING CHANGE:` — falla explícitamente el job — FR-011, no inventa un número)
- [X] T033 [US4] Job `build-and-publish-release` en `ci-main-back.yml` (imagen con la versión calculada + GitHub Release con el dist comprimido como asset, `softprops/action-gh-release`) — corregido tras auditoría posterior (2026-07-15): la llamada a `action-gh-release` no incluía `files:`, publicaba el Release sin ningún asset adjunto; ahora compila el jar real y lo adjunta como `.zip`
- [X] T034 [US4] Job `deploy-pre` en `ci-main-back.yml` (`environment: pre`, solo pull de la imagen recién publicada)
- [X] T035 [US4] [P] Crear `.github/workflows/ci-main-front.yml`, trigger `push` a `main`, `paths: ['src/frontend/**']`, permisos mínimos
- [X] T036 [US4] [P] Job `calculate-version` en `ci-main-front.yml`
- [X] T037 [US4] [P] Job `build-and-publish-release` en `ci-main-front.yml` — corregido tras auditoría posterior (2026-07-15): mismo problema que T033 (sin `files:` en el Release); ahora ejecuta `npm ci` + `ng build` real y adjunta el dist como `.zip`
- [X] T038 [US4] [P] Job `deploy-pre` en `ci-main-front.yml`
- [ ] T039 [US4] Verificación manual: ejecutar el Escenario 4 de `quickstart.md` (incluido el caso de versión no calculable) — **pendiente: requiere GitHub Actions real**

**Checkpoint**: US1-US4 cubren el camino completo feature→develop→main hasta pre.

---

## Phase 7: User Story 5 - Exigir autorización humana antes de tocar producción (Priority: P3)

**Goal**: Ningún despliegue llega a `prod` sin autorización de la lista de revisores de ese Environment (FR-014 a FR-016).

**Independent Test**: Con una versión en `pre`, confirmar que `prod` no recibe nada hasta que alguien de la lista de revisores lo aprueba, y que una cuenta fuera de esa lista no puede hacerlo (Escenario 5 de `quickstart.md`).

- [X] T040 [US5] Job `deploy-prod` en `ci-main-back.yml` (`environment: prod`, usa la lista de revisores de T004; pull de la misma imagen ya desplegada en `pre`, nunca reconstruye)
- [X] T041 [US5] [P] Job `deploy-prod` en `ci-main-front.yml` (mismo patrón)
- [ ] T042 [US5] Verificación manual: ejecutar el Escenario 5 de `quickstart.md`, incluyendo el intento de aprobación por una cuenta fuera de la lista de revisores — **pendiente: requiere GitHub Actions real + T004 configurado**

**Checkpoint**: US1-US5 cubren el ciclo completo hasta producción con aprobación humana.

---

## Phase 8: User Story 6 - Corregir producción sin esperar a que develop esté listo (Priority: P3)

**Goal**: Una rama `hotfix/*` llega a `main` sin pasar por `develop`, con los mismos gates, y se reintegra a `develop` (FR-017 a FR-019).

**Independent Test**: Crear una rama de corrección desde `main`, fusionarla, y confirmar que pasó los mismos gates, que se desplegó igual que cualquier otra entrega, y que se abrió una PR de reintegración hacia `develop` (Escenario 6 de `quickstart.md`).

- [X] T043 [US6] Confirmar en `ci-main-back.yml`/`ci-main-front.yml` (T031/T035) que el trigger `push` a `main` no distingue si el origen fue `develop` o `hotfix/*` (ADR-P3); documentarlo con un comentario en ambos workflows
- [X] T044 [US6] Job `reintegrate-hotfix-to-develop` en `ci-main-back.yml`, condicional a que la rama de origen sea `hotfix/*` (abre PR automática hacia `develop` con `peter-evans/create-pull-request`, FR-019) — detección real vía API de GitHub (`GET /commits/{sha}/pulls`), no heurística de texto del commit de merge
- [X] T045 [US6] [P] Job `reintegrate-hotfix-to-develop` en `ci-main-front.yml`, mismo patrón
- [ ] T046 [US6] Configurar la protección de rama de `main` para rechazar merges directos que no vengan de `develop` ni de una rama `hotfix/*` (FR-018) — **requiere acceso de administrador al repo, ver `.github/ENVIRONMENTS_SETUP.md`**
- [ ] T047 [US6] Verificación manual: ejecutar el Escenario 6 de `quickstart.md` — **pendiente: requiere GitHub Actions real**

**Checkpoint**: Las 6 historias de usuario funcionan de forma independiente y en conjunto.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Cierre transversal que no pertenece a ninguna historia (protege el propio pipeline) y verificación final.

- [X] T048 [P] Crear `.github/workflows/pipeline-config-guardian.yml`, trigger `pull_request` con `paths: ['.github/workflows/**', 'pipeline-constitution.md', 'pipeline-spec.md']`, job `constitution-guardian` reutilizando el workflow de T003 (FR-020) — usa la ruta real `specs/002-cicd-pipeline-branching/**` en vez del nombre genérico `pipeline-spec.md` (que no existe como archivo en este repo)
- [X] T055 Añadir job `workflow-lint` en `pipeline-config-guardian.yml` (actionlint vía contenedor Docker fijado por digest, `.github/actions-pins.md`) — no exigido por el reto, capa extra de verificación de sintaxis de los propios workflows añadida el 2026-07-15; corrido localmente contra los 8 workflows antes de commitear, exit code 0
- [ ] T049 Añadir el check `constitution-guardian` (vía `pipeline-config-guardian.yml`) a la protección de rama de `develop` y de `main` — **requiere acceso de administrador al repo, ver `.github/ENVIRONMENTS_SETUP.md`**
- [ ] T050 Verificación manual: ejecutar el Escenario 7 de `quickstart.md` — **pendiente: requiere GitHub Actions real**
- [X] T051 [P] Auditar los 7 workflows y confirmar que toda Action de terceros está fijada por SHA, no por tag (Principio III) — auditado con grep real, 0 coincidencias de `@v`/`@master`/`@main`
- [X] T052 [P] Auditar los 7 workflows y confirmar que cada uno declara su propio bloque `permissions:` mínimo, sin heredar de otro (Principio IV) — auditado con grep real, los 7 workflows tienen bloque `permissions:` propio
- [X] T053 [P] Auditar los jobs `deploy-dev`, `deploy-pre` y `deploy-prod` de los 7 workflows y confirmar que ninguno contiene un paso `docker build` (o equivalente) — solo `docker pull`/actualización con el tag ya publicado (Principio V, FR-016, ADR-P7) — auditado con grep real, 0 coincidencias
- [X] T054 Completar `README.md` (raíz) con la estrategia de ramas, cómo abrir una PR de prueba para disparar la validación, cómo verificar que la imagen snapshot llegó a GHCR (entrega del reto), dónde consultar el registro nativo de aprobaciones de producción de GitHub Environments (quién autorizó y cuándo — SC-005), y una nota sobre cómo observar en la práctica el cumplimiento de SC-008 (duración del gate de PR visible en la pestaña Actions, dado que no existe un dashboard dedicado en este alcance)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup; **bloquea** todas las historias
- **User Stories (Phase 3-8)**: dependen de Foundational
  - US1 y US2 (P1) son la base del gate de PR; US2 depende de que existan los workflows de US1 (T006, T014) para poder ajustar sus filtros
  - US3 y US4 (P2) dependen conceptualmente de que exista un gate de PR (US1) que ya haya validado lo que se fusiona, pero sus workflows (`ci-develop-*`, `ci-main-*`) son archivos nuevos e independientes de los de US1/US2
  - US5 (P3) depende de que exista `ci-main-*.yml` (US4) para añadirle el job `deploy-prod`
  - US6 (P3) depende de que exista `ci-main-*.yml` (US4) para añadirle el job de reintegración
- **Polish (Phase 9)**: T048 depende de T003 (Foundational); T049 depende de que T018 (US1) y la protección de `main` (T046, US6) ya existan; el resto es independiente

### Dentro de cada historia

- El workflow base (trigger + permisos) se crea antes que sus jobs individuales
- Los jobs marcados `[P]` dentro de una misma historia tocan partes distintas del mismo archivo o archivos distintos (backend vs frontend) y pueden hacerse en paralelo
- La verificación manual de cada historia es siempre la última tarea de esa fase

## Parallel Example: User Story 1

```bash
# Jobs de pr-validation-back.yml en paralelo (T007-T012, distintos jobs del mismo archivo):
Task: "Job backend-tests en pr-validation-back.yml"
Task: "Job backend-openapi-lint en pr-validation-back.yml"
Task: "Job backend-breaking-changes en pr-validation-back.yml"
Task: "Job backend-secrets-scan en pr-validation-back.yml"
Task: "Job backend-acceptance-check en pr-validation-back.yml"
Task: "Job backend-image-scan en pr-validation-back.yml"

# En paralelo con lo anterior, el workflow de frontend completo (T014-T017):
Task: "Crear pr-validation-front.yml con sus jobs"
```

## Implementation Strategy

### MVP First (User Stories 1 y 2, ambas P1)

1. Setup → Foundational (bloqueante)
2. US1 (gate de PR completo)
3. US2 (aislamiento por componente — en la práctica, mayormente ya viene dado por los filtros `paths` de US1; esta fase es sobre todo de verificación)
4. **STOP y VALIDAR**: correr Escenarios 1 y 2 de `quickstart.md`
5. Continuar con US3 → US4 (P2), luego US5 → US6 (P3), y cerrar con Polish

### Entrega incremental

Setup+Foundational → US1+US2 (gate de PR, MVP) → US3 (snapshot a dev) → US4
(entrega formal a pre) → US5 (aprobación de prod) → US6 (hotfix) → Polish
(guardián del propio pipeline + auditorías + README), validando cada
checkpoint con su escenario de `quickstart.md` antes de avanzar.

## Notes

- `[P]` = archivos o jobs distintos, sin dependencia entre sí
- `[Story]` mapea cada tarea a su historia de usuario para trazabilidad
- No hay tests automatizados de YAML (ver research.md); cada historia cierra
  con una verificación manual explícita contra GitHub Actions real, nunca se
  da por completa sin ella
- Ningún nombre de job puede cambiar sin actualizar
  `contracts/required-status-checks.md` en el mismo commit (o uno anterior)
  que actualice la protección de rama correspondiente
- Re-validado tras la revisión de `checklists/spec-quality.md` y el
  relanzamiento de `/speckit.plan` (2026-07-13): las 53 tareas originales
  seguían cubriendo la spec sin cambios estructurales; solo T012 y T032 se
  enriquecieron con el detalle de umbral de Trivy (ADR-P8) y de prefijos de
  Conventional Commits reconocidos (FR-011), que antes vivían solo en
  `research.md`
- Ampliado tras `/speckit.analyze` (2026-07-13): se añadió T053 (auditoría
  del Principio V / no-reconstrucción, antes sin tarea dedicada a diferencia
  de T051/T052) y se enriqueció la antigua T053 (README, renumerada T054)
  con la ubicación del registro de aprobaciones de producción (SC-005) y una
  nota sobre cómo observar SC-008 en la práctica. Total: 54 tareas
