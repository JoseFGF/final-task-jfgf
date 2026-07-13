# Plantilla de permisos mínimos por tipo de job (Principio IV)

Ningún workflow hereda el bloque `permissions:` por defecto de GitHub (que en
muchos repos es de lectura/escritura amplia); cada uno declara el suyo
propio, explícito, al nivel de workflow. Esta tabla es la referencia que
sigue cada `.yml` de `.github/workflows/`.

| Tipo de job | Permisos exactos | Por qué |
|---|---|---|
| Validación de PR (tests, lint, contrato, secretos, imagen) | `contents: read` | Solo necesita leer el código para compilarlo/analizarlo |
| `constitution-guardian` | `contents: read`, `pull-requests: read` | Lee el diff de la PR vía la API del agente; no escribe nada |
| Build + publish de imagen (`build-and-publish-*`) | `contents: read`, `packages: write` | `packages: write` es el único permiso que habilita `docker push` a GHCR — se declara solo aquí, en ningún otro job |
| Cálculo de versión (`calculate-version`) | `contents: read` | Solo lee el historial de commits; no escribe tags ni releases directamente |
| Entrega formal (`build-and-publish-release`) | `contents: write`, `packages: write` | `contents: write` es necesario para que `softprops/action-gh-release` cree el GitHub Release; `packages: write` para publicar la imagen |
| Despliegue (`deploy-dev`, `deploy-pre`, `deploy-prod`) | `contents: read`, `packages: read` | Solo necesita hacer `docker pull` de la imagen ya publicada — nunca `packages: write` |
| Reintegración de hotfix (`reintegrate-hotfix-to-develop`) | `contents: write`, `pull-requests: write` | Necesita crear una rama y abrir la PR de reintegración (`peter-evans/create-pull-request`) |

**Regla dura**: si un job no aparece en esta tabla porque hace algo nuevo, se
añade una fila aquí en el mismo cambio que lo introduce — nunca se copia el
bloque `permissions:` de otro workflow sin revisar si de verdad necesita lo
mismo.
