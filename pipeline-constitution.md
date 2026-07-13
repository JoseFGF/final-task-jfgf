<!--
Sync Impact Report
==================
Version change: 1.0.0 → 1.1.0
Modified principles: ninguno redefinido; se amplía la Estrategia de ramas
  (Additional Constraints) para admitir un cuarto tipo de rama
Added sections: ninguna nueva a nivel de encabezado; se añade contenido dentro
  de Additional Constraints (rama hotfix/* y su relación con Principio VII)
Removed sections: ninguna
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ compatible sin cambios
  - .specify/templates/spec-template.md ✅ compatible sin cambios
  - .specify/templates/tasks-template.md ✅ compatible sin cambios
  - .specify/memory/constitution.md — sigue sin tocarse (documento
    independiente)
Follow-up TODOs: ninguno
-->

# FieldOps Pipeline Constitution

Esta constitution rige exclusivamente el pipeline de CI/CD de FieldOps
(workflows en `.github/workflows/`). Es independiente de
`.specify/memory/constitution.md`, que rige el desarrollo de la aplicación en
sí; ninguna de las dos sustituye a la otra.

## Core Principles

### I. Spec-Antes-Que-YAML (NON-NEGOTIABLE)

Ningún archivo de workflow se escribe hasta que exista y esté aprobada la
especificación del pipeline correspondiente. El historial de git es la prueba:
el commit de la especificación precede siempre al primer `.yml` que la
implementa, nunca al revés.

**Rationale**: Un pipeline que se escribe a base de ir probando YAML hasta que
"funciona" termina codificando decisiones implícitas que nadie revisó a
propósito (qué bloquea un merge, quién puede desplegar, qué se reconstruye y
qué no). Fijar esas decisiones por escrito antes de tocar la configuración es
la única forma de que se puedan cuestionar antes de que ya estén en producción.

### II. Flujos Independientes por Componente

El backend (Spring Boot, `src/backend/`) y el frontend (Angular,
`src/frontend/`) tienen workflows de CI/CD completamente separados. Un cambio
que solo afecte a uno de los dos componentes no dispara ningún job del otro;
esto se garantiza filtrando por ruta de archivo en los triggers de cada
workflow, no por convención de nombres de rama ni por criterio manual.

**Rationale**: Los dos componentes se despliegan, versionan y validan de forma
distinta (una imagen de backend Maven no tiene nada que ver con un bundle
estático de Angular); ejecutar el pipeline completo por un cambio que solo
toca uno de los dos malgasta tiempo de CI y oscurece qué build corresponde a
qué cambio real.

### III. Pin por SHA en Acciones de Terceros

Toda acción de GitHub Actions que no sea propia del repositorio se referencia
por el hash de commit exacto, nunca por una etiqueta de versión (`@v4` o
similar).

**Rationale**: Una etiqueta puede volver a apuntar a otro commit sin que nadie
del equipo se entere; fijar el hash es la única garantía de que el código que
se ejecuta en el pipeline hoy es el mismo que se revisó y aprobó al añadir esa
acción.

### IV. Permisos Mínimos por Workflow

Cada workflow declara explícitamente, en su propio bloque `permissions:`,
únicamente los permisos que sus jobs necesitan. Ninguno hereda un conjunto de
permisos amplio por defecto ni reutiliza el bloque de otro workflow sin
revisarlo. Si un job necesita publicar en el registro de contenedores, ese
permiso se declara ahí y en ningún otro sitio del repositorio.

**Rationale**: El `GITHUB_TOKEN` con permisos de escritura amplios es la
superficie de ataque más común en pipelines mal configurados; declarar el
mínimo necesario por workflow limita el daño posible si un paso se ve
comprometido.

### V. Sin Reconstrucción en Despliegue

El job que despliega nunca reconstruye la aplicación desde el código fuente:
usa exactamente la imagen que ya superó todas las validaciones de CI y quedó
publicada en el registro de imágenes. No existe ninguna ruta de despliegue que
compile o empaquete de nuevo.

**Rationale**: Si el despliegue reconstruyera, lo que llega a producción
podría diferir —aunque sea mínimamente, por una dependencia que cambió entre
medias— de lo que realmente pasó las pruebas. Desplegar la imagen ya
verificada, byte a byte, es la única forma de que "pasó CI" y "está en
producción" signifiquen lo mismo.

### VI. Aprobación Manual para Producción

Los entornos previos a producción se despliegan automáticamente al cumplirse
su condición de disparo. Producción es la excepción: ningún despliegue llega
ahí sin que una persona del equipo lo autorice explícitamente en el momento.

**Rationale**: La automatización reduce fricción en las fases donde
equivocarse es barato de deshacer; producción no es una de ellas, y la
autorización humana es la última barrera antes de un cambio que afecta a
usuarios reales.

### VII. Gates Bloqueantes en Cada Fusión

Ninguna fusión de código hacia la rama de integración se permite si falla
cualquiera de sus comprobaciones automáticas: pruebas del componente,
validación de que el contrato de la API sigue siendo coherente, detección de
cambios que romperían a quien ya consume esa API, búsqueda de secretos
filtrados en el código, verificación de que los criterios de aceptación
siguen cumpliéndose contra la API real, análisis de vulnerabilidades de la
imagen construida, y una revisión automática de que el cambio no se salta las
reglas no negociables del sistema.

**Rationale**: Un gate que existe pero no bloquea es, en la práctica,
decorativo — alguien puede fusionar igual y la comprobación queda como una
nota que nadie mira. Bloquear el merge es lo que convierte cada gate en una
garantía real, no en una sugerencia.

## Additional Constraints

**Stack real de los componentes**: backend Spring Boot construido y probado
con Maven (`src/backend/`); frontend Angular construido y probado con npm
(`src/frontend/`). Cualquier gate de este pipeline se ejecuta con las
herramientas propias de cada stack (Maven para el backend, npm para el
frontend), no con comandos genéricos que asuman un stack distinto.

**Registro de imágenes**: GitHub Container Registry (GHCR), autenticado con el
`GITHUB_TOKEN` que GitHub inyecta automáticamente — sin secretos adicionales
que gestionar ni rotar.

**Estrategia de ramas**: `feature/*` para trabajo en curso (nunca se
despliega), `develop` como línea de integración continua, `main` para
versiones finales, y `hotfix/*` como única excepción admitida: permite llegar
a `main` sin pasar por `develop` cuando una corrección no puede esperar a que
`develop` esté en estado desplegable. Una rama `hotfix/*` no queda exenta de
ninguna validación por tomar este atajo — pasa exactamente por los mismos
gates bloqueantes que exige el Principio VII para llegar a `main`, y el
cambio se re-integra en `develop` para que no se pierda. Ninguna otra rama
participa del pipeline.

**Entornos**: `dev` (automático al integrar en `develop`), `pre` (automático
al llegar a `main`), `prod` (solo tras aprobación manual, Principio VI).

## Development Workflow

El pipeline se especifica y se construye en el mismo orden de gates que ya
rige el resto del proyecto, adaptado a este alcance: primero se fijan estas
reglas (Constitution, este documento); después se traducen a requisitos
verificables del pipeline en sí —qué dispara qué, qué bloquea qué— (Specify);
se resuelven las ambigüedades que el brief original deja abiertas, como el
mecanismo exacto de detección de cambios por ruta o cómo se calcula la
versión semántica de cada imagen (Clarify); se diseña el orden y las
dependencias entre los jobs de los seis workflows (Plan); se descompone en
tareas por workflow (Tasks); y solo entonces se escriben los archivos `.yml`
(Implement).

Cada fase se cierra con su propio commit, de modo que el orden real quede
grabado en el historial y no dependa de la memoria de nadie.

## Governance

Esta constitution tiene prioridad sobre cualquier decisión ad-hoc tomada al
escribir un workflow. Al ser un proyecto de autoría individual, las enmiendas
las decide y aprueba el propio autor, documentadas con su Sync Impact Report
correspondiente.

**Versionado** (SemVer aplicado a esta constitution):
- MAJOR: se elimina o redefine un principio existente de forma incompatible.
- MINOR: se añade un principio nuevo o se amplía sustancialmente una guía.
- PATCH: aclaraciones, correcciones de redacción o cambios no semánticos.

**Revisión de cumplimiento**: antes de dar por cerrado cualquier workflow, se
verifica explícitamente que no contradice ninguno de los principios
anteriores. Una desviación se documenta y se resuelve antes de seguir
adelante; no se acepta una excepción sin dejar constancia escrita de por qué
era necesaria.

**Version**: 1.1.0 | **Ratified**: 2026-07-13 | **Last Amended**: 2026-07-13
