# Data Model: Pipeline de CI/CD y Estrategia de Ramas

Este feature no maneja datos persistidos en una base de datos: sus "entidades"
son construcciones de configuración que viven en workflows de GitHub Actions
y en los propios metadatos de GitHub (Environments, Releases, Registry). Se
documentan aquí con el mismo rigor que un modelo de datos porque determinan
directamente cómo se escriben los archivos `.yml` en Implement.

## Componente

| Campo | Valor | Notas |
|---|---|---|
| `nombre` | `backend` \| `frontend` | Fijo, no configurable por PR |
| `ruta` | `src/backend/` \| `src/frontend/` | Usada en los filtros `paths:` |
| `runtimeDeValidación` | Maven \| npm | Determina qué gates de FR-002/FR-003 aplican |
| `gatesExigidos` | ver tabla "Validación" | Backend tiene 6, frontend tiene 3 (más el guardián, transversal) |

## Validación (Gate)

| Gate | Herramienta | Componente | Bloqueante |
|---|---|---|---|
| Pruebas del código | Maven (backend) / npm (frontend) | Ambos | Sí |
| Contrato de API | Spectral | Backend | Sí |
| Breaking changes de API | oasdiff | Backend | Sí |
| Secretos filtrados | Gitleaks | Ambos | Sí |
| Criterios de aceptación vs API real | script propio | Backend | Sí |
| Vulnerabilidades de imagen | Trivy (bloquea CRITICAL/HIGH, reporta MEDIUM/LOW — ADR-P8) | Backend únicamente (FR-003 no exige este gate en frontend) | Sí |
| Guardián de constitución | Claude Code Action | Ambos + archivos de config del pipeline (FR-020) | Sí |

## Versión publicada

| Campo | En `develop` | En `main` |
|---|---|---|
| `formato` | `x.y.z-snapshot.{sha-corto}` | `x.y.z` |
| `origen del cálculo` | versión base + sha del commit | commits desde la última versión, Conventional Commits (ADR-P2) |
| `dónde se publica` | GHCR (imagen) + workflow artifact (dist, 90 días) | GHCR (imagen) + GitHub Release (dist, permanente) |
| `asociada a` | commit de `develop` | commit de `main` (vía `develop` o vía `hotfix/*`) |

## Entorno

| Entorno | Disparo | Aprobación | Usa imagen de |
|---|---|---|---|
| `dev` | merge a `develop` | automática | versión snapshot recién publicada |
| `pre` | merge a `main` | automática | versión final recién publicada |
| `prod` | tras despliegue en `pre` | manual, lista de revisores del Environment (Clarify) | la misma imagen ya desplegada en `pre`, nunca reconstruida (Principio V) |

## Rama

| Rama | Rol | Participa en el pipeline |
|---|---|---|
| `feature/*` | trabajo en curso | Solo dispara validación de PR hacia `develop`; nunca se despliega |
| `develop` | integración continua | Dispara build+publish+deploy a `dev` |
| `main` | versiones finales | Dispara build+publish+release+deploy a `pre`; punto de partida para `prod` |
| `hotfix/*` | corrección urgente (ampliación v1.1.0 de la constitution) | Dispara PR hacia `main` con los mismos gates que `develop`; tras fusionar, abre PR de reintegración hacia `develop` |

## Pull Request

| Campo | Valor | Notas |
|---|---|---|
| `rama origen` | `feature/*` \| `hotfix/*` \| (reintegración de hotfix) | Determina qué conjunto de validaciones se exige |
| `rama destino` | `develop` \| `main` | Toda PR hacia `main` que no venga de `develop` ni de `hotfix/*` se rechaza (FR-018) |
| `es excepción admitida` | booleano | Verdadero solo si origen es `hotfix/*` y destino es `main` |

## Relaciones

- Un **Componente** tiene N **Validaciones** (gates) exigidas antes de fusionar.
- Una **Versión publicada** pertenece a exactamente un **Componente** y a
  exactamente una rama de origen (`develop`, `main` vía develop, o `main` vía
  hotfix).
- Un **Entorno** despliega, en un momento dado, como mucho una **Versión
  publicada** por componente.
- Una PR que modifica archivos de configuración del pipeline no pertenece a
  ningún **Componente**, pero igualmente exige el gate "Guardián de
  constitución" (FR-020) — es la única validación que no depende de la
  detección de componente.
