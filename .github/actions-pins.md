# Acciones de terceros fijadas por SHA (Principio III)

Referencia única para todos los workflows de `.github/workflows/`. Ninguna
acción de esta lista se referencia por tag (`@v4`) en ningún workflow — solo
por el SHA exacto documentado aquí. Verificado contra la API de GitHub el
2026-07-13 (tag → commit real, incluyendo resolución de tags anotados donde
aplica).

| Acción | Tag verificado | SHA (usar en `uses:`) |
|---|---|---|
| `actions/checkout` | v7.0.0 | `9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0` |
| `actions/setup-java` | v5.5.0 | `0f481fcb613427c0f801b606911222b5b6f3083a` |
| `actions/setup-node` | v6.4.0 | `48b55a011bda9f5d6aeb4c2d9c7362e8dae4041e` |
| `stoplightio/spectral-action` | v0.8.13 | `6416fd018ae38e60136775066eb3e98172143141` |
| `oasdiff/oasdiff-action` | v0.1.6 | `024f6c399f9a21ada1addb0f9a36ce1bfac995f1` |
| `aquasecurity/trivy-action` | v0.36.0 | `ed142fd0673e97e23eac54620cfb913e5ce36c25` |
| `docker/build-push-action` | v7.3.0 | `53b7df96c91f9c12dcc8a07bcb9ccacbed38856a` |
| `docker/login-action` | v4.4.0 | `af1e73f918a031802d376d3c8bbc3fe56130a9b0` |
| `softprops/action-gh-release` | v3.0.1 | `718ea10b132b3b2eba29c1007bb80653f286566b` |
| `actions/upload-artifact` | v7.0.1 | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |
| `peter-evans/create-pull-request` | v8.1.1 | `5f6978faf089d4d20b00c7766989d076bb2fc7f1` |
| `anthropics/claude-code-action` | v1.0.171 | `e90deca47693f9457b72f2b53c17d7c445a87342` |

**Nota sobre `workflow-lint` (actionlint)**: no requerido por el enunciado del
reto, añadido como capa extra de verificación (2026-07-15) sobre el propio
`pipeline-config-guardian.yml`. Igual que Gitleaks, se invoca vía contenedor
Docker fijado por digest, no por la Action wrapper:
`docker://rhysd/actionlint@sha256:9d36088643581e728c969f35141f88139fec77280b2be23c1f66f8e40e1025e7`
(tag `latest`, verificado el 2026-07-15 contra Docker Hub). Corrido localmente
contra los 8 workflows antes de commitear: exit code 0, sin hallazgos.

**Nota sobre Gitleaks**: la Action gestionada `gitleaks/gitleaks-action`
exige una licencia (`GITLEAKS_LICENSE`) en repos privados/de organización;
es gratuita solo en repos públicos de cuenta personal. Para no depender de
gestionar esa licencia (ni de tener que verificar la visibilidad del repo),
los jobs `*-secrets-scan` invocan el binario Go de Gitleaks directamente vía
contenedor Docker fijado por digest
(`docker://zricethezav/gitleaks@sha256:b109bc5f8f76a38196a3e413704fc5b9e3c32360bce4e4b603bd6f45b3721dbb`,
verificado el 2026-07-13 contra Docker Hub) en vez de la Action wrapper —
ese camino sigue siendo MIT y no requiere licencia, independientemente de la
visibilidad del repositorio.

**Nota sobre `semantic-release`**: no es una GitHub Action, es un paquete
npm ejecutado dentro de un job de Node (`npx --yes semantic-release@25.0.7`,
versión verificada el 2026-07-13 contra el registro de npm). Se fija por
versión exacta de npm (no por SHA, mecanismo distinto a las Actions de
terceros), documentado en el propio job `calculate-version`.

**Revalidar si**: pasa mucho tiempo desde 2026-07-13 sin que se re-audite
esta tabla — un SHA fijado no cambia solo, pero conviene revisar
periódicamente si hay CVEs conocidas en las versiones fijadas aquí.

**Ambigüedad pendiente de confirmar con el instructor (Módulo 12)**: el
material del módulo enseña GHCR en detalle (login con `GITHUB_TOKEN`, sin
secrets adicionales) pero su diapositiva de "Criterios de éxito" dice
literalmente "Imagen en ACR" (Azure Container Registry). Esta
implementación usa **GHCR** de punta a punta, consistente con el resto del
módulo y con `.specify/pipeline-constitution.md`. Si el criterio de evaluación real
exige ACR, haría falta añadir login/push a Azure Container Registry (con
OIDC, coherente con el resto del módulo) además de o en vez de GHCR.
