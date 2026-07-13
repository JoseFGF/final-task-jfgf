# Quickstart: Validar el Pipeline de CI/CD

Guía de validación end-to-end. No sustituye a `README.md` (que documentará
la estrategia de ramas para cualquiera que llegue al repo) — esto es la
prueba de que cada pieza especificada realmente ocurre.

## Prerrequisitos

- Permisos de mantenedor en el repositorio (para configurar Environments y
  protección de rama antes de la primera prueba).
- El Environment `prod` configurado con su lista de revisores requeridos
  (ver `research.md`, ADR-P6) antes de probar User Story 5.

## Escenario 1 — Gate de PR bloquea una validación fallida (US1)

1. Crear una rama `feature/quickstart-test` con un cambio en `src/backend/`
   que rompa deliberadamente una prueba existente.
2. Abrir PR hacia `develop`.
3. Verificar: el check `backend-tests` falla, la PR queda marcada como no
   apta para fusionar, y el resto de checks del backend (Spectral, oasdiff,
   Gitleaks, ACs, Trivy, guardián) también se ejecutan (aunque el resultado
   de esa PR concreta ya esté condicionado por el primer fallo).
4. Corregir la prueba, hacer push a la misma rama.
5. Verificar: los checks vuelven a correr y esta vez todos pasan; la PR
   queda apta para fusionar (SC-001).

## Escenario 2 — Aislamiento por componente (US2)

1. Abrir una PR que solo modifique archivos en `src/frontend/`.
2. Verificar: solo corren los checks de `pr-validation-front.yml`; ningún
   check de `pr-validation-back.yml` aparece en la PR (SC-002).
3. Repetir con una PR que solo toque `src/backend/` y verificar lo simétrico.

## Escenario 3 — Snapshot y despliegue a dev (US3)

1. Fusionar una PR de prueba en `develop`.
2. Verificar en GHCR: existe una imagen nueva con tag
   `x.y.z-snapshot.{sha-corto}` correspondiente al componente modificado.
3. Verificar que el entorno `dev` está corriendo esa versión, en menos de 10
   minutos desde la fusión (SC-003).

## Escenario 4 — Entrega formal y despliegue a pre (US4)

1. Fusionar en `main` (vía `develop`) un conjunto de commits que sigan
   Conventional Commits desde la última versión.
2. Verificar: se publica una imagen con versión semver calculada
   automáticamente, existe un GitHub Release visible con el dist adjunto, y
   el entorno `pre` queda corriendo esa versión (SC-004).
3. Repetir con commits que NO sigan la convención y verificar que el
   pipeline señala explícitamente que no pudo calcular la versión, sin
   publicar nada (FR-011).

## Escenario 5 — Aprobación manual de producción (US5)

1. Con una versión ya desplegada en `pre`, verificar que `prod` no recibe
   nada automáticamente.
2. Como alguien de la lista de revisores del Environment `prod`, aprobar el
   despliegue.
3. Verificar: `prod` queda corriendo exactamente la misma imagen que ya
   estaba en `pre` (comparar el digest/tag, no solo el número de versión) —
   ninguna reconstrucción ocurrió (SC-006).
4. Repetir el intento de aprobación con una cuenta que NO esté en la lista
   de revisores y verificar que el sistema lo rechaza.

## Escenario 6 — Hotfix directo a main (US6)

1. Crear una rama `hotfix/quickstart-test` directamente desde `main`.
2. Abrir PR hacia `main`.
3. Verificar: se exigen los mismos checks que en cualquier PR hacia
   `develop` para el componente tocado (mismo mecanismo, ADR-P3).
4. Fusionar. Verificar: se despliega igual que cualquier otra entrega a
   `main`, y se abre automáticamente una PR de reintegración hacia `develop`
   (FR-019) — que a su vez pasa por sus propios gates antes de poder
   fusionarse.
5. Intentar fusionar una rama que no sea `hotfix/*` directamente a `main` y
   verificar que se rechaza (SC-007).

## Escenario 7 — Guardián de constitución en cambios al propio pipeline (FR-020)

1. Abrir una PR que solo modifique un archivo en `.github/workflows/`.
2. Verificar: aunque esa PR no toca `src/backend/` ni `src/frontend/`, el
   check `constitution-guardian` igualmente se ejecuta y puede bloquear la
   fusión.
