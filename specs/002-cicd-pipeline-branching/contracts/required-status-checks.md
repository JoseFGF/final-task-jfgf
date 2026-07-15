# Contrato: Status Checks Requeridos

A diferencia del feature 001 (una API REST con `contracts/openapi.yaml`), lo
que este feature expone como "interfaz" hacia el resto del sistema (la
protección de rama de GitHub) es un conjunto fijo de nombres de job/check.
La protección de rama de `develop` y `main` DEBE referenciar exactamente
estos nombres como checks requeridos — si un nombre de job cambia en
Implement sin actualizar este contrato, la protección de rama queda
desincronizada silenciosamente (un check que ya no existe nunca se marca
"passed", bloqueando fusiones para siempre; o un check nuevo no se exige
nunca).

## `pr-validation-back.yml` (dispara: PR hacia `develop`, `paths: src/backend/**`)

| Job (nombre exacto de check) | Corresponde a |
|---|---|
| `backend-tests` | FR-002 (pruebas Maven) |
| `backend-openapi-lint` | FR-002 (Spectral) |
| `backend-breaking-changes` | FR-002 (oasdiff) |
| `backend-secrets-scan` | FR-002 (Gitleaks) |
| `backend-acceptance-check` | FR-002 (script de ACs vs API real) |
| `backend-image-scan` | FR-002 (Trivy) |
| `constitution-guardian` | FR-004 |

## `pr-validation-front.yml` (dispara: PR hacia `develop`, `paths: src/frontend/**`)

| Job (nombre exacto de check) | Corresponde a |
|---|---|
| `frontend-tests` | FR-003 (pruebas npm) |
| `frontend-secrets-scan` | FR-003 (Gitleaks) |
| `constitution-guardian` | FR-004 |

## `pipeline-config-guardian.yml` (dispara: PR con cambios en `.github/workflows/**`, `.specify/pipeline-constitution.md`, `pipeline-spec.md`)

| Job (nombre exacto de check) | Corresponde a |
|---|---|
| `workflow-lint` | No exigido por el reto; verificación extra de sintaxis (`actionlint`) sobre los propios workflows, añadida el 2026-07-15 |
| `constitution-guardian` | FR-020 (mismo job/nombre que en los dos anteriores, reutilizado — no un guardián distinto) |

## `ci-develop-back.yml` / `ci-develop-front.yml` (dispara: push a `develop`, filtrado por componente)

| Job | Corresponde a |
|---|---|
| `build-and-publish-snapshot` | FR-008 |
| `deploy-dev` | FR-009 |

## `ci-main-back.yml` / `ci-main-front.yml` (dispara: push a `main`, filtrado por componente — incluye llegada vía `develop` o vía `hotfix/*`, ADR-P3)

| Job | Corresponde a |
|---|---|
| `calculate-version` | FR-010, FR-011 |
| `build-and-publish-release` | FR-010, FR-012 |
| `deploy-pre` | FR-013 |
| `deploy-prod` (Environment `prod`, con revisores requeridos) | FR-014, FR-015, FR-016 |
| `reintegrate-hotfix-to-develop` (solo se ejecuta si la rama de origen es `hotfix/*`) | FR-019 |

## Reglas de este contrato

- Ningún nombre de job de esta tabla puede eliminarse o renombrarse sin
  actualizar este archivo en el mismo commit (o uno inmediatamente anterior)
  que actualiza la protección de rama.
- `constitution-guardian` es intencionalmente el mismo nombre de job en los
  tres workflows de PR — la protección de rama de `develop` lo exige una
  sola vez por nombre, sin importar cuál de los tres lo produjo en una PR
  concreta.
- `deploy-prod` es el único job de toda esta tabla que depende de una
  aprobación humana (GitHub Environment `prod`, ADR-P6); ningún otro debe
  configurarse con esa misma protección.
