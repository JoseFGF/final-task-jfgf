# Configuración manual requerida (T004, T018, T046, T049)

Estos pasos viven en la configuración del repositorio de GitHub (Settings),
no en ningún archivo de este repo — no se pueden aplicar escribiendo YAML,
y esta sesión no tiene acceso a la API de administración del repositorio
(no hay `gh` CLI ni token con permisos de administrador disponible aquí).
Quedan documentados para que quien tenga permisos de mantenedor los aplique
antes de la primera ejecución real del pipeline.

## 1. GitHub Environments (T004, ADR-P6)

En **Settings → Environments**, crear tres entornos:

- **`dev`**: sin regla de protección (despliegue automático al fusionar en `develop`).
- **`pre`**: sin regla de protección (despliegue automático al fusionar en `main`).
- **`prod`**: con **Required reviewers** activado, añadiendo una lista de
  personas concreta y distinta del conjunto general de mantenedores del
  repositorio (Clarify, sesión 2026-07-13) — no marcar "any maintainer".

## 2. Secretos necesarios

- `ANTHROPIC_API_KEY`: secreto a nivel de repositorio (o de organización),
  usado por el job `constitution-guardian` (`_constitution-guardian.yml`).
- `GITHUB_TOKEN`: no requiere configuración manual, GitHub lo inyecta
  automáticamente por workflow run.

## 3. Protección de rama de `develop` (T018)

En **Settings → Branches → Add branch protection rule** para `develop`,
exigir como status checks requeridos exactamente los nombres listados en
`specs/002-cicd-pipeline-branching/contracts/required-status-checks.md`
para `pr-validation-back.yml`/`pr-validation-front.yml`:

`backend-tests`, `backend-openapi-lint`, `backend-breaking-changes`,
`backend-secrets-scan`, `backend-acceptance-check`, `backend-image-scan`,
`frontend-tests`, `frontend-secrets-scan`, `constitution-guardian`.

## 4. Protección de rama de `main` (T046, T049)

Regla de protección para `main`:

- Exigir el check `constitution-guardian` (vía `pipeline-config-guardian.yml`,
  T049) además de los checks del componente que corresponda.
- **Restringir quién puede hacer push directo** a `main` de forma que solo
  se acepten fusiones que vengan de `develop` o de una rama `hotfix/*`
  (FR-018) — GitHub no tiene un selector nativo de "rama origen" en branch
  protection; la forma real de cumplir FR-018 es una regla de repositorio
  (**Rulesets → Branch rulesets**) con una condición de rama de origen, o,
  si el plan de GitHub del repo no soporta rulesets, aplicando restricciones
  de equipo/usuario combinadas con un job de verificación temprano en
  `ci-main-*.yml` que falle explícitamente si `github.event.before` no
  corresponde a un commit alcanzable desde `develop` ni la rama tiene
  prefijo `hotfix/`. Documentar aquí cuál de las dos vías se usó una vez
  decidido, porque no es una tarea que se pueda cerrar solo con YAML.

## 5. Verificación

Una vez aplicado lo anterior, ejecutar los Escenarios 1-7 de
`specs/002-cicd-pipeline-branching/quickstart.md` contra PRs/fusiones reales
para confirmar que el pipeline completo se comporta como se especificó.
