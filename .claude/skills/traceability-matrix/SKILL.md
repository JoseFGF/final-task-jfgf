---
name: traceability-matrix
description: Genera y mantiene docs/traceability.md, la matriz requisito → acceptance criteria → test. Úsalo cuando el usuario pida "actualiza la trazabilidad", "genera la matriz requisito-test", o durante /speckit.analyze o /speckit.implement cuando haya que verificar que todo acceptance criteria tiene un test que lo respalde. No escribe tests ni código de producción, solo audita y documenta.
---

# Traceability Matrix

Esta skill audita y documenta la relación entre requisitos y tests, no los crea.

## Pasos

1. **Localizar la spec**: lee `.specify/spec.md` (o `/specs/spec.md` si esa es la convención del proyecto). Extrae cada FR (formato EARS) y sus acceptance criteria asociados. Si un FR no tiene acceptance criteria explícitos, márcalo como incompleto en vez de inventarlos.

2. **Localizar los tests**: recorre `/tests` (unit + contract + integration) buscando qué test(s) verifican cada acceptance criteria. Usa como pistas: nombres de archivo/test, comentarios que referencien el FR (p.ej. `// FR-003`), y la funcionalidad real que ejerce el test. No asumas cobertura por similitud de nombre sin confirmar que el test realmente ejercita ese criterio.

3. **Generar/actualizar `docs/traceability.md`** con una tabla:

   | Requisito (FR) | Acceptance Criteria | Test | Estado |
   |---|---|---|---|
   | FR-00X | ... | `path/al/test.ts::nombre` | Cubierto |

   - **Cubierto**: existe un test identificado que verifica el criterio.
   - **No verificado**: el criterio existe en la spec pero no se encontró test. No lo marques como cubierto "porque parece obvio".
   - **Huérfano** (sección aparte): tests que no corresponden a ningún requisito de la spec — puede ser scope creep o test obsoleto; repórtalo, no lo borres tú mismo.

4. **Resumen final**: al terminar, reporta cuántos requisitos están cubiertos vs no verificados. Si hay no verificados, sé explícito — este proyecto trata "no se puede verificar" como "no está terminado".

## Reglas

- No implementes tests para tapar huecos; tu trabajo es exponer el hueco, no cerrarlo.
- No modifiques `spec.md` ni el código de tests; solo lees y escribes `docs/traceability.md`.
- Si la estructura de carpetas del proyecto difiere de `/specs`, `/tests`, `/docs`, busca la convención real antes de asumir rutas por defecto.
