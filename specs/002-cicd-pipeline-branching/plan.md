# Implementation Plan: Pipeline de CI/CD y Estrategia de Ramas

**Branch**: `002-cicd-pipeline-branching` | **Date**: 2026-07-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-cicd-pipeline-branching/spec.md`

**Constitution que rige este feature**: `.specify/pipeline-constitution.md` (v1.1.0,
raíz del repo) — no `.specify/memory/constitution.md`, que rige el desarrollo
de la aplicación y es un documento independiente.

## Summary

Seis workflows de GitHub Actions (tres pares backend/frontend: validación de
PR, CI de develop, CI de main) más un job transversal de guardián de
constitución que también protege los propios archivos de configuración del
pipeline. Backend y frontend se validan, versionan y despliegan de forma
completamente independiente vía filtros de ruta nativos. La versión final se
calcula automáticamente por Conventional Commits; un hotfix reutiliza el
mismo workflow de `main` sin necesitar una ruta paralela, y se reintegra a
`develop` automáticamente. Ningún despliegue reconstruye: siempre usa la
imagen ya publicada en GHCR. Producción exige aprobación de una lista de
revisores concreta vía GitHub Environment.

## Technical Context

**Language/Version**: YAML de GitHub Actions (sin versión propia; runners
`ubuntu-latest`).

**Primary Dependencies**: `actions/checkout`, `actions/setup-java` (Maven),
`actions/setup-node` (npm), `stoplightio/spectral-action` (o equivalente CLI
de Spectral), `oasdiff/oasdiff-action`, `gitleaks/gitleaks-action`,
`aquasecurity/trivy-action`, `docker/build-push-action`,
`docker/login-action`, `softprops/action-gh-release`,
`actions/upload-artifact`, `semantic-release` (cálculo de versión, ADR-P2),
`peter-evans/create-pull-request` (reintegración de hotfix, ADR-P3), Claude
Code Action (guardián de constitución, ADR-P4). Todas fijadas por SHA
(Principio III).

**Storage**: N/A (no hay base de datos propia de este feature); GHCR actúa
como registro de artefactos, no como almacenamiento de datos de aplicación.

**Testing**: validación funcional contra GitHub Actions real (PRs de
prueba), no un framework de test de YAML — ver research.md.

**Target Platform**: GitHub Actions (runners `ubuntu-latest`), GHCR, GitHub
Environments (`dev`, `pre`, `prod`).

**Project Type**: configuración de infraestructura/CI-CD (no una aplicación
en sí); dos componentes desplegables independientes ya existentes
(`src/backend/`, `src/frontend/`).

**Performance Goals**: gate de PR completo en <15 min en el 95% de los casos
(SC-008); fusión a `develop` hasta desplegado en `dev` en <10 min (SC-003).

**Constraints**: pin por SHA en toda acción externa (Principio III); permisos
mínimos declarados por workflow (Principio IV); ningún job de despliegue
reconstruye (Principio V); `prod` exige aprobación de una lista de revisores
concreta, no cualquier mantenedor (Clarify); `hotfix/*` es la única
excepción al camino feature→develop→main (.specify/pipeline-constitution.md v1.1.0);
Trivy bloquea la fusión solo ante severidad CRITICAL/HIGH, reportando
MEDIUM/LOW sin bloquear (ADR-P8, añadido en la revisión de checklist de
spec-quality); el gate de PR se mide en tiempo de reloj real incluyendo cola
del runner, no solo ejecución activa (SC-008, aclarado en la misma revisión).

**Scale/Scope**: 2 componentes, 6 workflows principales + 1 job transversal
reutilizado (guardián de constitución) + 1 job condicional de reintegración
de hotfix. Sin necesidad de diseño para alta escala (repo de un solo
proyecto, no una organización con decenas de servicios).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio (`.specify/pipeline-constitution.md` v1.1.0) | Estado | Cómo se cumple |
|---|---|---|
| I. Spec-Antes-Que-YAML | PASS | `spec.md` y este `plan.md` se commitean antes que cualquier archivo en `.github/workflows/` |
| II. Flujos Independientes por Componente | PASS | `paths:` por componente en los 6 workflows principales (ADR-P1) |
| III. Pin por SHA en Acciones de Terceros | PASS | Toda acción listada en Technical Context se fija por SHA en Implement, no por tag |
| IV. Permisos Mínimos por Workflow | PASS | Cada workflow declarará su propio bloque `permissions:` en Tasks/Implement; `packages: write` solo en los jobs de publish |
| V. Sin Reconstrucción en Despliegue | PASS | ADR-P7: los jobs de despliegue solo hacen pull de la imagen ya publicada |
| VI. Aprobación Manual para Producción | PASS | Environment `prod` con revisores requeridos (ADR-P6) |
| VII. Gates Bloqueantes en Cada Fusión | PASS | Los 7 checks de `contracts/required-status-checks.md` son status checks requeridos en la protección de rama |

Sin violaciones — no se rellena Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-cicd-pipeline-branching/
├── plan.md                          # este archivo
├── research.md                      # Phase 0 — 7 ADRs + validación del propio pipeline
├── data-model.md                    # Phase 1 — Componente, Validación, Versión, Entorno, Rama
├── quickstart.md                    # Phase 1 — 7 escenarios de validación end-to-end
└── contracts/
    └── required-status-checks.md    # nombres de check exigidos por la protección de rama
```

### Repositorio (raíz)

```text
.github/
└── workflows/
    ├── pr-validation-back.yml
    ├── pr-validation-front.yml
    ├── pipeline-config-guardian.yml   # cambios a .github/workflows/**, .specify/pipeline-constitution.md, pipeline-spec.md
    ├── ci-develop-back.yml
    ├── ci-develop-front.yml
    ├── ci-main-back.yml               # incluye la ruta de hotfix (ADR-P3), sin workflow separado
    └── ci-main-front.yml

.specify/pipeline-constitution.md   # ya existe, v1.1.0 (movido a .specify/ el 2026-07-15)
pipeline-spec.md   # puntero al spec real (añadido el 2026-07-15)
specs/002-cicd-pipeline-branching/spec.md   # ya existe
```

**Structure Decision**: seis workflows principales (uno por combinación
componente × momento del ciclo: PR/develop/main) más un séptimo workflow
ligero (`pipeline-config-guardian.yml`) dedicado exclusivamente a FR-020, ya
que ese gate no pertenece a ningún componente y por tanto no encaja de forma
natural dentro de `pr-validation-back.yml` ni `pr-validation-front.yml` sin
duplicar su filtro de rutas. El hotfix NO tiene workflow propio (ADR-P3): se
apoya en que `ci-main-*.yml` ya dispara con cualquier push a `main`,
evitando un octavo archivo que habría que mantener sincronizado con el de
`main`.

## Complexity Tracking

*Sin violaciones de la Constitution Check que requieran justificación — tabla
vacía intencionadamente.*

## Validación del plan (paso equivalente al de 001-order-lifecycle-workflow)

Cruce spec↔plan↔contrato realizado el 2026-07-13: los 20 FRs y las 8 Success
Criteria de `spec.md` tienen su correspondencia en `contracts/required-status-checks.md`
o en `research.md`/`data-model.md` para los que no se expresan como un check
(FR-006, FR-007, FR-016 son comportamiento transversal de varios jobs, no un
check propio). Sin marcadores `NEEDS CLARIFICATION` pendientes. Plan listo
para `/speckit.tasks`.

**Re-validación tras el checklist `spec-quality.md`** (mismo día): la revisión
retroactiva del checklist introdujo cambios en `spec.md` (entidad Pull
Request, FR-002 y FR-011 acotados, SC-008 aclarado, un edge case nuevo sobre
fallo de reintegración de hotfix, una asunción nueva sobre Conventional
Commits) y un ADR nuevo en `research.md` (ADR-P8, umbral de Trivy). Ninguno
de estos cambios altera el número de workflows, su estructura de triggers o
la Constitution Check — siguen siendo 7/7 PASS sin violaciones. Se propagó:
- `data-model.md`: añadida la entidad "Pull Request" y anotado el umbral de
  Trivy en la tabla de Validación (Gate).
- Este `plan.md`: Constraints ampliado con el umbral de Trivy (ADR-P8) y la
  definición de "tiempo de reloj real, cola incluida" de SC-008.
- `contracts/required-status-checks.md` y `tasks.md` no requieren cambios:
  ninguno de los ajustes añade, quita o renombra un check ni una tarea, solo
  precisa criterios ya cubiertos por los checks/tareas existentes.

Plan sigue listo para `/speckit.tasks` (ya generado) y para `/speckit.analyze`.
